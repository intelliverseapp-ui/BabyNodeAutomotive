# BabyNodeAutomotive Remote-Control Production-Readiness Review

## Scope and method

Read-only review of the current `babynode-remote-control-review` branch, covering production Kotlin/Compose code, JVM and Android instrumented tests, Gradle configuration, manifest/resources, command mapping and dispatch, Bluetooth framing and lifecycle, protocol validation, reconnection, external intents, and release/debug separation. Each original finding in [BABYNODE_REMOTE_CONTROL_REVIEW_01.md](./BABYNODE_REMOTE_CONTROL_REVIEW_01.md) was checked against the current implementation.

No project source, tests, Gradle configuration, manifest, or resources were changed. This report is the only file created for this review. No build, test, or lint command was run: Gradle may write generated outputs, which would violate the read-only constraint. The validation commands at the end are recommendations, not claimed results.

## Executive assessment

**Not production-ready for vehicle actuation.** Several significant remediations are present: user command mapping is limited to a small exact-phrase table, the dispatcher uses the shared Phase 1 allowlist, sensitive commands require a one-use confirmation object, device approval is explicit and persisted, response framing is bounded, response fields are validated, timeout text says the outcome is unknown, and debug-only UI is gated by `BuildConfig.DEBUG`.

Remaining blockers include an Android/firmware capability contract that accepts only a `single`/`stub_only`/non-transmitting peer, response-ordering races that can lose handshake/config readiness or hide a real response, request correlation that is not scoped to a transport session, and an Activity-destruction path that discards terminal unknown-outcome events. The strict phrase mapper also normalizes question punctuation into executable commands. Android contains no vehicle-specific CAN IDs, CAN payload maps, or CAN-frame transmission logic; no firmware or protocol contract is present here to establish safe vehicle actuation.

## Verified findings

### 1. HIGH — Current handshake contract excludes an actuator-capable peer and the advertised dual module

