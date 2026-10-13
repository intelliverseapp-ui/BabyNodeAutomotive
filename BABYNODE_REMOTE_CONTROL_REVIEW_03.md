# BabyNodeAutomotive Remote-Control Review — Third Revision

## Scope and method

This is a read-only review of the complete current branch, not only its latest commit or diff. I read `BABYNODE_REMOTE_CONTROL_REVIEW_01.md` and `BABYNODE_REMOTE_CONTROL_REVIEW_02.md` first, then inspected the current command mapper and dispatcher, RFCOMM transport and protocol, request/status models, Activity lifecycle and retry paths, Compose voice/debug surfaces, persistence, manifest/resources, Gradle configuration, JVM tests, and Android instrumentation tests.

No source, tests, Gradle files, manifests, configuration, or existing review reports were changed. This report is the only file created for this review. I did not execute Gradle because the task requires read-only handling; I inspected the existing generated test reports and build artifacts instead. Generated reports do not carry a source revision identifier, so they are evidence of recent runs, not a fresh build tied cryptographically to this exact source tree.

## Executive assessment

The current branch contains substantial, real safety improvements: natural-language mapping is now closed-phrase based; the dispatcher enforces a shared canonical allowlist; sensitive commands require a one-use confirmation object; protocol/version/response fields and frames are validated; transport writes and terminal results are correlated; cancellation, timeout, disconnect, and close use outcome-unknown language; stale socket work is generation-guarded; external intents do not dispatch commands; and release UI defaults to hiding debug controls. Android remains CAN-agnostic.

**The application is not ready for vehicle actuation.** The negotiated peer contract deliberately permits only a non-transmitting development stub and rejects a truthful actuator-capable peer. In addition, terminal outcomes persisted on Activity teardown or transport replacement are retained in event history but are not presented to the user after the originating request state is discarded. Request generations prevent local UI collisions, but they are not carried on the wire, so a stale/replayed response with a reused request ID and command is not distinguishable from a current response. A small parser edge case and test reliability gaps also remain.

## Verification evidence

