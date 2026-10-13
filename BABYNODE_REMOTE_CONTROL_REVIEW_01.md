# BabyNodeAutomotive Remote-Control Review

## Scope and review summary

This was a read-only review of the `babynode-remote-control-review` branch, covering the Android front-end remote-control path, including command input and mapping, dispatch, transport/status handling, Bluetooth protocol and lifecycle, approved-device persistence, and existing tests. The worktree was clean at review time. The review did not run a build or tests.

The app contains no vehicle-specific CAN IDs or payloads; Android remains CAN-agnostic. No SQL, shell, WebView, or path-traversal injection sinks, hard-coded credentials, or direct external-intent command dispatch were found. The RFCOMM socket uses the secure bonded-device variant. Framing has a 4096-byte bound, oversized or unterminated frames close the link, duplicate responses are ignored after first match, and connection/write paths are serialized with mutexes.

## Findings

### 1. HIGH — Natural-language input can trigger unintended actuation

**Files and lines:** `app/src/main/java/com/babynode/automotive/CarCommandMap.kt:114–175, 212–254, 301–310, 397–423, 707–713`

**Why it matters:** Token-set matching ignores grammar and intent. Questions such as “Is the trunk open?” or “did you unlock the doors?”, unrelated phrases such as “lock my phone”, and single tokens including `unlock`, `lock`, `mute`, `xm`, `satellite`, `washer`, and `spray` can map to actuating commands. `"unlock"` takes precedence over other context, so “Unlock the trunk” maps to `UNLOCK_DOORS`. Contractions such as “can't” are split into tokens, so “I can't unlock the doors” can pass the negation gate. Other negators such as “without”, “instead”, “cancel”, “undo”, “wait”, and “ignore” are not covered. A multi-intent utterance silently dispatches only its first match.

**Recommended fix:** Replace token-set matching with a strict, closed grammar or exact phrase set. Reject questions, negation, unmatched tokens, and multiple distinct intents. Require an explicit confirmation step for sensitive actions such as unlocking or opening compartments.

**Recommended automated test:** Add table-driven `CarCommandMapTest` cases for every allowed phrase and a negative corpus covering questions, contractions, negation, unrelated sentences, multi-intent input, and single-token triggers. Add a property/fuzz test that mapper results are only unknown, ignored, blocked, or a member of the Phase 1 allowlist.

### 2. MEDIUM — Phase 1 allowlist is not enforced at dispatch/transport boundaries

**Files and lines:** `app/src/main/java/com/babynode/automotive/CarCommandMap.kt:35–109, 781–793`; `app/src/main/java/com/babynode/automotive/CarCommandDispatcher.kt:374–401`; `app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt:466–524`; `app/src/main/java/com/babynode/automotive/CarCommandDetector.kt:31–181, 327–400`

**Why it matters:** The mapper’s private allowlist is effectively circular: mapper outputs are already literals in that list, so it does not protect future callers. The dispatcher accepts any regex-valid canonical command, allowing another `sendCommand` caller to bypass Phase 1 policy. `CarCommandDetector` is a second, weaker pre-filter with its own normalization, negation, and vocabulary; it admits `sunroof` and `moonroof`, which the mapper cannot produce.

**Recommended fix:** Enforce one shared Phase 1 allowlist at the dispatcher boundary (and optionally again at transport), and remove the duplicate detector gate or make it use the same canonical decision logic and normalizer.

**Recommended automated test:** Verify every mapper output is allowlisted; verify dispatcher rejection of non-allowlisted but regex-valid values, lowercase values, and overlong names; and use a fake transport to prove rejected input never reaches `sendCommand`.

### 3. MEDIUM — Module configuration is fire-and-forget and does not gate commands

**Files and lines:** `app/src/main/java/com/babynode/automotive/MainActivity.kt:90–92, 174–177, 780–792`; `app/src/main/java/com/babynode/automotive/CarCommandDispatcher.kt:89–141`

