# BabyNodeAutomotive Remote-Control Review 04

## 1. Scope and review method

This is the fourth complete, read-only review of the current `babynode-remote-control-review` branch. The three prior reports were read completely before inspecting the implementation:

- [BABYNODE_REMOTE_CONTROL_REVIEW_01.md](./BABYNODE_REMOTE_CONTROL_REVIEW_01.md)
- [BABYNODE_REMOTE_CONTROL_REVIEW_02.md](./BABYNODE_REMOTE_CONTROL_REVIEW_02.md)
- [BABYNODE_REMOTE_CONTROL_REVIEW_03.md](./BABYNODE_REMOTE_CONTROL_REVIEW_03.md)

I reviewed the whole current tracked application rather than limiting the review to the latest commits: command mapping and allowlisting; dispatcher, confirmation, and configuration gates; request and response correlation; session and socket generations; transport writes and terminal outcomes; RFCOMM setup, receive loops, reconnects, and close behavior; Activity and Compose lifecycle; outcome persistence; Bluetooth identity approval; external-intent handling; manifest permissions; debug/release surfaces; Gradle settings; JVM tests; Android instrumentation tests; and available test/build artifacts.

This review is read-only. No source, test, Gradle, manifest, resource, configuration, or existing review file was changed. No build, test, lint, or format command was run. The only file created is this report.

Status terms used below:

- **RESOLVED** — the current implementation closes the finding's reported defect.
- **PARTIALLY RESOLVED** — the defect is improved but a material part remains.
- **STILL OPEN** — the finding remains materially true.
- **SUPERSEDED** — later behavior or scope makes the original finding no longer applicable as phrased; any related residual risk is described separately.

## 2. Current revision and branch

- **Branch:** `babynode-remote-control-review`
- **HEAD:** `1857dd6dcf5eae731f39409a87f3d39d1c141689`
- **HEAD commit:** `docs: add Baby Node remote control review reports`
- **HEAD commit time:** 2026-10-12 11:00:58 -04:00
- **Worktree before this report was created:** clean

The implementation fixes immediately preceding HEAD include `686b7ec` (reject unmatched command quotes), `2c27e02` (signal-driven stale receive-loop test completion), and `0a21b68` (reject the RFCOMM test installer in release builds). Their current source and test changes were independently inspected.

## 3. Executive assessment

The current branch is materially safer than the first three reviewed versions. Current source has a small closed command-phrase map; rejects questions, quoted input, and unmatched quote syntax before normalization; enforces the shared canonical allowlist at dispatch; requires one-use confirmation for sensitive commands; gates user commands on a validated handshake and acknowledged module configuration; validates bounded protocol frames and response fields; prioritizes terminal outcomes over `CommandSent`; scopes local request matching to a persisted transport generation; guards socket cleanup by generation; and has an injectable fake-RFCOMM test path with deterministic stale-reader completion. Debug command tools are hidden by the release default. External intents do not dispatch commands, the approved bonded-device identity requires user approval, and the manifest does not request microphone recording permission.

**The branch is not ready to merge as a dependable remote-control application.** A critical user-visible result gap remains across Activity teardown/recreation: only pending-request close errors are persisted, already-received outcomes are discarded, and restored errors are not shown by the release UI. In addition, wire responses are not bound to the negotiated peer session. Local generations do not prevent an old peer response with a reused wire ID and command from being accepted in a new session.

This app is **not ready for physical vehicle actuation**. The current peer contract accepts only a `single` module with `can_transmit == false` and `vehicle_commands == "stub_only"`; it explicitly sets `allowsVehicleActuation = false`. This is a deliberate fail-closed development scope, not evidence that an actuator-capable peer or vehicle integration works. Android source remains CAN-agnostic and has no vehicle-specific CAN identifiers, payloads, or CAN transmission path.

## 4. Verification evidence

### Work performed for this review

| Evidence | Result |
|---|---|
| Prior reports | All three required reports read in full before current implementation review. |
| Current source/tests/configuration | Inspected directly across the complete tracked Android app, including the command, transport, lifecycle, persistence, UI, manifest, and test surfaces. |
| Tests/build/lint run during this review | **None.** No test or build pass is claimed as a result of this review. |
| Worktree before report creation | Clean. The report is the only file created. |

### Existing generated artifacts inspected

