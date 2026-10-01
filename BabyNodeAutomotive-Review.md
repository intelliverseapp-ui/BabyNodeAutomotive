# BabyNodeAutomotive Codebase Review

**Review date:** 2026-10-01  
**Recommendation:** **NOT READY** for DuoCAN-C6 integration or live-vehicle CAN transmission.

## Scope and Verification

Reviewed the Android app's Kotlin source, command and transport paths, Compose UI, manifest, Gradle configuration, tests, project metadata, XML resources, and checked-in binary resource/tool artifacts. Generated `build/` outputs were not treated as source. The worktree already contained modifications to `CarCanBusTcp.kt`, `CarCommandDispatcher.kt`, and `ui/CommandTestSection.kt`, plus deletions of two prior Markdown reports; this review reflects the present files and does not restore or alter those changes. The two absent reports could not be reviewed.

Verification performed:

- `./gradlew testDebugUnitTest`: failed; 3 of 4 tests failed.
- `./gradlew assembleDebug`: succeeded; Gradle reported all tasks up to date.
- Android instrumentation tests and physical DuoCAN-C6 interoperability were not run.

The host test failures are reproducible: the `CarStatusBusTest` cases hit `RuntimeException: Method i in android.util.Log not mocked` from `CarStatusBus`/`CarStatusEvent` logging. Thus the existing unit tests do not currently pass on the configured local JVM test runner.

## Architecture Summary

This is a single-Activity Android app using Jetpack Compose. `MainActivity` owns a mock or TCP `CarCanTransport`, collects transport events into `CarStatusBus`, and supplies a `CarCommandDispatcher` to the UI. Spoken input is keyword-filtered, converted from natural language to a canonical command, mapped to a fixed CAN frame, and sent asynchronously. The command tester also accepts raw `SEND` commands. The TCP implementation targets `192.168.4.1:1234`; the mock emits synthetic events. Incoming frames are displayed in a bounded debug history, but no vehicle signal decoding or Rogue CAN mapping layer is present.

## Subsystem Evaluation

### Command Parsing and CAN Mapping

Natural-language recognition and canonical mapping are separate substring-based tables in [CarCommandDetector.kt](app/src/main/java/com/babynode/automotive/CarCommandDetector.kt) and [CarCommandMap.kt](app/src/main/java/com/babynode/automotive/CarCommandMap.kt). There is no token-boundary, intent-confidence, or confirmation gate. For example, “set clock” contains `lock` and can map to `LOCK_DOORS`; “back on” contains `ac` and can map to `AC_ON`. These false positives can cause an unintended actuation when voice recognition is used. Cruise commands exist in the mapper but cruise is absent from the detector vocabulary, so those commands are not routed through voice input.

The static IDs and payloads in [CarCanMap.kt](app/src/main/java/com/babynode/automotive/CarCanMap.kt) are not associated with a vehicle make/model, bus profile, firmware contract, or validated Rogue CAN database. The mapping includes safety-relevant functions such as locks, windows, lights, body controls, and cruise control. No evidence in the repository establishes that these identifiers or payloads are correct for the intended vehicle or safe to transmit. Dynamic cruise speed is converted directly to one byte without an enforced range, so out-of-range values can wrap.

The raw path in [CarCommandDispatcher.kt](app/src/main/java/com/babynode/automotive/CarCommandDispatcher.kt) allows direct frame transmission from a UI text field. It accepts IDs without outbound CAN identifier range validation and permits payload lengths through 64 bytes. There is no command allowlist or vehicle-state interlock in this path. A zero-length `SEND id 0` frame is rejected by the parser's minimum token count even though zero-length frames are otherwise accepted by the length check.

### TCP Transport and Protocol Handling