**Why it matters:** `selectedModule` defaults to `SINGLE_CAN` on cold start and is not persisted. On every connection, the app sends `config.module=single` without an explicit user choice. The result is ignored, and command dispatch is allowed before a successful configuration acknowledgement. A mismatch between the selected module and actual hardware may therefore produce silent failures or unsafe assumptions.

**Recommended fix:** Persist the user's module choice or require an explicit choice before sending commands. Track the configuration request, surface failures, and disable command dispatch until a correlated success response. Prefer negotiating module/capabilities as part of a session handshake.

**Recommended automated test:** Use a fake transport to test configuration timeout and rejection, verify commands are blocked until acknowledgement, verify configuration is sent again after reconnect, and verify the selection survives restart.

### 4. MEDIUM — Cancellation can report “cancelled” after bytes may have been written

**File and lines:** `app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt:573–585, 617–641`

**Why it matters:** A blocking `write`/`flush` cannot necessarily be interrupted. If cancellation is observed after the blocking operation returns, `writeCompleted` may still be false and the cancellation path removes the pending request and reports “Command send cancelled”, even though bytes may have reached the peer. A later response is then treated as late/unexpected, while the user has been told an action was cancelled.

**Recommended fix:** Distinguish definitely-not-written from possibly-written outcomes. Record the write outcome in a cancellation-safe way; after a possible write, report “outcome unknown” and retain request tracking until a terminal response or timeout.

**Recommended automated test:** Use a fake output stream whose write blocks, cancel during the write, then assert the event reports outcome unknown and the pending request remains tracked until timeout or response.

### 5. MEDIUM — Pending requests and status state are not cleaned up on close/replacement

**Files and lines:** `app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt:690–722, 1228–1247`; `app/src/main/java/com/babynode/automotive/MainActivity.kt:318–373, 511–516`

**Why it matters:** Closing the transport cancels pending commands without emitting terminal error events. The `CarStatusBus` is not reset on transport replacement, so it can retain an old `Connected` state or request status for an operation that can no longer resolve. Reconnection decisions may consult the old bus state.

**Recommended fix:** On close/replacement, emit terminal outcome-unknown events for pending commands and a disconnected/reset state. Make reconnect decisions use the current transport instance’s state.

**Recommended automated test:** Replace a transport while a command is pending and assert that the request receives a terminal outcome, the bus is reset, and the UI no longer presents the old connection as active.

### 6. MEDIUM — UI request correlation relies on a conflated latest-event state

**Files and lines:** `app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt:91–114, 224–236, 418–452`; `app/src/main/java/com/babynode/automotive/ui/CommandTestSection.kt:86–109, 251–262, 391–425`; `app/src/main/java/com/babynode/automotive/CarStatusBus.kt:45–52, 89–97`

**Why it matters:** Both UI sections observe only `latestEvent`, which stores one event. A response or unrelated event can overwrite an event before the UI records the request, leaving “Request sent” indefinitely. The status captured in the dispatch callback is stale, weakening the “already arrived” check. Request IDs restart at 1 for each transport, so stale active UI state could collide with a new transport's request ID.

**Recommended fix:** Track outcomes per request ID with a non-conflated event stream or per-request state. Include a transport/session generation in the request key, add a terminal UI timeout, and centralize duplicated correlation logic.

**Recommended automated test:** Extend `CarStatusBusTest` for response-before-registration, interleaved requests, unrelated events between send and response, and transport replacement/request-ID reuse.

### 7. MEDIUM — Bluetooth identity is pinned on first use by display name

**Files and lines:** `app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt:321–432`; `app/src/main/java/com/babynode/automotive/BabyNodeApp.kt:21–89`; `app/src/main/AndroidManifest.xml:37–40`; `app/src/main/res/xml/backup_rules.xml`; `app/src/main/res/xml/data_extraction_rules.xml`

**Why it matters:** If there is no stored identity, the first bonded device with display name `BabyNodeCAN` is pinned. A device name is attacker-controlled, and there is no user-facing confirmation or “forget device” action. The peer is trusted based on bond and name alone, without application-level proof or version negotiation. Duplicate matching names are refused and a pinned device whose name changes is refused, which are fail-closed behaviors. Backup rules are empty while backup is enabled; moving the pin to a device without the corresponding bond fails closed, so this is lower risk.