**Files:** [BabyNodeProtocol.kt](./app/src/main/java/com/babynode/automotive/BabyNodeProtocol.kt#L42), [MainActivity.kt](./app/src/main/java/com/babynode/automotive/MainActivity.kt#L34), [AutomotiveScreen.kt](./app/src/main/java/com/babynode/automotive/ui/AutomotiveScreen.kt#L106)

**Why it matters:** `validateHandshakeCapabilities()` accepts only `module == "single"`, `can_transmit == false`, and `vehicle_commands == "stub_only"`. The UI advertises `DUAL_CAN` and sends module ID `"dual"`. Consequently, a peer truthfully reporting the dual module is rejected; a peer truthfully reporting that it can transmit vehicle commands is also rejected. The app currently has no demonstrated handshake path for an actuator-capable or dual-module production peer. Silently relaxing these checks would not be safe; the peer contract and supported capabilities must first be defined and verified.

**Recommended fix:** Define a versioned Android/BabyNodeCAN protocol contract that specifies module identity and command capabilities. Validate the negotiated module against the user's selected module and enable commands only for explicitly supported, independently safety-reviewed capabilities. If the only supported peer is intentionally a non-actuating stub, remove/label the production vehicle-control surface accordingly rather than presenting it as production actuation.

**Recommended automated regression test:** Protocol/peer contract tests for the supported single and dual module identities and capability combinations. Assert that an actuator-capable peer is either accepted only under the approved contract or fails closed with an explicit unsupported-capability state; also assert that the UI cannot select a module the handshake rejects.

### 2. HIGH — Activity destruction discards terminal unknown-outcome events

**Files:** [MainActivity.kt](./app/src/main/java/com/babynode/automotive/MainActivity.kt#L902), [CarCanBusBluetooth.kt](./app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L770)

**Why it matters:** `CarCanBusBluetooth.close()` returns terminal `Error` events for pending requests, marked “outcome unknown.” Transport replacement passes those events into the status bus, but `MainActivity.onDestroy()` calls `transport.close()` and discards the returned list. A request whose bytes may have reached BabyNodeCAN can therefore lose its only terminal outcome when the Activity is destroyed/recreated. The next Activity starts with a fresh status bus and cannot tell the user that the prior action may have executed, which can invite unsafe manual retries.

**Recommended fix:** Preserve pending request identity and an explicit outcome-unknown result across Activity recreation/process teardown, or otherwise make the result visible before teardown is allowed to complete. Never convert this path into definite failure or automatically retry the command.

**Recommended automated regression test:** With a fake transport, write a command and destroy/recreate the Activity before its response. Assert that the recreated UI identifies the exact command as outcome unknown and offers no automatic replay.

### 3. MEDIUM — Fast handshake/configuration acknowledgments can be missed

**Files:** [CarCommandDispatcher.kt](./app/src/main/java/com/babynode/automotive/CarCommandDispatcher.kt#L127), [MainActivity.kt](./app/src/main/java/com/babynode/automotive/MainActivity.kt#L981)

**Why it matters:** The dispatcher assigns `pendingProtocolHandshakeRequest` and `pendingModuleConfigRequest` only after `transport.sendCommand()` returns. The Bluetooth receiver can process a response after the bytes are written but before that suspend call returns. In that ordering, `acceptTransportEvent()` sees no pending request and cannot mark the handshake ready. `MainActivity` reacts to the handshake response by immediately calling `sendModuleConfig()`, which rejects it because readiness is still false. The handshake callback later retries processing only `statusBus.latestEvent`; it does not retry module configuration, and an intervening unrelated event can overwrite the response. The same latest-only recovery can lose an early module-configuration acknowledgment, leaving vehicle commands blocked until a reconnect/manual retry.

**Recommended fix:** Track the request and its exact response by request identity across send completion and event collection. Use retained event history (or a request-result primitive) rather than `latestEvent`, and start module configuration only after the handshake's correlated successful result has been consumed.

**Recommended automated regression test:** A fake transport that emits the handshake response synchronously/early, followed by an unrelated event before `sendCommand()` returns; assert that configuration is still sent exactly once. Repeat for an early configuration acknowledgment and assert vehicle commands become ready only for its exact successful response.

### 4. MEDIUM — An immediate response can be overwritten by the later `CommandSent` event

**Files:** [CarCanBusBluetooth.kt](./app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L553), [CarCanBusBluetooth.kt](./app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L585), [VoiceInputSection.kt](./app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt#L105), [CommandTestSection.kt](./app/src/main/java/com/babynode/automotive/ui/CommandTestSection.kt#L99)

**Why it matters:** A pending request is registered before the write. BabyNodeCAN can respond as soon as it receives the bytes, while `sendCommand()` emits `CommandSent` only after the write/flush returns. The receive loop can therefore append `CommandResponse` before the writer appends `CommandSent`. Both UI sections select the newest matching event, so they can select `CommandSent` instead of the already-arrived response, then display a local timeout/outcome-unknown state. A user may repeat an action that was already accepted.

**Recommended fix:** Make per-request state monotonic: “sent” must not overwrite a terminal response/error, and consumers should prioritize terminal outcomes over nonterminal `CommandSent` events. Alternatively, emit a single ordered request-state stream from the transport.

**Recommended automated regression test:** Use a fake peer that responds immediately during the write. Assert that the response remains the final UI state and is never replaced by “sent,” “waiting,” or timeout.

### 5. MEDIUM — Request identity can collide across transport replacement

**Files:** [CarCanTransport.kt](./app/src/main/java/com/babynode/automotive/CarCanTransport.kt#L23), [CarCanBusBluetooth.kt](./app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L83), [MainActivity.kt](./app/src/main/java/com/babynode/automotive/MainActivity.kt#L448), [VoiceInputSection.kt](./app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt#L73)

**Why it matters:** Each new Bluetooth transport starts request IDs at 1, and `CarCommandRequest` identifies a request only by ID and command. On transport replacement, the Compose sections remain at the same composition location and retain their `remember`-backed `activeRequest`; they are not keyed/reset by the dispatcher or a transport generation. A later request from the replacement transport with the same ID and command can match stale UI state as though it belonged to the old request. The handshake session ID is validated but is not incorporated into request/event identity.

**Recommended fix:** Include a non-reusable transport/session generation in request and event identity and in UI matching. Reset request-local Compose state when the dispatcher/session changes. Preserve terminal outcomes from the old session separately from new-session events.

**Recommended automated regression test:** Keep an old voice request active, replace the transport, then emit a same-ID/same-command request and response from the new transport. Assert that the old UI request does not consume the new event.

### 6. MEDIUM — Punctuation normalization turns explicit questions into commands

**Files:** [CarCommandMap.kt](./app/src/main/java/com/babynode/automotive/CarCommandMap.kt#L137), [CarCommandMapTest.kt](./app/src/test/java/com/babynode/automotive/CarCommandMapTest.kt#L6)

**Why it matters:** Normalization replaces every non-alphanumeric character with whitespace before exact phrase lookup. Thus `"unlock the doors?"` normalizes to the allowed phrase `"unlock the doors"` and maps to `UNLOCK_DOORS`; the same applies to a question-form headlights phrase. This is a residual grammar/intent ambiguity despite the exact phrase table. Sensitive commands still require an additional in-app confirmation, but the mapper itself does not fail closed on this question form.

**Recommended fix:** Reject question syntax and quotation/mention forms before punctuation is discarded, or tokenize punctuation in the closed grammar so only explicitly allowed imperative forms match.

**Recommended automated regression test:** Add table-driven cases for trailing/interior `?`, quoted commands, and question-like ASR output. Assert they are unknown/rejected and never reach the transport; retain tests for all deliberately supported imperative forms.

### 7. MEDIUM — Receive-loop shutdown is not scoped to the socket generation

**Files:** [CarCanBusBluetooth.kt](./app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L155), [CarCanBusBluetooth.kt](./app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L420), [CarCanBusBluetooth.kt](./app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L876), [CarCanBusBluetooth.kt](./app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L1403)

**Why it matters:** A receive loop captures `connectedSocket`, but its exception path checks the transport-wide `intentionalDisconnect` flag and calls `handleConnectionLoss(reason)`, which closes the transport's current global socket/streams without checking that they still belong to the loop's socket. `disconnect()` cancels the old loop without joining it; a later `connect()` resets the shared flag to false. If the old blocking read then reports its close/error after reconnection, the stale loop can treat it as an unexpected loss and close the new connection. The same lifecycle lacks a socket-generation check when installing a connection after the pre-install disconnect/close check.

**Recommended fix:** Associate each connect/receive/write operation with a socket-generation token. Only the current generation may update connection state or close current resources; serialize installation and disconnect/close and recheck shutdown state while holding the resource lock.

**Recommended automated regression test:** Use controllable fake sockets to pause an old receive loop across disconnect/reconnect and release its read exception after the new socket is installed. Assert the new socket remains connected and no stale `Disconnected` event is emitted. Also race connection installation against disconnect and close.

### 8. MEDIUM — The bounded frame reader silently removes carriage returns inside JSON strings

**Files:** [BoundedFrameReader.kt](./app/src/main/java/com/babynode/automotive/BoundedFrameReader.kt#L42), [BoundedFrameReaderTest.kt](./app/src/test/java/com/babynode/automotive/BoundedFrameReaderTest.kt#L34)

**Why it matters:** The reader discards every `\r`, not only a carriage return immediately preceding `\n`. A malformed frame such as a response command containing an embedded raw carriage return can be transformed into a different, valid exact command before JSON parsing. That weakens strict field validation and can cause malformed peer input to correlate as a valid response.

**Recommended fix:** Accept CRLF by removing at most one `\r` immediately before the newline; reject any other raw carriage return in the frame.

**Recommended automated regression test:** Keep the CRLF test and add embedded-CR cases inside and outside JSON strings. Assert those malformed frames are rejected rather than normalized into a valid command/status.

### 9. LOW — Forget-device persistence failure is ignored

**Files:** [BabyNodeApp.kt](./app/src/main/java/com/babynode/automotive/BabyNodeApp.kt#L117), [MainActivity.kt](./app/src/main/java/com/babynode/automotive/MainActivity.kt#L307)

**Why it matters:** `clearApprovedBabyNodeCanAddress()` returns the result of `SharedPreferences.commit()`, but the UI callback ignores it and replaces/reconnects the transport regardless. If persistence fails, the UI gives no indication and the old Bluetooth identity can remain approved.

**Recommended fix:** Check the return value, surface a clear error, and do not claim the device was forgotten or start an unapproved reconnect unless the persisted identity is confirmed cleared.

**Recommended automated regression test:** Inject a preference store whose removal commit fails. Assert the UI reports failure and does not proceed as though the device identity had been forgotten.

### 10. LOW — `RECORD_AUDIO` is declared although voice capture is delegated

**Files:** [AndroidManifest.xml](./app/src/main/AndroidManifest.xml#L5), [VoiceInputSection.kt](./app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt#L152)

**Why it matters:** The app declares `RECORD_AUDIO`, while the reviewed code launches `RecognizerIntent` and contains no direct audio-capture path or runtime request for this permission. The declaration should match the app's actual permission needs.

**Recommended fix:** Remove the permission if direct recording is not required by the supported recognition flow; otherwise document the requirement and implement/test the necessary runtime consent behavior.

**Recommended automated regression test:** Inspect the merged debug/release manifests and add an API-level permission test confirming that only permissions needed by the implemented capture path are declared/requested.

### 11. MEDIUM — Safety-critical transport/UI behavior lacks integration regression tests

**Files:** [CarCanBusBluetooth.kt](./app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt), [MainActivity.kt](./app/src/main/java/com/babynode/automotive/MainActivity.kt), [VoiceInputSection.kt](./app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt), [app/src/test/](./app/src/test/), [app/src/androidTest/](./app/src/androidTest/)

**Why it matters:** The repository has useful JVM tests for pure protocol validation, mapping, dispatch policy, event matching, status state, frame bounds, generation counters, and write-outcome classification. However, the actual Bluetooth transport is tied to Android Bluetooth APIs and static adapter/socket creation; there is no fake RFCOMM peer suite exercising real event ordering, timeout, cancellation, disconnect, close, replacement, or response parsing through the transport. The instrumented suite checks preference persistence and basic package context, not Compose review/confirmation, debug/release surfaces, permissions, or Activity lifecycle. The races above are consequently not covered by the current tests.

**Recommended fix:** Introduce a narrow injectable Bluetooth socket/stream seam and test the actual transport state machine with deterministic fake streams/peer responses. Add focused Compose and Activity lifecycle tests for review, confirmation/cancellation, event ordering, replacement, destruction, retry exhaustion/manual retry, and external-intent rejection.

**Recommended automated regression test:** A fake RFCOMM conformance suite covering early/late/duplicate/interleaved responses, malformed fields, write cancellation/failure, timeout, disconnect, close, reconnection generation, and request-ID reuse, plus instrumented tests proving each UI state and debug/release condition.

## Disposition of BABYNODE_REMOTE_CONTROL_REVIEW_01.md

| Original finding | Disposition | Current evidence and residual |
|---|---|---|
| 1. Natural-language input could trigger unintended actuation | **Partially addressed** | The current mapper uses a five-entry exact phrase table and has negative tests. Punctuation normalization still maps question forms such as `"unlock the doors?"` to a command (finding 6). |
| 2. Phase 1 allowlist not enforced at dispatcher/transport boundary | **Addressed on current user-command path** | `CarCommandDispatcher.isValidCanonicalCommand()` calls the shared `CarCommandMap.isPhaseOneCommand()` policy before dispatch. Protocol handshake/module configuration are separate internal commands. The transport API itself remains permissive, but no production UI code calls it outside the dispatcher. |
| 3. Module configuration was fire-and-forget and did not gate commands | **Partially addressed** | Handshake and configuration acknowledgments gate user commands and module selection is persisted. Early acknowledgment races can leave readiness false, and the hard-coded handshake contract conflicts with the dual selection/actuator-capable peer (findings 1 and 3). |
| 4. Cancellation could report cancelled after bytes may have been written | **Addressed in the write-outcome path** | The transport distinguishes pre-write failure from possible transmission, retains pending requests after cancellation once a write starts, and uses outcome-unknown wording. Existing tests exercise the pure classifier, not a blocking real/fake stream cancellation. |
| 5. Pending requests/status were not cleaned up on close/replacement | **Partially addressed** | Replacement consumes terminal events and resets old status. `onDestroy()` discards `close()`'s terminal events, so Activity recreation loses pending outcomes (finding 2). |
| 6. UI request correlation relied on one conflated latest event | **Partially addressed** | The UI searches bounded event history and matches request ID plus command. `CommandSent` can supersede a faster response, and identity lacks a transport/session generation (findings 4 and 5). |
| 7. Bluetooth identity was pinned by display name | **Mostly addressed** | First use now displays the unique bonded candidate and requires explicit approval; the address is validated, persisted, excluded from backup, and can be forgotten. A failed forget commit is ignored (finding 9); adapter/peer approval is not covered by an instrumented Bluetooth test. |
| 8. No protocol handshake/response-version validation | **Partially addressed** | Version, integer ID, exact response type/status/command, reason bounds, duplicate top-level key, session ID, and capability checks exist. Capability support is hard-coded to `single`/`stub_only`; the session ID is not part of subsequent request identity; embedded CR is silently stripped (findings 1, 5, and 8). |
| 9. Reconnection race/retry limit | **Mostly addressed** | MainActivity uses a generation guard, bounded retries, and a visible manual retry path; transport events from replaced transport instances are ignored. Socket-level receive work is not generation-bound (finding 7), and Activity/retry integration tests are absent. |
| 10. Voice confirmation and production/debug surface | **Mostly addressed** | Recognized text is shown for review before dispatch; sensitive commands need a second one-use confirmation. Command Tester/CAN Debug are composed only under `BuildConfig.DEBUG`; shortcuts are absent and external intents are not dispatched. Release minification remains disabled (hardening, not by itself a demonstrated bypass), and `RECORD_AUDIO` remains declared (finding 10). |
| 11. Timeout wording did not communicate unknown outcome | **Addressed** | Timeout helper and UI wording state outcome unknown and that the command may have executed; positive peer response is described as acceptance, not physical completion. |
| 12. Large transport/duplicated logic impeded testing | **Partially addressed** | Pure protocol, frame reader, event matcher, generation, write outcome, and timeout-message components were extracted. The Bluetooth transport remains monolithic and is not injectable, and request-state logic is duplicated across voice/typed UI; test gap is finding 11. |

## Requested safety checks

- **Closed grammar/fail-closed mapping:** Substantially improved, but question punctuation is discarded before exact lookup (finding 6).
- **Shared Phase 1 allowlist:** Enforced by the dispatcher for mapped user commands; no production UI bypass found.
- **Handshake/config gating:** Present and fail-closed, but early responses can leave readiness stuck (finding 3); current capability policy also does not describe a production actuator-capable/dual peer (finding 1).
- **Sensitive confirmation/cancellation/replay:** Exact confirmation object identity is checked and consumed before transmission; cancellation consumes it; new submissions invalidate it. Sequential replay/stale-object tests exist. No physical command can be dispatched by the external-intent path found.
- **Definite failure vs outcome unknown:** Pre-write failures and possibly-written commands are distinguished; transport timeout and replacement errors use unknown-outcome wording. Activity destruction loses the terminal unknown result (finding 2).
- **Request correlation:** Exact ID+command matching and bounded event history are present. Response-before-sent ordering and unscoped request IDs remain (findings 4 and 5).
- **Replacement/reconnect:** MainActivity rejects events from replaced transport objects and advances connection generations. A stale receive loop can still operate on a newer socket during same-instance disconnect/reconnect (finding 7).
- **Bluetooth approval/persistence/backup:** Explicit device approval, address validation, persistence, forget control, and backup exclusions are present. Forget persistence failure is not surfaced (finding 9).
- **Protocol/framing:** Protocol version, exact statuses/types/commands, integer ID ranges, handshake session/capability fields, duplicate top-level key detection, bounded frames, and truncated-frame rejection are present. Embedded CR is stripped, session identity is not used as a request namespace, and current capability values are restrictive (findings 1, 5, and 8).
- **Voice/debug/external intents:** Voice text is reviewed before dispatch and sensitive commands require a second confirmation. Command Tester and event-debug UI are build-gated; no shortcuts resource is present. The exported launcher Activity logs/rejects known external command intents and does not map arbitrary external data to commands.
- **Timeout wording:** Outcome-unknown semantics are explicit.
- **Pure components and tests:** Several pure components have focused JVM coverage; there is no actual Bluetooth peer/lifecycle/Compose integration coverage (finding 11).
- **Android CAN boundary:** No vehicle-specific CAN ID, CAN payload, or CAN-frame transmission logic was found in Android. Commands are canonical JSON sent over Bluetooth; vehicle-specific mapping/transmission is outside this repository. This review therefore does not certify BabyNodeCAN firmware or vehicle behavior.

## Remaining production-readiness blockers

1. Define and verify the BabyNodeCAN protocol/capability contract before enabling any actuator-capable peer; current handshake accepts only a single-module non-transmitting stub.
2. Make handshake/configuration request registration and acknowledgment consumption race-safe.
3. Preserve terminal outcomes through Activity destruction and scope every request identity to a transport/session generation.
4. Prevent nonterminal send events from masking responses; eliminate stale receive-loop control over replacement sockets.
5. Reject question/quotation syntax before phrase normalization and reject malformed frames rather than normalizing them.
6. Add deterministic fake-peer transport tests and UI/lifecycle tests covering the actual safety-critical path before vehicle use.

## Validation commands

**Executed during this review:** None. Running Gradle could modify generated build outputs, contrary to the read-only constraint. Prior review documents or existing build artifacts were not treated as validation of this branch.

**Recommended after separately authorizing generated build-output changes:**

```text
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug :app:assembleRelease
./gradlew --no-daemon :app:lintDebug :app:lintRelease
./gradlew --no-daemon :app:connectedDebugAndroidTest
```

The connected instrumented-test command requires an appropriate Android device/emulator. Add and run the transport peer/conformance and lifecycle/UI regression suites listed above; the existing test suite alone does not validate those paths.