[CarCanBusTcp.kt](app/src/main/java/com/babynode/automotive/CarCanBusTcp.kt) implements a newline-delimited UTF-8 text protocol: outbound `SEND` lines and inbound `CAN_RX` lines. It connects to a hard-coded endpoint with a 3-second connect timeout and up to five attempts. The repository contains no DuoCAN protocol specification, protocol-version negotiation, device identity check, or captured-hardware test proving that the assumed grammar, decimal fields, newline behavior, port, and endpoint match the DuoCAN-C6 firmware.

The receive loop uses `BufferedReader.readLine()` with no line-size limit. A peer that can reach the socket can supply an arbitrarily long line or a high rate of lines, creating memory, CPU, and log-volume pressure. The socket path has no app-layer authentication or encryption, no heartbeat/read timeout, and no reconnect loop after a connection drops. Whether the device's Wi-Fi setup supplies additional isolation is not established by this code.

Outbound writes are serialized and checked with `PrintWriter.checkError()`, but `FrameSent` means the text was written locally without a reported stream error; it is not confirmation that DuoCAN parsed the command or that a CAN controller/vehicle accepted it. The app uses no transaction ID or application-level acknowledgement. Inbound parsing checks identifier range and payload length, but it accepts lengths up to 64 without negotiating classic CAN versus CAN-FD or verifying the connected device's mode. Outbound frames do not receive equivalent identifier/length validation at the transport boundary.

### Concurrency and Lifecycle

The TCP class uses a mutex around connect, disconnect, and writes, and the activity serializes transport switches. This reduces simultaneous stream writes and close/send overlap. However, the connect retry loop calls `Thread.sleep()` while holding the connection mutex; a failed connect can occupy the transport switch path for multiple seconds, and blocking socket calls are not promptly cancellable. The synchronous `close()` path does not take that mutex, so activity teardown can race an in-progress connect and miss a socket that is assigned after `close()` snapshots the current socket. Commands are launched asynchronously and are not represented by a caller-visible completion result.

The transport event flow uses a bounded buffer with drop-oldest behavior. The UI history is also bounded to 200 events, which limits retained event-history growth. By contrast, an individual inbound TCP line is unbounded. The app's transport ownership is Activity-scoped; no background service or reconnection owner exists if the Activity is destroyed or the app is backgrounded.

### UI and State Reporting

The app starts on the Mock transport ([MainActivity.kt](app/src/main/java/com/babynode/automotive/MainActivity.kt)); that mock emits a fabricated ACK ([CarCanBusMock.kt](app/src/main/java/com/babynode/automotive/CarCanBusMock.kt)). This is useful for demonstrating UI flow but is not evidence of hardware transmission or acknowledgement.

Both the voice and manual command UI set their local result to “Executed” immediately after calling the dispatcher ([VoiceInputSection.kt](app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt), [CommandTestSection.kt](app/src/main/java/com/babynode/automotive/ui/CommandTestSection.kt)). Dispatch is asynchronous, so this status can appear before connection checks or write failures. Invalid or unknown commands are mainly logged rather than returned as structured UI results. `CarStatusBus` changes to `Failed` only when an error arrives while connecting; other errors can leave its connection state marked connected until a later disconnect event.

Incoming CAN frames are shown as raw ID/data debug entries; they are not decoded into signal values or mapped Rogue CAN data. No vehicle-specific controls, response verification, safety status, or stale-data indication are represented in the UI.

### Android Configuration, Dependencies, and Tests

The app targets API 37 with minimum API 26, requests Internet and microphone permissions, and exposes only its launcher Activity. The main build configuration is conventional for a small Compose application, and the debug APK task completes in the checked workspace. Release minification is disabled. The manifest enables backup, but the app currently defines no persistent application data requiring backup.

Automated coverage is very limited: the substantive unit tests cover `CarStatusBus` transitions/history, and the remaining tests are template smoke tests. There are no tests for TCP connection lifecycle, text framing, malformed or oversized input, raw `SEND` validation, command parsing, CAN mappings, transport switching, or UI reporting. The current JVM tests are blocked by Android logging calls executed from host-side model/state code, as confirmed by the test XML stack traces.