| Artifact | What it reports | Freshness and limitation |
|---|---|---|
| `app/build/test-results/testDebugUnitTest/*.xml` and the Gradle unit-test HTML report | 14 suites, 120 tests, zero reported failures/errors; XML timestamps are 2026-10-12 10:53:55 -04:00. | Existing evidence only, not rerun here and not cryptographically tied to HEAD. The artifact timestamp precedes the commit time of the unmatched-quote fix; therefore it does not establish that the exact HEAD source passed all 120 tests. The quote test exists in current source. |
| `app/build/outputs/androidTest-results/connected/debug/TEST-SM-F946U1 - 16-_app-.xml` and connected-test HTML report | 11 RFCOMM conformance tests, zero reported failures/errors, device `SM-F946U1 - 16`, timestamp 2026-10-12 10:59:08 -04:00. The report includes `staleReceiveFailureCannotCloseNewConnection`. | This is the conformance class, not evidence that the complete current Android instrumentation suite ran. It is not tied to HEAD; its timestamp precedes the last release-hook guard commit. |
| `app/build/outputs/apk/debug/app-debug.apk`, `app/build/outputs/apk/release/app-release-unsigned.apk`, and Android-test APK | Debug, unsigned release, and androidTest APK artifacts exist. Debug/release APK timestamps are 10:59:02 and 10:58:59 -04:00. | Artifact presence is not a fresh build result for HEAD. No exact source revision was embedded or established during this review. |
| Current test source | JVM test classes and Android instrumentation source are present, including 11 RFCOMM conformance test methods. | Source coverage is not a test pass. Test cases listed below as absent were inferred from source and were not executed. |

No fresh output for the current full Android instrumentation suite was established. Earlier review reports' test counts are historical and are not substituted for current-branch execution evidence.

## 5. Complete Review 01 findings status

| # | Review 01 finding | Status | Current disposition |
|---:|---|---|---|
| 1 | Natural-language input can trigger unintended actuation | **RESOLVED** | Exact closed phrase mapping, question/quote rejection, negation handling, and unmatched-quote rejection are present. |
| 2 | Phase 1 allowlist is not enforced at dispatch/transport boundaries | **RESOLVED** | The shared allowlist and canonical syntax are checked at the dispatcher boundary. |
| 3 | Module configuration is fire-and-forget and does not gate commands | **RESOLVED** | Handshake and module-configuration acknowledgments gate user commands. See the remaining integration-test gaps for ordering coverage. |
| 4 | Cancellation can report “cancelled” after bytes may have been written | **RESOLVED** | A possibly-started write retains request tracking and uses outcome-unknown semantics. |
| 5 | Pending requests and status state are not cleaned up on close/replacement | **PARTIALLY RESOLVED** | Close/replacement returns or retains unknown outcomes, but Activity teardown loses already-received terminal outcomes and restored outcomes are not visible in release. See Finding 1. |
| 6 | UI request correlation relies on a conflated latest-event state | **RESOLVED** | Bounded event history and a shared request matcher are used; terminal outcomes outrank `CommandSent`. Wire-level session correlation remains separately open. |
| 7 | Bluetooth identity is pinned on first use by display name | **RESOLVED** | First-use approval is explicit, identity is pinned by validated address, and forget failure is surfaced. |
| 8 | No protocol handshake or response-version validation | **PARTIALLY RESOLVED** | Version, response fields, and stub capabilities are validated, but the negotiated session identity is not bound to subsequent wire requests/responses. See Finding 2. |
| 9 | Reconnection race and retry limit | **RESOLVED** | Activity and socket generations, bounded retries, manual retry, and a synchronized stale-receive regression are present. |
| 10 | Voice confirmation and production/debug surface | **RESOLVED** | Voice review precedes submission; sensitive actions require a one-use second confirmation; debug UI defaults off in release. |
| 11 | Timeout wording does not communicate an unknown outcome | **RESOLVED** | Timeout and potentially-transmitted failures use outcome-unknown wording. |
| 12 | Large transport and duplicated logic increase maintenance risk | **PARTIALLY RESOLVED** | Pure helpers and a real-transport conformance seam exist, but `CarCanBusBluetooth` and voice/typed request-state logic remain broad/duplicated. This is not by itself a merge blocker; avoid broad aesthetic refactoring. |

## 6. Complete Review 02 findings status