| Claim | Evidence in repository | Independent conclusion |
|---|---|---|
| JVM tests pass | Existing [testDebugUnitTest report](app/build/reports/tests/testDebugUnitTest/index.html) reports **118 tests, 0 failures**, generated Oct 12, 2026 at 1:12 AM. | Supported by the existing report; not rerun in this read-only review. |
| 28 Android instrumentation tests pass on Samsung SM-F946U1 | Existing [connected Android test report](app/build/reports/androidTests/connected/debug/index.html) reports **28 tests, 0 failures**. The test output is under `SM-F946U1 - 16` and ends `OK (28 tests)`. | Supported by the existing device report/log; not rerun. |
| 11 deterministic RFCOMM conformance tests pass | The report lists 11 passing tests in `CarCanBusBluetoothConformanceTest`; the class drives the production transport through injected deterministic streams. | The 11 cases are present and reported passing. Not every case is fully deterministic: the stale-read regression uses a fixed delay rather than a completion signal (see new finding 3). |
| Debug and release builds pass | Existing debug, release, and androidTest APKs are present under `app/build/outputs/apk/`. The generated release [BuildConfig](app/build/generated/source/buildConfig/release/com/babynode/automotive/BuildConfig.java) has `DEBUG = false`. | Artifacts and release gating are present. No fresh `assembleDebug`/`assembleRelease` was run, so build success for this exact source was not independently re-established. |
| Release-style UI hides debug tools | [AutomotiveScreen.kt](app/src/main/java/com/babynode/automotive/ui/AutomotiveScreen.kt#L33) defaults the visibility flag to `BuildConfig.DEBUG`, and [MainActivity.kt](app/src/main/java/com/babynode/automotive/MainActivity.kt#L288) uses that default. | Production release call path hides the tools. The Compose tests explicitly set the flag false/true rather than launching the release APK. |
| Android has no vehicle-specific CAN transmission | Production source search found canonical JSON/RFCOMM commands and no vehicle CAN IDs, payload encoding, or CAN-frame send path. [CarCommandMap.kt](app/src/main/java/com/babynode/automotive/CarCommandMap.kt#L7) places vehicle mapping in BabyNodeCAN. | Verified for this repository only; no conclusion about absent peer firmware or a real vehicle. |
| External intents cannot directly dispatch commands | [MainActivity.kt](app/src/main/java/com/babynode/automotive/MainActivity.kt#L978) only inspects/rejects known external inputs; the manifest has only a launcher filter. The intent test exercises the inspection path and asserts no transport command is sent. | No direct external-command dispatch path found. The test calls the production inspection helper, rather than relying on OS intent resolution. |
| Audio recording permission is absent | The main manifest has Bluetooth permissions only; [AppPermissionTest.kt](app/src/androidTest/java/com/babynode/automotive/AppPermissionTest.kt) asserts that the installed package does not request `RECORD_AUDIO`. | Verified. |

## 1. Prior findings verification table

Each finding is classified exactly once. Details for every partial resolution follow the table.

### BABYNODE_REMOTE_CONTROL_REVIEW_01.md

| # | Prior finding | Status |
|---:|---|---|
| 1 | Natural-language input can trigger unintended actuation | PARTIALLY RESOLVED |
| 2 | Phase 1 allowlist is not enforced at dispatch/transport boundaries | RESOLVED |
| 3 | Module configuration is fire-and-forget and does not gate commands | RESOLVED |
| 4 | Cancellation can report “cancelled” after bytes may have been written | RESOLVED |
| 5 | Pending requests and status state are not cleaned up on close/replacement | PARTIALLY RESOLVED |
| 6 | UI request correlation relies on a conflated latest-event state | RESOLVED |
| 7 | Bluetooth identity is pinned on first use by display name | RESOLVED |
| 8 | No protocol handshake or response-version validation | PARTIALLY RESOLVED |
| 9 | Reconnection race and retry limit | RESOLVED |
| 10 | Voice confirmation and production/debug surface | RESOLVED |
| 11 | Timeout wording does not communicate an unknown outcome | RESOLVED |
| 12 | Large transport and duplicated logic increase maintenance risk | PARTIALLY RESOLVED |

### BABYNODE_REMOTE_CONTROL_REVIEW_02.md

| # | Prior finding | Status |
|---:|---|---|
| 1 | Handshake excludes an actuator-capable peer and advertised dual module | PARTIALLY RESOLVED |
| 2 | Activity destruction discards terminal unknown-outcome events | PARTIALLY RESOLVED |
| 3 | Fast handshake/configuration acknowledgments can be missed | RESOLVED |
| 4 | Immediate response can be overwritten by later `CommandSent` | RESOLVED |
| 5 | Request identity can collide across transport replacement | PARTIALLY RESOLVED |
| 6 | Punctuation normalization turns explicit questions into commands | RESOLVED |
| 7 | Receive-loop shutdown is not scoped to socket generation | RESOLVED |
| 8 | Bounded frame reader silently removes embedded carriage returns | RESOLVED |
| 9 | Forget-device persistence failure is ignored | RESOLVED |
| 10 | `RECORD_AUDIO` is declared although voice capture is delegated | RESOLVED |
| 11 | Safety-critical transport/UI behavior lacks integration regression tests | PARTIALLY RESOLVED |

### Details for prior findings that remain partial

#### Review 01, finding 1 — closed grammar still erases an unmatched quote

- **Severity:** Low
- **Exact file:** [CarCommandMap.kt](app/src/main/java/com/babynode/automotive/CarCommandMap.kt#L144)
- **Why it remains:** The broad token-set matching and question/negation cases are fixed, and sensitive actions now require a second confirmation. However, the quote check only rejects balanced outer single quotes. A leading unmatched quote is subsequently stripped during normalization and the remainder can exactly match an allowed phrase.
- **Concrete failure scenario:** `CarCommandMap.map("'turn headlights on")` returns `HEADLIGHTS_ON`; if the user submits the reviewed voice/typed text while module configuration is ready, the non-sensitive command can be sent.
- **Smallest safe remediation:** Reject unmatched ASCII/curly quote and apostrophe syntax before normalization.
- **Exact test coverage needed:** Add unmatched opening/closing quote cases for every mapped phrase to `CarCommandMapTest`; verify rejected input never reaches a fake transport.

#### Review 01, finding 5 — close/replacement results are retained but not reliably presented

- **Severity:** High
- **Exact files:** [MainActivity.kt](app/src/main/java/com/babynode/automotive/MainActivity.kt#L214) and [MainActivity.kt](app/src/main/java/com/babynode/automotive/MainActivity.kt#L1045); [BabyNodeApp.kt](app/src/main/java/com/babynode/automotive/BabyNodeApp.kt#L177); [AutomotiveScreen.kt](app/src/main/java/com/babynode/automotive/ui/AutomotiveScreen.kt#L253); [VoiceInputSection.kt](app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt#L76)
- **Why it remains:** Close/replacement terminal errors are now returned and retained in `CarStatusBus`, and Activity destruction attempts to persist them. However, the UI request state is held in `remember` state keyed by the dispatcher. After replacement/recreation the new voice section starts with `activeRequest == null`; its READY state renders “No voice request submitted” and does not select a persisted event from history. In release, the debug event-history panel is hidden. Also, `onDestroy()` persists only events returned by `transport.close()`; if the peer response already removed the request from the transport’s pending table, the already-received response/error in `statusBus` is not persisted at all. A failed preference commit is only logged.
- **Concrete failure scenario:** A command write begins, then the Activity is rotated or the transport is replaced before its response. The old transport returns outcome unknown and it is persisted, but the new release UI has no originating active request and displays no unknown result. Alternatively, the peer response arrives just before rotation; `close()` returns no pending terminal event and the accepted/rejected result is lost with the old `statusBus`. In either case, the user can submit the same command again without seeing the prior outcome.
- **Smallest safe remediation:** Preserve the request’s user-visible terminal outcome (whether it was received before close or synthesized by close) outside destroyed Compose state; show it after recreation/replacement until explicitly dismissed. Treat persistence failure as an explicit unresolved state, not log-only. Never replay the command automatically.
- **Exact test coverage needed:** Instrumented scenarios with a real `CarCanBusBluetooth` test connection for both (a) destruction before response and (b) response immediately before destruction. Verify the exact command and terminal/unknown status appear in the new release-style UI and no replay occurs. Add a persistence-failure test proving the UI does not claim a clean restart.

#### Review 01, finding 8 — protocol validation is improved, but session identity is not request identity on the wire

- **Severity:** Medium
- **Exact files:** [CarCanBusBluetooth.kt](app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L64), [CarCanBusBluetooth.kt](app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L612), [CarCanBusBluetooth.kt](app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L1229), [CarCanBusBluetooth.kt](app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L1382), [CarCanTransport.kt](app/src/main/java/com/babynode/automotive/CarCanTransport.kt#L20)
- **Why it remains:** Each transport starts its wire request counter at 1 and sends only `id`, version, type, command, and optional value. `sessionGeneration` is local metadata on requests/events; it is not serialized. A session ID is checked in the handshake response, but is not retained and required on subsequent responses. Response lookup is by request ID and then exact command. Thus the local UI identity is improved, but the peer response namespace is not session-bound.
- **Concrete failure scenario:** After reconnect, a delayed/replayed response from a previous connection has the same ID and command as a new pending request. The transport accepts it as the current response, which can falsely tell the user that BabyNodeCAN accepted the new request even if the new request was not acted on.
- **Smallest safe remediation:** Define a session nonce in the protocol and include/bind it to every request and response, or use a wire request identifier that cannot be reused across sessions and is validated by the peer. Continue matching both session and request identity locally.
- **Exact test coverage needed:** Fake peer test: establish session A and record a response; reconnect with session B; submit the same command using a reused numeric ID; replay A’s response and assert it is ignored and does not terminate B’s request; then send B’s valid response and assert exactly one terminal result.

#### Review 01, finding 12 — testability improved, but transport/UI responsibilities remain broad

- **Severity:** Low
- **Exact files:** [CarCanBusBluetooth.kt](app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L1) (the class remains approximately 1,700 lines); [VoiceInputSection.kt](app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt#L50); [CommandTestSection.kt](app/src/main/java/com/babynode/automotive/ui/CommandTestSection.kt#L40)
- **Why it remains:** Pure protocol/frame/generation/outcome helpers and an injectable RFCOMM stream seam now exist, and the actual transport state machine has conformance tests. The transport still combines socket lifecycle, writes, response parsing, pending requests, and outcomes; Voice and typed sections also duplicate request-state logic.
- **Concrete failure scenario:** A future change to response parsing or close handling can change pending-request cleanup and terminal-event ordering in the same transport class, while parallel UI correlation implementations can drift. This is primarily a maintenance/regression risk, not evidence of a present direct actuation bypass.
- **Smallest safe remediation:** No broad refactor is required before merge for this concern alone. Continue the extraction incrementally when changing these paths, starting with one shared request-state/UI component and one transport pending-request component.
- **Exact test coverage needed:** When extracting, retain the current 11 fake-stream conformance scenarios and add unit tests for per-request terminal-state monotonicity and interleaved UI request state. No separate test can prove a class-size reduction.

#### Review 02, finding 1 — safe stub-only capability contract is not a production actuator contract

- **Severity:** High for any vehicle-actuation release
- **Exact files:** [BabyNodePeerContract.kt](app/src/main/java/com/babynode/automotive/BabyNodePeerContract.kt#L8), [MainActivity.kt](app/src/main/java/com/babynode/automotive/MainActivity.kt#L33)
- **Why it remains:** The dual module is no longer offered, and the selected module is explicitly labelled a development stub. This is a sound fail-closed scope reduction. However, the contract accepts only `single`, `can_transmit == false`, and `vehicle_commands == "stub_only"`; it explicitly does not authorize physical actuation. A real actuator-capable or dual peer is rejected.
- **Concrete failure scenario:** When a real BabyNodeCAN peer reports its truthful actuator capability or dual-module identity, the handshake rejects it and the app never becomes command-ready. The current source therefore cannot support the advertised physical vehicle-control use case; loosening the check without a verified peer contract would be unsafe.
- **Smallest safe remediation:** Keep physical actuation disabled and make the development-stub-only scope unmistakable in the user-facing surface until the Android/firmware protocol and supported vehicle/module capabilities are jointly defined and reviewed. Only then add a versioned capability contract and enable exactly those reviewed combinations.
- **Exact test coverage needed:** Cross-repository protocol conformance tests for every supported module/capability tuple; assert unsupported or actuator-capable tuples fail closed until approved, and assert no UI control implies physical actuation when only the stub contract is enabled.

#### Review 02, finding 2 — Activity teardown persists results but drops their user-visible request association

- **Severity:** High
- **Exact files:** [MainActivity.kt](app/src/main/java/com/babynode/automotive/MainActivity.kt#L1045), [BabyNodeApp.kt](app/src/main/java/com/babynode/automotive/BabyNodeApp.kt#L177), [VoiceInputSection.kt](app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt#L76), [AutomotiveScreen.kt](app/src/main/java/com/babynode/automotive/ui/AutomotiveScreen.kt#L253)
- **Why it remains:** The prior “events are discarded” implementation defect is partly fixed: `close()` returns pending-request terminal events and `onDestroy()` persists them. But `onCreate()` restores only a bus event list; it does not restore the originating voice/typed request state. The normal release UI does not render the restored event as a pending/unknown request. Already-received outcomes are not carried over, and persist failure is only logged.
- **Concrete failure scenario:** Rotation/destruction occurs after a possibly transmitted `UNLOCK_DOORS`; the new Activity shows no request-specific unknown state and permits a repeat submission. The same loss occurs if a valid response arrives just before destruction and has already been removed from the pending map.
- **Smallest safe remediation:** Restore a visible request outcome independently from transport/UI request objects, with explicit “may have executed; do not assume failure” wording and no automatic replay. Preserve already-received terminal responses as well as close-generated unknown results.
- **Exact test coverage needed:** End-to-end Activity recreation from a real pending fake-RFCOMM request and from a request whose response arrives just before recreation; assert the restored release UI shows the exact terminal/unknown result and no additional command is written.

#### Review 02, finding 5 — local generation is not propagated to peer correlation

- **Severity:** Medium
- **Exact files:** [CarCanTransport.kt](app/src/main/java/com/babynode/automotive/CarCanTransport.kt#L20), [CarCanBusBluetooth.kt](app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L612), [CarCanBusBluetooth.kt](app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L1320), [CarRequestEventMatcher.kt](app/src/main/java/com/babynode/automotive/CarRequestEventMatcher.kt#L27)
- **Why it remains:** `sessionGeneration` correctly separates local requests/events and Compose is keyed to dispatcher replacement. The wire ID still restarts per transport and does not carry that generation/session. This is the same residual protocol-correlation issue detailed for Review 01 finding 8.
- **Concrete failure scenario:** A late or replayed prior-session response with matching ID and command resolves a new session’s pending request.
- **Smallest safe remediation:** Make the negotiated session identity part of every protocol request/response correlation or make the peer-visible request IDs non-reusable.
- **Exact test coverage needed:** Cross-session replay test described for Review 01 finding 8, plus an assertion that same-ID requests from different sessions never match in the parser/transport.

#### Review 02, finding 11 — tests cover much more, but not the full user-visible safety path

- **Severity:** Medium
- **Exact files:** [CarCanBusBluetoothConformanceTest.kt](app/src/androidTest/java/com/babynode/automotive/CarCanBusBluetoothConformanceTest.kt#L35), [MainActivityLifecycleTest.kt](app/src/androidTest/java/com/babynode/automotive/MainActivityLifecycleTest.kt#L44), [TerminalOutcomePersistenceTest.kt](app/src/androidTest/java/com/babynode/automotive/TerminalOutcomePersistenceTest.kt#L33), [VoiceInputSectionTest.kt](app/src/androidTest/java/com/babynode/automotive/VoiceInputSectionTest.kt#L27)
- **Why it remains:** The 11 RFCOMM cases exercise the actual transport with fake streams and cover important write/response/timeout/close/reverse-order behavior. Separate Compose and Activity tests exist. However, Activity lifecycle tests synthesize close events rather than keeping a real request pending through recreation; persistence tests exercise serialization directly rather than UI display; the voice UI tests review/cancel but do not prove the full sensitive-confirmation-to-write path in the UI; adapter discovery/permission/device approval is not run through an injected adapter; and one stale-loop test relies on a fixed delay.
- **Concrete failure scenario:** A unit or transport test passes while a recreated release UI hides the restored result, or a real adapter-selection path differs from the test installation seam.
- **Smallest safe remediation:** Add focused integration tests around the actual missing user-visible and device-selection boundaries; retain pure helper tests rather than replacing them.
- **Exact test coverage needed:** Pending request → Activity destruction → persisted restore → visible unknown status/no replay; voice review → submit sensitive command → confirmation cancel and confirmation accept → exact one transport write; and a deterministic adapter/connection-factory test for first approval, pinned identity, disconnect, and reconnect.

## 2. Newly discovered findings

### 1. LOW — Test-only RFCOMM installation hook is compiled into production source without a release guard

- **File and lines:** [CarCanBusBluetooth.kt](app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt#L153); related mutable Activity factory in [MainActivity.kt](app/src/main/java/com/babynode/automotive/MainActivity.kt#L48)
- **Why it matters:** The production defaults are the Android RFCOMM factory and `CarCanBusBluetooth`, and repository search found no production call site for the test installation hook. The `internal` test hooks nevertheless live in the main source set and compile into release artifacts; Kotlin `internal` is a source-level visibility boundary, not a runtime build guard. `installConnectionForTest()` bypasses device discovery/approval and installs an arbitrary `RfcommConnection` into the real transport state machine.
- **Concrete failure scenario:** A future same-module production call accidentally invokes the test hook and replaces the approved-device path with a supplied stream connection. No current external intent or production call path does this.
- **Smallest safe remediation:** Keep injection out of the release source set where practical, or explicitly make test-install operations unavailable in release builds while preserving the production constructor/factory defaults.
- **Exact test coverage needed:** A release-variant test/build assertion that the test-install entry point is absent or rejects invocation, plus a debug instrumentation assertion that the seam still exercises the production transport state machine.

### 2. LOW — The stale receive-loop regression test has timing-only completion synchronization

- **File and lines:** [CarCanBusBluetoothConformanceTest.kt](app/src/androidTest/java/com/babynode/automotive/CarCanBusBluetoothConformanceTest.kt#L1908)
- **Why it matters:** After releasing the old fake reader’s failure, the test waits `250 ms` and asserts that the new socket remains connected. There is no latch/flow signal that the old receive coroutine has processed the released failure. Under a delayed test scheduler/device, the assertion can run before the stale-loop path has completed and falsely pass.
- **Concrete failure scenario:** A regression would let an old loop close the new socket, but the stale coroutine is not scheduled within the fixed wait; the test checks early and passes.
- **Smallest safe remediation:** Give the fake connection/transport an explicit completion signal and await either the stale-loop cleanup decision or a bounded terminal condition before asserting.
- **Exact test coverage needed:** Deterministically block the old read, install the new connection, release the old read, await the old job’s completion/ignored-failure signal, then assert the new socket remains open and no disconnect event was emitted.

## 3. Test-coverage gaps

- **Terminal outcome display:** Persistence tests prove event serialization/one-time consumption, and the Activity lifecycle test proves a synthetic close result is saved. Neither proves a pending transport request’s unknown outcome is visible after recreation; the release UI currently has no active request to correlate it.
- **Actual connect/device-selection boundary:** `installConnectionForTest()` bypasses Android adapter discovery and bonded-device approval. There is no injected adapter test for candidate count, explicit approval, pinned address/name mismatch, and actual connection installation.
- **Sensitive voice confirmation:** UI tests prove recognition review and cancellation, and dispatcher JVM tests exercise confirmation identity/replay. A UI test should click through successful review and sensitive confirmation, plus cancel at the second confirmation, asserting the exact write count.
- **Stale-session peer response:** Existing duplicate/late/reverse-order tests are within a transport session. Add old-session replay after reconnect, because the peer-visible response is not session-bound.
- **Malformed response integration:** Pure protocol tests validate strict fields, and conformance tests cover unsupported status/mismatched command. Add transport-level assertions that missing/wrong version, malformed JSON, wrong ID types, and invalid handshake capability fields cannot mark configuration ready or produce a successful response.
- **Lifecycle/retry synchronization:** Retry tests poll real wall-clock state with `SystemClock.sleep`; the stale-receive test uses a fixed delay. Use explicit latches/flows or a controlled clock where possible to reduce device scheduling flakiness.
- **Release-style visibility:** The tests exercise the Compose parameter with `false` and `true`; source default and generated release `BuildConfig.DEBUG=false` support the actual release behavior, but an APK-level release surface assertion is absent.
- **Forget failure path:** Production checks `commit()` and does not replace the transport on failure; the pure decision is tested. There is no failing-preference integration test proving the visible error path.
- **Full branch build freshness:** Existing recent reports/APKs support the stated baseline, but there is no fresh Gradle run in this review. Before release, run the unit, debug/release assembly, lint, and connected instrumentation tasks on the intended source revision.

## 4. Production-readiness assessment

### Safety and behavior verified

- **Fail-closed command mapping:** Only five exact phrases currently map to commands; arbitrary tokens and multiple intents are rejected. Questions, quoted phrases, negation, out-of-scope actions, and unsupported canonical values have broad JVM test coverage. The unmatched quote case above remains.
- **Canonical boundary:** `CarCommandDispatcher` validates the shared Phase 1 allowlist and canonical syntax before user command submission. Production voice input and debug typed input both route through that dispatcher. Android sends canonical JSON and remains CAN-agnostic.
- **Sensitive actions:** `UNLOCK_DOORS` and `TRUNK_OPEN` return an exact one-use confirmation object. It is consumed before sending; newer submissions invalidate older confirmations; stale/reconstructed objects fail. Voice review and explicit second confirmation UI are present. Cancellation is covered; affirmative UI integration remains weakly tested.
- **Request/terminal outcomes:** Transport pending requests are keyed by request ID; responses must match request ID and exact command; terminal response/error outranks later `CommandSent`; timeout/cancel/write failure use conservative outcome-unknown wording. Close/disconnect races use conditional map removal so competing timeout/response/close paths do not normally emit multiple terminal events. Local matching additionally includes session generation.
- **Transport lifecycle:** Socket generations guard receive-loop processing and cleanup. Disconnect/close invalidate old work and pending requests receive unknown outcomes. The injected stream suite tests stale read, cancellation after write starts, flush failure, duplicate/mismatched responses, reverse order, timeout, and close. Wire-level session correlation and user-visible restored outcomes remain incomplete.
- **Retry policy:** Activity connection work is generation-guarded, retries are bounded, manual retry resets attempts, and the UI exposes retry in disconnected/failed states. The retry test covers exhaustion/manual recovery; synchronization is wall-clock based.
- **External/UI/manifest:** The exported activity has only a launcher filter. Known deep-link, Assistant, and processed-text values are inspected/rejected, not dispatched. `RECORD_AUDIO` and `INTERNET` are absent. Bluetooth runtime permissions are declared/requested for supported versions. Release code defaults debug-only command tester/event UI off.
- **Test seams:** Production defaults remain the Android factory and default transport. The stream seam is narrow and routes send/receive/correlation through production transport code, but the test installer and mutable Activity factory are still in the main source set and should be excluded/guarded in release.

### Readiness conclusion

The branch is materially safer than both prior revisions, and the existing test artifacts support the claimed 118 JVM / 28 device test pass. However, **do not use or describe this implementation as ready for physical vehicle actuation**: the peer contract explicitly supports only a non-transmitting development stub. Even for the stub workflow, the user-visible terminal-outcome gap and unbound wire session identity should be addressed before treating outcomes as reliable across lifecycle/reconnect boundaries.

## 5. Exact remaining work, ordered into small steps

1. **Make scope explicit:** Keep the current stub-only capability gate. Clearly label the release surface as development/stub-only and prevent claims of physical vehicle control until a reviewed actuator-capable peer contract exists.
2. **Surface unknown outcomes:** Add a persistent request-outcome UI model that restores after Activity recreation and transport replacement. Show exact command and “may have executed”; never automatically retry. Add the pending-request recreation UI test.
3. **Bind wire responses to a session:** Define session-scoped wire correlation, serialize the negotiated session identity in subsequent requests/responses, validate it on every response, and add a prior-session replay test.
4. **Close malformed quote normalization:** Reject unmatched quote/apostrophe syntax before normalization and add map-plus-fake-transport regression cases.
5. **Harden test-only hooks:** Remove release availability for the stream installer and test factory hooks, or guard them to debug/test variants; verify release call paths retain only the default Android transport.
6. **Make race tests deterministic:** Replace the stale-receive fixed delay and retry polling with explicit completion signals/controlled synchronization; rerun the complete 11-case conformance suite.
7. **Fill narrow integration gaps:** Add successful sensitive-confirmation UI coverage, failing-forget persistence coverage, and fake-adapter approval/identity tests.
8. **Revalidate the intended source revision:** Run JVM tests, debug and release assembly/lint, and connected Android instrumentation on the target device; record the source revision and exact task outcomes.

## 6. Final verdict

**NOT READY TO MERGE**