**Recommended fix:** Require explicit user confirmation of the device on first use, provide a “Forget device” action, and authenticate the peer in a protocol handshake. Consider excluding the preference from backup and checking persistence success.

**Recommended automated test:** Add an injected/fake Bluetooth adapter suite for zero matches, multiple matches, pinned-but-absent device, changed name, and first-use confirmation. Add `BabyNodeAppTest` for pin persistence and malformed stored values.

### 8. MEDIUM — No protocol handshake or response-version validation

**File and lines:** `app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt:251–260, 496–524, 940–1186`

**Why it matters:** Command envelopes carry `version=1`, but responses are not required to match a protocol version. “Connected” is emitted once RFCOMM is established, without proving that the peer supports the expected protocol. Response parsing is lenient: `type` is case-insensitive, `command` is trimmed/case-folded, and `status` is trimmed/lowercased. The reason string is displayed as received, bounded only by the 4096-byte frame limit. Request IDs restart at 1 after transport replacement, which can collide with peer-side deduplication caches.

**Recommended fix:** Add a handshake validating protocol version, capabilities, and session identity before reporting connected. Require strict response field types and values, including version, and use session-scoped request IDs.

**Recommended automated test:** Add pure protocol-parser/codec tests for wrong or missing version, bad field types, fractional/negative/oversized IDs, duplicate keys, malformed and oversized frames, missing newline, CRLF, empty lines, partial UTF-8, unknown IDs, late/duplicate responses, and mismatched commands.

### 9. LOW — Reconnection race and retry limit

**Files and lines:** `app/src/main/java/com/babynode/automotive/MainActivity.kt:444–523, 525–658, 795–804`; `app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt:151–158`

**Why it matters:** A disconnect arriving while a reconnect job is active can be ignored, including in the post-connect delay. After five failures there is no further retry until activity recreation. There is no lifecycle-aware retry behavior. `connect()` can return silently when another connection attempt is active, leaving the bus in `Connecting`. A stale receive loop could also close a newer socket after disconnect/reconnect; current `MainActivity` does not call `disconnect()`, so this is latent.

**Recommended fix:** Drive reconnection from one state machine, use a generation counter so stale work cannot affect a newer connection, and provide an explicit retry action or bounded lifecycle-aware retry policy.

**Recommended automated test:** Add `MainActivityLifecycleTest` using a fake transport and virtual time for connection failure, disconnect during retry, repeated failures, replacement, and stale receive-loop events.

### 10. LOW — Voice confirmation and production/debug surface

**Files and lines:** `app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt:116–287`; `app/src/main/java/com/babynode/automotive/ui/AutomotiveScreen.kt:147–161`; `app/src/main/java/com/babynode/automotive/ui/CanDebugSection.kt:24–27, 56–59`; `app/build.gradle.kts:27`; `app/src/main/res/xml/shortcuts.xml:12, 23`; `app/src/main/java/com/babynode/automotive/MainActivity.kt:677–714`

**Why it matters:** Voice dispatch uses the first recognition hypothesis without an echo-and-confirm step. The free-text Command Tester and CAN Debug panel ship unconditionally, and `CanDebugSection` logs event lines at INFO on recomposition. The launcher advertises `bn://unlock_doors` and `bn://lock_doors` shortcuts, while the exported activity’s current intent inspection only logs/rejects these inputs and no external-intent path was found that dispatches commands. The shortcuts are misleading and could become risky if a future handler acts on them. Release minification is disabled.

**Recommended fix:** Echo and confirm recognized text before dispatch, especially for sensitive commands. Gate debug/test surfaces and verbose event logging to debug builds. Remove the shortcuts or make them open a confirmation-only screen, and continue rejecting external-intent dispatch. Consider enabling minification for release.