| # | Review 02 finding | Status | Current disposition |
|---:|---|---|---|
| 1 | Handshake excludes an actuator-capable peer and advertised dual module | **PARTIALLY RESOLVED** | The dual option is no longer offered and the app now expressly supports only a non-transmitting single-module stub. The actuator-capable peer remains unsupported by design; physical-actuation readiness is absent. |
| 2 | Activity destruction discards terminal unknown-outcome events | **PARTIALLY RESOLVED** | Pending-close errors are saved and consumed once, but results already received before close are not saved, and restored errors are not rendered in the release UI. See Finding 1. |
| 3 | Fast handshake/configuration acknowledgments can be missed | **RESOLVED** | The dispatcher buffers early protocol events and consumes them against the exact request identity. The lifecycle/integration tests do not yet prove the full Activity path. |
| 4 | Immediate response can be overwritten by later `CommandSent` | **RESOLVED** | The shared matcher gives a matching terminal response/error priority over a send event. |
| 5 | Request identity can collide across transport replacement | **PARTIALLY RESOLVED** | Local request/event identities include a persisted transport generation, but the wire ID still restarts and the peer session ID is not part of response correlation. See Finding 2. |
| 6 | Punctuation normalization turns explicit questions into commands | **RESOLVED** | `?` and quote syntax are rejected before normalization; question forms are covered in current tests. |
| 7 | Receive-loop shutdown is not scoped to socket generation | **RESOLVED** | Socket-generation checks gate stale receive-loop cleanup, and current conformance source waits for an explicit old-loop completion signal. |
| 8 | Bounded frame reader silently removes embedded carriage returns | **RESOLVED** | CRLF termination is accepted while an embedded carriage return is rejected. |
| 9 | Forget-device persistence failure is ignored | **RESOLVED** | The failure decision is checked and reported instead of proceeding as if the stored identity was removed. |
| 10 | `RECORD_AUDIO` is declared although voice capture is delegated | **RESOLVED** | The current manifest and installed-package assertion omit `RECORD_AUDIO`. |
| 11 | Safety-critical transport/UI behavior lacks integration regression tests | **PARTIALLY RESOLVED** | Actual RFCOMM conformance coverage has grown substantially, but lifecycle restoration, release UI outcome visibility, successful sensitive confirmation, adapter approval, and other end-to-end boundaries remain unproven. See Section 10. |

## 7. Complete Review 03 findings status

The table covers the residual issues and new findings called out in Review 03, in addition to the Review 01/02 dispositions above.

| # | Review 03 finding | Status | Current disposition |
|---:|---|---|---|
| 1 | Unmatched quote can normalize to an allowed command | **RESOLVED** | Current mapping rejects unmatched ASCII/curly single-quote syntax before normalization; current table-driven source tests cover open and close quote cases for every mapped phrase. |
| 2 | Close/replacement outcomes are retained but not reliably presented | **PARTIALLY RESOLVED** | Pending close errors are persisted, but already-received responses/errors are not, and the release UI has no restored request state. See Finding 1. |
| 3 | Local session identity is not request identity on the wire | **STILL OPEN** | The request session generation is local metadata. No session nonce is serialized on each command/response, and the validated handshake `session_id` is not retained for response matching. See Finding 2. |
| 4 | Broad transport and duplicated UI responsibilities | **PARTIALLY RESOLVED** | Testability improved through helpers and injected streams; the remaining class size/duplication is a maintenance concern, not a justification for broad pre-merge refactoring. |
| 5 | Stub-only capability contract is not a production actuator contract | **STILL OPEN for vehicle-actuation readiness** | This is intentionally fail-closed: the current app supports a non-transmitting development stub only. No actuator-capable contract is verified here. |
| 6 | Test suite misses full user-visible safety paths | **PARTIALLY RESOLVED** | Current tests cover more transport and UI cases, but they do not demonstrate the critical lifecycle/persistence path end-to-end or a live Bluetooth approval boundary. See Section 10. |
| 7 | RFCOMM test installer not guarded in release | **RESOLVED** | `installConnectionForTest()` checks release `BuildConfig.DEBUG` and throws when false. A separate mutable Activity transport factory remains in main source; see New Finding 3. |
| 8 | Stale receive-loop test uses fixed-delay completion | **RESOLVED** | The current test releases the old read failure and waits for an explicit receive-loop-finished latch before asserting the new connection remains active. |