## Identified Risks

| Severity | Risk | Evidence |
|---|---|---|
| **Critical** | Fixed control IDs/payloads are not validated for a target vehicle; the app can transmit commands for safety-relevant vehicle functions. | [CarCanMap.kt](app/src/main/java/com/babynode/automotive/CarCanMap.kt#L24) |
| **High** | Broad substring matching can route ordinary phrases such as “set clock” or “back on” to door-lock or A/C commands. | [CarCommandMap.kt](app/src/main/java/com/babynode/automotive/CarCommandMap.kt#L31), [CarCommandDetector.kt](app/src/main/java/com/babynode/automotive/CarCommandDetector.kt#L29) |
| **High** | Raw `SEND` can bypass natural-language mapping and safety checks; outbound identifier and frame-length constraints are insufficiently enforced. | [CarCommandDispatcher.kt](app/src/main/java/com/babynode/automotive/CarCommandDispatcher.kt#L32), [CarCanBusTcp.kt](app/src/main/java/com/babynode/automotive/CarCanBusTcp.kt#L165) |
| **High** | DuoCAN TCP wire compatibility and device identity are unverified; local write success is not device or CAN acknowledgement. | [CarCanBusTcp.kt](app/src/main/java/com/babynode/automotive/CarCanBusTcp.kt#L26), [CarCanBusTcp.kt](app/src/main/java/com/babynode/automotive/CarCanBusTcp.kt#L191) |
| **High** | Unbounded line reads and absent heartbeat/reconnect behavior create memory/availability and stale-connection risks. | [CarCanBusTcp.kt](app/src/main/java/com/babynode/automotive/CarCanBusTcp.kt#L251) |
| **Medium** | UI success text is optimistic and may be displayed before the send completes or fails. | [CommandTestSection.kt](app/src/main/java/com/babynode/automotive/ui/CommandTestSection.kt#L52), [VoiceInputSection.kt](app/src/main/java/com/babynode/automotive/ui/VoiceInputSection.kt#L48) |
| **Medium** | Host unit tests fail because Android `Log` methods are invoked by JVM-executed state/event code. | [CarStatusBus.kt](app/src/main/java/com/babynode/automotive/CarStatusBus.kt#L34), [CarCanTransport.kt](app/src/main/java/com/babynode/automotive/CarCanTransport.kt#L64) |
| **Medium** | Transport close can race a blocking connect during Activity teardown; the retry delay blocks while holding the connection mutex. | [CarCanBusTcp.kt](app/src/main/java/com/babynode/automotive/CarCanBusTcp.kt#L56), [CarCanBusTcp.kt](app/src/main/java/com/babynode/automotive/CarCanBusTcp.kt#L225), [MainActivity.kt](app/src/main/java/com/babynode/automotive/MainActivity.kt#L90) |
| **Low/Medium** | Voice cruise commands are mapped but not admitted by the voice detector; zero-DLC raw frames are rejected by the parser token-count check. | [CarCommandDetector.kt](app/src/main/java/com/babynode/automotive/CarCommandDetector.kt#L104), [CarCommandDispatcher.kt](app/src/main/java/com/babynode/automotive/CarCommandDispatcher.kt#L36) |

## Stability Assessment

The project is structurally buildable in the current environment, but the passing build task was up to date rather than a demonstrated clean build, and the host unit-test task fails. More importantly, build success does not establish safe CAN behavior. Protocol framing, device identity, acknowledgement semantics, frame-mode limits, and vehicle-specific command mappings have not been verified against DuoCAN-C6 hardware or firmware. The repository contains no Rogue CAN decoding or mapped-signal implementation to assess for later telemetry use.

**Final recommendation: NOT READY for integration with DuoCAN-C6 for vehicle-control use, and NOT READY for mapped Rogue CAN data.** The current evidence supports only a development UI/mock demonstration and an unverified TCP prototype. No live-vehicle command transmission should be treated as validated by this review.