**Recommended automated test:** Add instrumented Compose tests with a stubbed recognizer for accepted, rejected, negated, and cancelled input; verify shortcut/deep-link intents never call the transport and debug-only surfaces are absent from release builds.

### 11. LOW — Timeout wording does not communicate an unknown outcome

**Files and lines:** `app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt:743–790, 1138–1165`; `app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt:584–596`

**Why it matters:** A response timeout does not prove that an action did not execute. The timeout message says “response timeout” without clarifying that the command may have executed. Cancellation handling does communicate an unknown outcome, so the two paths are inconsistent. A positive “ok” is shown as “Request accepted”, not physical completion, which appropriately avoids a false claim that the vehicle action completed.

**Recommended fix:** Use consistent “outcome unknown” timeout messaging and explain that the command may have executed.

**Recommended automated test:** Verify timeout event/UI wording and ensure a timeout does not imply successful completion or definite non-execution.

### 12. LOW — Large transport and duplicated logic increase maintenance risk

**Files:** `app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt` (1315 lines); `app/src/main/java/com/babynode/automotive/CarCommandDetector.kt`; `app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt`; `app/src/main/java/com/babynode/automotive/ui/CommandTestSection.kt`

**Why it matters:** `CarCanBusBluetooth` combines framing, protocol parsing, pending-request state, Bluetooth I/O, and lifecycle behavior in one class, is hard-wired to `BabyNodeApp.instance` and `BluetoothAdapter.getDefaultAdapter()`, and is difficult to test independently. The detector and map duplicate normalization/negation/out-of-scope logic. Voice and command-test sections duplicate request correlation and state handling.

**Recommended fix:** Incrementally extract a pure frame reader, protocol codec, pending-request table, injected Bluetooth link/socket factory, shared normalized-phrase logic, and shared request tracker. Preserve Android’s CAN-agnostic boundary; do not add vehicle-specific CAN IDs or payloads to this repository.

**Recommended automated test:** Add focused unit suites for extracted framing, parsing, and request-tracking components, with regression tests proving existing command and response behaviors remain unchanged.

## Overall production-readiness assessment

**Not production-ready for vehicle actuation.** The command interpreter can fail open on sensitive commands; allowlist enforcement and module-configuration gating are incomplete; cancellation, timeout, and transport replacement can leave ambiguous outcomes; and automated coverage of safety-critical behavior is insufficient.

## Prioritized remediation order

1. Replace natural-language token matching with fail-closed parsing, reject ambiguous/multi-intent inputs, and require confirmation for sensitive actions.
2. Enforce the Phase 1 allowlist at the dispatcher boundary and eliminate divergent filtering logic.
3. Gate commands on acknowledged module configuration or a validated session handshake.
4. Correct cancellation, timeout, close, and replacement outcomes so a potentially transmitted command is never represented as definitely cancelled or unsent.
5. Track outcomes per request robustly across event ordering and transport replacement.
6. Add first-use device confirmation, a forget-device flow, and protocol/version/session validation.
7. Stabilize reconnection and gate debug/test functionality in production.
8. Refactor large and duplicated components to make safety-critical behavior independently testable.

## Specific missing test suites

- `CarCommandMapTest` and `CarCommandDetector` tests for allowed, negated, ambiguous, multi-intent, and out-of-scope input.
- `CarCommandDispatcherTest` with a fake transport for allowlist enforcement, rejection, cancellation, failures, and configuration gating.
- Protocol/framing and Bluetooth transport tests for correlation, timeout, late/duplicate/unsolicited/malformed responses, concurrent sends, cancellation, disconnect, and close.
- `CarStatusBusTest` coverage for interleaved/out-of-order outcomes and transport replacement.
- `BabyNodeAppTest` for approved-device persistence and malformed values.
- `MainActivityLifecycleTest` for reconnect, replacement, lifecycle, and stale events using virtual time.
- Instrumented Compose tests for typed/voice input, confirmation/cancellation, and external-intent rejection.

## Review integrity

This report was created from the completed review findings. The review was not rerun. No existing files were modified, and no fixes were made.