## 8. Detailed explanation of findings not fully resolved

### Finding 1 — Terminal outcomes do not survive teardown as visible request outcomes

- **Severity:** HIGH for any actuator-capable use; material correctness gap even in the current stub workflow.
- **Files/locations:** `app/src/main/java/com/babynode/automotive/MainActivity.kt` (`onCreate`, approximately lines 214–225; `onDestroy`, approximately lines 1045–1080); `app/src/main/java/com/babynode/automotive/BabyNodeApp.kt` (`saveTerminalRequestOutcomes` and `consumeTerminalRequestOutcomes`, approximately lines 177–345); `app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt` (`close` and pending-command terminalization, approximately lines 940–1000 and 1560–1590); `app/src/main/java/com/babynode/automotive/ui/AutomotiveScreen.kt` (release rendering); `app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt` (READY state and transport status rendering, approximately lines 758 and 826).
- **Why it matters:** `onDestroy()` persists only the request-specific errors returned by `transport.close()`. A response or request-specific error received before destruction has already been removed from the transport's pending table, so `close()` returns no such event and the event-history state is discarded with the Activity. On recreation, the app restores persisted close errors into `CarStatusBus`, but the voice section's `remember` state has no `activeRequest` and renders “No voice request submitted.” The debug history panel is absent in release. Thus even a persisted unknown result has no visible request-associated presentation. A failed preference commit is only logged. Abrupt process termination is an additional case in which `onDestroy()` cannot be relied upon to run.
- **Concrete failure scenario:** A user submits a command, BabyNodeCAN responds (or may have received it), and the Activity is rotated or destroyed. For a received response, the app does not persist it. For a still-pending request, it persists an unknown error, but the recreated release UI does not show that result. The user can submit the same command again without seeing whether the prior request was accepted or may have executed.
- **Smallest safe remediation:** Maintain a durable, user-visible outcome record keyed by session/request identity for every terminal result and every possibly-transmitted unresolved request. Restore and render it in the release UI until explicitly acknowledged/dismissed; make persistence failure visible and fail closed. Never automatically replay a command.
- **Exact regression/integration test:** Using a fake RFCOMM connection through the production transport and an ActivityScenario, cover (1) request pending during Activity destruction, (2) successful/rejected response immediately before destruction, and (3) persistence commit failure. Recreate the Activity and assert the exact command and accepted/rejected/outcome-unknown wording appears in release-style UI and no command is retransmitted.

### Finding 2 — Peer responses are not bound to the negotiated session

- **Severity:** MEDIUM for the current non-transmitting stub; HIGH as a blocker to any actuator-capable protocol.
- **Files/locations:** `app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt` (`nextMessageId`, request JSON construction, and incoming response matching; approximately lines 110–115, 612–670, and 1220–1485); `app/src/main/java/com/babynode/automotive/CarCanTransport.kt` (`CarCommandRequest.sessionGeneration`); `app/src/main/java/com/babynode/automotive/BabyNodeProtocol.kt` (`validateHandshakeCapabilities`); `app/src/main/java/com/babynode/automotive/BabyNodePeerContract.kt`.
- **Why it matters:** The transport allocates a local persisted `sessionGeneration`, but serializes only the numeric request ID, protocol version, command type, command, and optional value. The handshake response's `session_id` is validated, but it is not retained or required on subsequent responses. The response parser finds pending work by wire `id`, then exact command. A new transport starts `nextMessageId` at 1; generation is attached to an accepted event from the locally found request, not proven by the peer response.
- **Concrete failure scenario:** Session A uses wire ID 1 for `LOCK_DOORS`; after reconnect/replacement, session B again uses ID 1 for the same command. If the peer delivers or replays A's delayed response in B, the app matches it to B's pending request and presents a response for a command that B has not acknowledged.
- **Smallest safe remediation:** Extend the versioned peer contract so a negotiated, unpredictable session identity is included in every command and response and checked against the active session, or use a peer-visible request namespace that cannot be reused across sessions. Do not relax the current capability gate to enable actuation before cross-peer contract verification.
- **Exact regression/integration test:** A deterministic peer test establishes session A, captures a response, reconnects as session B, submits the same command with a reused numeric ID, replays A's response, and asserts it is ignored and cannot resolve B. Then send B's valid response and assert exactly one terminal outcome for B.

