# BabyNodeAutomotive Fix Operation: Change Summary

**Operation date:** 2026-09-30  
**Scope:** Lifecycle, TCP reliability, connection state, coroutine ownership, UI compatibility, and code quality. CAN frame definitions and intent parsing were intentionally left unchanged.

## Summary

The fix operation replaced ad hoc activity-owned coroutine work and transient UI status handling with lifecycle-owned transport coordination and explicit connection/event state. TCP now reports send success only after a checked write, parses incoming frames defensively, and owns a cancellable receive job. The screen consumes retained state, respects system insets, and scrolls on smaller displays. Obsolete USB-serial build configuration was removed, and focused tests were added for connection state and event retention.

## Changes by Subsystem

### Activity Lifecycle and Transport Selection

**Affected file:** `app/src/main/java/com/babynode/automotive/MainActivity.kt`

- Replaced the manually created `MainScope` with the activity’s `lifecycleScope`, so transport and dispatcher work is cancelled with the activity.
- Removed the initial mock connection followed by a Compose-effect transport replacement. The initial mock is now created once and observed before it connects.
- Added a `Mutex`-serialized transport switch. Switching disconnects and closes the previous transport, stops its event collector, installs and observes the replacement, then connects it.
- Added synchronous transport cleanup during `onDestroy` so TCP sockets are closed as the activity is torn down.
- Moved the selected transport and current dispatcher into Compose state, updating them from the serialized switch operation.
- Applied the `Scaffold` content padding to the main screen.

**Reasoning:** The prior switching coroutine could read a reassigned transport property and disconnect the replacement instead of the old transport. It also allowed overlapping connect/disconnect work and used a scope that was not tied to the activity lifecycle.

### Connection State and Event History

**Affected files:**
- `app/src/main/java/com/babynode/automotive/CarStatusBus.kt`
- `app/src/main/java/com/babynode/automotive/MainActivity.kt`
- `app/src/main/java/com/babynode/automotive/ui/AutomotiveScreen.kt`
- `app/src/main/java/com/babynode/automotive/ui/CanDebugSection.kt`

- Replaced the status bus’s unused internal event stream/merge API with `StateFlow` values for connection state, latest event, and a bounded 200-event history.
- Added explicit `Disconnected`, `Connecting`, `Connected`, and `Failed` states. Transport events update the current state and retained event history.
- Started transport event collection before connecting, avoiding loss of initial connection events.
- Updated the UI to display connection state and render the retained event history rather than collecting only the latest transient event for debug logging.

**Reasoning:** A hot flow with no replay could drop events before collection, and the Compose UI reduced the stream to one value, allowing bursts to disappear from the debug log. State flows retain the current state and the event history remains available across recompositions.

### TCP Reliability

**Affected files:**
- `app/src/main/java/com/babynode/automotive/CarCanBusTcp.kt`
- `app/src/main/java/com/babynode/automotive/CarCanTransport.kt`
- `app/src/main/AndroidManifest.xml`

- Added `INTERNET` permission for Android socket access.
- Injected the lifecycle-owned coroutine scope into the TCP transport and replaced its unmanaged raw thread with a cancellable receive job.
- Serialized connect, disconnect, and send operations with a mutex; retained the three-second connect timeout.
- Added synchronous `close()` to the transport contract for owner teardown and implemented it for TCP and mock transports.
- Checked `PrintWriter` output errors before emitting `FrameSent`. Sending while disconnected emits an error; write failures close the connection and emit error/disconnection events.
- Added tolerant parsing for whitespace-separated receive lines and validation for frame ID range, DLC/payload length, DLC bounds, and unsigned payload bytes. Invalid lines emit an error and do not terminate the receive loop.
- Configured the transport event buffer to drop the oldest item rather than suspend indefinitely under pressure.

**Reasoning:** Previously a missing writer or failed `PrintWriter` write could still produce `FrameSent`, and malformed receive data could terminate the raw reader thread. The new behavior makes transport outcomes observable and ties receive processing to an owner-controlled lifecycle.

### Mock Coroutine Usage

**Affected file:** `app/src/main/java/com/babynode/automotive/CarCanBusMock.kt`

- Removed the injected coroutine scope and nested `launch` calls. Mock connect, disconnect, send, and acknowledgement events are emitted directly from their suspend functions.
- Added the transport `close()` implementation.

**Reasoning:** The mock previously returned before its events were emitted, which weakened ordering and made it less representative of the transport contract.

### UI Compatibility and Layout

**Affected files:**
- `app/src/main/java/com/babynode/automotive/ui/AutomotiveScreen.kt`
- `app/src/main/java/com/babynode/automotive/ui/CanDebugSection.kt`

- Made the main content vertically scrollable and applied the insets supplied by `Scaffold`.
- Replaced the debug section’s local mutable log and `removeFirst()` call with the event history supplied by the status bus.

**Reasoning:** The screen’s stacked sections could be clipped on smaller devices, and the previous log eviction call triggered an API-level lint error for the project’s minimum SDK of 26. The bounded history now resides in the status layer and is rendered without an API-35-only operation.

### Build Configuration Cleanup

**Affected files:**
- `app/build.gradle.kts`
- `settings.gradle.kts`

- Removed the unused USB-serial dependency and its JitPack repository entries.

**Reasoning:** The active transport implementation is TCP, and the source transport contract states USB support has been removed. Removing unused dependency/repository configuration reduces unnecessary build and dependency surface.

### Tests

**Added file:** `app/src/test/java/com/babynode/automotive/CarStatusBusTest.kt`

- Tests connection transitions, preservation of connection-failure details, and retention of only the most recent 200 events.

## Intentionally Unchanged

- `CarCommandMap` and `CarCommandDetector` were not modified.
- `CarCanMap` CAN frame IDs and payload definitions were not modified.
- No vehicle-specific behavior was added or changed.
- The earlier `BabyNodeAutomotive-Analysis.md` report was left unchanged.

## Verification

- `./gradlew testDebugUnitTest`: passed, including the added status-bus tests.
- `./gradlew lintDebug`: passed.
- Editor diagnostics: no errors reported in changed Kotlin files.
- `git diff --check`: passed.
- No DuoCAN hardware or live TCP endpoint was used for runtime verification.