### Finding 3 — Activity transport-factory override remains a release-compiled test seam

- **Severity:** LOW; no current external or production call path was found.
- **Files/locations:** `app/src/main/java/com/babynode/automotive/MainActivity.kt` (companion `transportFactory` and reset helper, approximately lines 48–56); `app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt` (`installConnectionForTest`, approximately lines 153–162); `app/build.gradle.kts` (release `isMinifyEnabled = false`, line 27).
- **Why it matters:** The RFCOMM installer itself now correctly rejects use when `BuildConfig.DEBUG` is false. Separately, `MainActivity.transportFactory` remains a mutable `internal` property in the main source set with no release guard. Kotlin `internal` is a source/module visibility boundary, not a runtime build-variant guard. Its default remains the production transport and current production paths do not mutate it, so this is not a demonstrated remotely reachable bypass. It does leave a same-module production override seam in release code.
- **Concrete failure scenario:** A future in-module production path, or unintended in-process call, replaces the approved Bluetooth transport factory with a test/custom transport in a release build, bypassing the intended device approval and RFCOMM selection route.
- **Smallest safe remediation:** Put the override in a debug/test source set or make release use of the override explicitly reject mutation while leaving production construction fixed to `CarCanBusBluetooth`.
- **Exact regression/integration test:** Build/inspect the release variant and assert the test override setter is absent or rejects mutation, while a debug instrumentation test confirms the test factory still supports lifecycle tests. Keep the existing `installConnectionForTest()` release rejection assertion.

### Finding 4 — Current peer contract intentionally cannot authorize vehicle actuation

- **Severity:** HIGH blocker for physical vehicle actuation; not a defect in the current fail-closed stub contract.
- **Files/locations:** `app/src/main/java/com/babynode/automotive/BabyNodePeerContract.kt` (`SUPPORTED_MODULE`, `SUPPORTED_VEHICLE_COMMAND_CAPABILITY`, `allowsVehicleActuation`, approximately lines 8–25); `app/src/main/java/com/babynode/automotive/MainActivity.kt` (`ModuleType`, approximately lines 30–40); `app/src/main/java/com/babynode/automotive/CarCommandMap.kt`; `app/src/main/java/com/babynode/automotive/CarCanTransport.kt`.
- **Why it matters:** Handshake validation accepts only `single`, `can_transmit == false`, and `vehicle_commands == "stub_only"`. The UI labels the only module as a development stub. Android sends canonical requests over RFCOMM but contains no vehicle-specific CAN mapping/transmission implementation. Therefore there is no source evidence that an actuator-capable peer is safely supported, and truthful actuator-capable capabilities are rejected.
- **Concrete failure scenario:** A real BabyNodeCAN firmware peer reports that it can transmit vehicle commands or reports a non-stub capability. The app rejects it and remains unable to operate the vehicle. Weakening validation without a reviewed cross-repository contract would create the opposite and unsafe failure mode.
- **Smallest safe remediation:** Keep the current gate closed and keep the development/stub label prominent. Before any actuation release, define and jointly review a versioned Android/peer contract, supported module/capability matrix, authentication/session rules, and explicitly approved actuator operations; then implement and test only those capabilities.
- **Exact regression/integration test:** Cross-repository contract tests for every supported module/capability tuple, proving unsupported and actuator-capable tuples remain rejected until approved, and proving the UI cannot imply physical actuation while the stub-only contract is active.

### Finding 5 — Residual maintainability and integration-coverage findings

- **Severity:** LOW for architecture concentration; MEDIUM for missing safety-path integration evidence.
- **Files/locations:** `app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt`; `app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt`; `app/src/main/java/com/babynode/automotive/ui/CommandTestSection.kt`; relevant files under `app/src/test/` and `app/src/androidTest/`.
- **Why it matters:** The transport still combines socket lifecycle, writes, parsing, pending requests, and terminal outcomes, and voice/typed sections duplicate some request-state logic. More importantly, existing tests do not prove the full release user path for persisted outcomes or device approval. These are concrete regression opportunities, but class size alone is not a production defect and does not justify broad refactoring.
- **Concrete failure scenario:** A transport/lifecycle change passes helper or transport tests while the release Activity loses a terminal result after recreation, or a UI confirmation path behaves differently from the dispatcher-only test.
- **Smallest safe remediation:** No broad refactor is required before merge for class size. Add the narrowly targeted integration cases in Section 10. Extract a shared request-state or pending-request component only when changing that behavior, preserving the current conformance suite.
- **Exact regression/integration test:** Add the Activity recreation/persistence test from Finding 1; add a voice UI happy path that confirms a sensitive command and proves exactly one write; add an injected Bluetooth adapter/connection-factory test for first approval, pinned identity, disconnect, and reconnect.

## 9. Newly discovered findings

Finding 3 in Section 8 is newly called out in this review: the dedicated RFCOMM installation method is now release-guarded, but the mutable Activity transport factory used by tests remains in main source without an equivalent runtime/variant boundary. No current production call site was found. This is low severity and should not be represented as a remotely exploitable issue.

No other newly discovered high-confidence safety or correctness defect was confirmed in the inspected command parser, allowlist, sensitive confirmation, transport generation cleanup, external-intent path, Bluetooth approval path, manifest, or debug/release UI defaults.

## 10. Remaining test-coverage gaps

1. **Outcome lifecycle and persistence:** `TerminalOutcomePersistenceTest` proves event serialization and one-time consumption; `MainActivityLifecycleTest` supplies synthetic close events. Neither keeps an actual fake-RFCOMM request through Activity recreation nor proves a restored result is visible in release UI. There is no test for a response received immediately before destruction or a failed outcome commit.
2. **Session replay across reconnect:** Current local generation/matcher tests do not exercise peer replay against a new connection. Add the session-A/session-B test from Finding 2.
3. **Successful sensitive-command UI:** `VoiceInputSectionTest` covers recognition review/cancellation and cancellation at sensitive confirmation; current inspected instrumentation does not prove the affirmative second-confirmation UI path causes exactly one write.
4. **Bluetooth adapter and approval boundary:** The RFCOMM stream installer exercises the real transport state machine after connection installation, but bypasses Android adapter discovery and bonded-device approval. Add a fake adapter/factory test for zero/multiple candidates, explicit approval, pinned address, changed identity, and reconnect.
5. **Malformed protocol integration:** Pure protocol tests cover strict validators and the conformance suite covers invalid status and mismatched commands. The current connected conformance report does not demonstrate transport-level malformed JSON, wrong/missing version, wrong ID type, invalid handshake capabilities, or malformed reason behavior against a pending request. Invalid packets appear to fail closed by being ignored and eventually timing out, but exact terminal behavior should be asserted.
6. **Release surface and release hook:** The Compose visibility tests explicitly set `debugToolsVisible` rather than launching a release build. The RFCOMM test installer is guarded in current source, but no current-revision release test/inspection artifact proves it. The Activity transport-factory seam also needs a release-variant assertion.
7. **Retry synchronization:** `MainActivityRetryTest` polls with `SystemClock.sleep`; this is bounded but timing-based. Prefer a signal/controlled clock when revising the test. The stale receive-loop regression itself no longer relies on the old fixed-delay completion.
8. **Full branch validation:** No fresh unit, connected instrumentation, debug/release assembly, or lint task was run against HEAD during this review. Run these after separately allowing their generated-output writes, and record the exact source revision and task outputs.

## 11. Production-readiness assessment

### A. BabyNodeAutomotive itself

The branch has meaningful fail-closed controls and a much stronger testable transport than earlier reviews. Its canonical-command boundary, sensitive confirmation, malformed response validation, terminal transport semantics, device approval, and release/debug separation are substantially improved. It is nevertheless **not ready to merge as a dependable remote-control application** while terminal results can disappear from the user-visible release surface after Activity destruction and peer responses are not session-bound. These issues matter even if current peers are intended to be stubs because the UI must not invite an unsafe duplicate submission and the protocol must not claim an old response belongs to a new request.

### B. Current development/stub workflow

The app is scoped to an approved, bonded `BabyNodeCAN` peer implementing the `single`/non-transmitting/`stub_only` contract. This is a development and protocol-conformance workflow, not a verified actuator workflow. Existing artifacts provide evidence for a 120-test JVM run and an 11-test connected RFCOMM conformance run, but not a fresh, complete validation of HEAD. Lifecycle restoration and adapter/approval integration still need direct tests.

### C. Physical vehicle actuation

**Not ready. Do not claim physical vehicle-control readiness.** The Android/peer contract explicitly forbids transmission, there is no validated actuator-capable peer contract in this repository, and Android remains CAN-agnostic. A truthful actuator-capable peer is rejected. No change to the current fail-closed capability check should be made without joint peer-contract and safety validation.

### Safety checks independently re-evaluated

- **Command phrase parsing and unmatched quotes:** Exact phrase table; explicit question/quote syntax rejected before punctuation normalization; current source test covers unmatched opening/closing single quotes and curly quotes. The fix is present; test artifact freshness is qualified above.
- **Canonical command allowlisting:** Shared Phase 1 allowlist is checked by the dispatcher; both voice and debug typed paths route through it.
- **Sensitive confirmation and replay prevention:** `UNLOCK_DOORS` and `TRUNK_OPEN` require the exact pending confirmation object; it is consumed before sending; stale confirmation objects and cancellation do not transmit.
- **Request/response correlation:** Local matching uses session generation, request ID, and canonical command; terminal outcomes outrank send events. Wire session identity remains unbound (Finding 2).
- **Terminal outcomes and timeout/cancel/disconnect/close:** Potentially transmitted requests use unknown-outcome language; close returns request-correlated unknown outcomes. Persistence/rendering is incomplete (Finding 1).
- **Activity destruction/recreation and transport replacement:** Replacement clears old status and retains returned terminal close events; Activity recreation consumes persisted close errors. Previously received outcomes are not preserved and restored outcomes are not shown in release (Finding 1).
- **RFCOMM lifecycle/socket generations:** Current receive-loop and cleanup operations are generation-guarded; deterministic stale-read completion exists in current test source and is represented in the connected conformance artifact.
- **Retries:** Bounded attempts, manual retry, and generation guards are present; current retry instrumentation uses polling rather than a controlled clock.
- **Malformed protocol and handshake/capability validation:** Version, response type/status/command, integer request ID, reason length/content, duplicate top-level keys, bounded frames, and exact stub capability values are validated. The negotiated handshake `session_id` is not bound to later responses.
- **Release/debug behavior and test hooks:** Release UI defaults to hiding debug tester/history. `installConnectionForTest()` checks `BuildConfig.DEBUG`; the mutable Activity factory remains a low-risk release-compiled test seam (Finding 3).
- **External intents:** Manifest has a launcher filter only; production handling rejects known command-style input and does not dispatch external data.
- **Bluetooth identity:** First-use unique bonded candidate requires explicit approval and is then pinned by address; forget failure is surfaced. Secure RFCOMM uses the bonded-device path.
- **Manifest permissions:** Current main manifest declares Bluetooth permissions/features only; it does not declare `RECORD_AUDIO` or Internet access. The package permission test asserts no audio recording permission.
- **Android/CAN architecture:** Android sends canonical protocol commands; no vehicle-specific CAN IDs, payload encoding, or CAN transmission implementation was found.

## 12. Exact remaining work, ordered into small logical steps

1. **Make terminal outcomes durable and visible.** Persist received terminal responses/errors and possibly-transmitted unresolved requests; restore them to a release-visible request outcome surface; surface persistence failures; never automatically replay. Add the three lifecycle/persistence scenarios in Finding 1.
2. **Bind peer responses to session identity.** Define the versioned cross-peer session correlation contract, serialize/validate it on every command and response, and add old-session replay regression coverage. Keep the current peer capability gate closed meanwhile.
3. **Close release test-factory seam.** Move `MainActivity.transportFactory` to a debug/test source set or reject release mutation; assert release variant behavior while retaining the debug test seam.
4. **Complete narrow integration tests.** Add positive sensitive-confirmation UI, injected adapter/device approval, malformed-protocol transport, and release-surface/hook assertions. Keep the deterministic stale-reader test.
5. **Validate the exact candidate revision.** After generated-output changes are authorized, run JVM tests, complete connected Android instrumentation, debug/release assembly, and debug/release lint. Record the candidate SHA and exact outcomes; do not substitute prior generated artifacts.
6. **Define any future actuation scope separately.** Only after Android and BabyNodeCAN jointly specify and review the supported modules, peer authentication/session behavior, and actuation capability matrix should any actuator-capable peer be considered.

## 13. Final verdict

NOT READY TO MERGE
