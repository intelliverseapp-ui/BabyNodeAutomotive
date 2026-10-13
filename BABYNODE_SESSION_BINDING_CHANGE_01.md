# BabyNode Session Binding Change 01

Android side of Review 04 Finding 2, targeting the session-bound protocol in SingleCAN-WROOM32 commit 29ac9da. No builds or tests were run. SingleCAN-WROOM32 was not modified.

## 1. Files changed

- `app/src/main/java/com/babynode/automotive/CarCanBusBluetooth.kt` (modified)
- `app/src/main/java/com/babynode/automotive/BabyNodeProtocol.kt` (modified)
- `app/src/androidTest/java/com/babynode/automotive/CarCanBusBluetoothConformanceTest.kt` (modified)
- `app/src/androidTest/java/com/babynode/automotive/CarCanBusBluetoothSessionBindingTest.kt` (new)
- `BABYNODE_SESSION_BINDING_CHANGE_01.md` (this report)

`CarCommandDispatcher` is untouched and remains unaware of wire session IDs.

## 2. Functions, properties and helpers

`BabyNodeProtocol.kt`
- Added `parseSessionId(Any?): Long?`. It returns the value only for Byte/Short/Int/Long in `1..MAX_SESSION_ID`. Strings, doubles and every other type return null.

`CarCanBusBluetooth.kt`
- Added the `@Volatile private var negotiatedPeerSessionId: Long?` property.
- `sendCommand`: packet construction moved inside the `writeMutex` block, after the connection check. It adds the handshake/session logic described below.
- `processIncomingPacket`: added the ordinary-response session check and the handshake session retention.
- `installConnectionForTest`: new optional parameter `peerSessionIdForTest: Long? = null`. It is debug-only, already guarded by `BuildConfig.DEBUG`, and validated with `parseSessionId`.
- `connect`, `installConnectionForTest`, `disconnect`, `close` and `closeSocketResourcesImmediately` now clear the session.

## 3. Where the session ID is stored

In `CarCanBusBluetooth.negotiatedPeerSessionId`. It is separate from the local `sessionGeneration`.

## 4. How the handshake establishes it

`sendCommand` sends `protocol.handshake` without `session_id`. It also clears any previously negotiated ID at that moment, so a failed re-handshake leaves no session. In `processIncomingPacket` a response counts as a handshake bootstrap response only when both the pending command and the response command are `protocol.handshake`. The ID is stored only after the existing checks pass, in this order:
- the request ID matches a pending request;
- the command matches;
- the status is valid (`ok`);
- the version is valid;
- `validateHandshakeCapabilities` passes, which includes a valid `session_id`.

The pending entry is then removed, and only if the removal succeeds is `negotiatedPeerSessionId` set to `parseSessionId(session_id)`. The connection must also still be installed and not disconnected or closed.

## 5. Why rejected handshakes cannot establish it

The store sits after every early `return`. Mismatched, invalid-capability, invalid-session, non-`ok`, duplicate or late responses return earlier, or fail the `removed` check, or fail the `status == "ok"` condition. Nothing is retained on those paths.

## 6. Post-handshake packets

For every non-handshake command, `sendCommand` reads `negotiatedPeerSessionId` under `writeMutex` and writes `"session_id": <id>` into the packet. This covers `config.module` and user commands.

## 7. Sends without a session

A non-handshake `sendCommand` with a null `negotiatedPeerSessionId` throws `IOException("BabyNodeCAN peer session is not negotiated")` inside the existing write path. This happens before the pending command is registered and before any bytes are written. The existing error handling emits the request error. The packet cannot be built without the session.

## 8. Ordinary response validation

For every response that is not a handshake bootstrap response, the code runs `parseSessionId` on `session_id`. It requires the result to be non-null and equal to the current `negotiatedPeerSessionId`. This runs after the duplicate-key check, which already rejects duplicate `session_id` keys, and after the pending lookup. It runs before command-mismatch handling and before pending removal. A non-handshake response to a pending handshake is also treated as ordinary and needs a matching session, which is null at that point, so it is ignored.

## 9. Missing, malformed, zero, out-of-range and mismatched IDs

The packet is logged and ignored with `return`. The pending command is not removed, `responseReceived` is not completed, no `CommandResponse` or error is emitted, nothing is marked ready, and no request is resolved. The legitimate response can still resolve the request, otherwise the existing timeout applies.

## 10. Old-session responses

When a new connection is installed, the old session is cleared, and the new handshake sets a new ID. An old-session response with the same request ID and command carries the old ID, which does not equal the current one, so it is ignored. If no new session exists, any response is ignored because the current ID is null.

## 11. Lifecycle paths that clear the session

- `installConnectionForTest` start, and the install block (set to `peerSessionIdForTest`, null by default).
- `connect()` install block (cleared on successful install, before the new handshake).
- `connect()` failure, through `closeSocketResources`.
- `disconnect()`.
- `close()`.
- `handleConnectionLoss`, through `closeSocketResourcesImmediately`.
- `closeSocketResourcesImmediately`, which covers every close, invalidate and replace path that goes through it.
- A new `protocol.handshake` send.

## 12. Local `sessionGeneration`

It is unchanged. It is still allocated once per transport instance and is used only in request and event correlation.

## 13. Tests

New in `CarCanBusBluetoothSessionBindingTest.kt`:
- handshake sent without `session_id`;
- successful handshake retains the session, and `config.module` and user commands carry it;
- send rejected with no session;
- matching response resolves exactly once, with a duplicate ignored;
- missing, string-malformed, fractional, zero, out-of-range and mismatched response session IDs are ignored, and a later valid response resolves exactly once;
- an old-session response with a reused ID and command cannot resolve the new-session request;
- disconnect clears the session;
- connection replacement clears the session;
- an error-status handshake, a zero-session handshake and an invalid-capability handshake each leave no session.

Updated in `CarCanBusBluetoothConformanceTest.kt`:
- the fake peers (`FlushTriggeredResponseConnection`, `ReverseOrderResponseConnection`) now include `session_id` (`TEST_PEER_SESSION_ID`) in responses;
- all existing `installConnectionForTest` calls pass `peerSessionIdForTest = TEST_PEER_SESSION_ID`.

## 14. Preserved behavior

Request ID and command matching, pending-request handling, timeout and outcome-unknown paths, allowlisting and confirmation (in the dispatcher), handshake capability validation, module-configuration gating, socket-generation guards, duplicate-response handling and the release/debug guard on the test seam are all unchanged.

## 15. Limitations

- Existing conformance tests use the debug-only `peerSessionIdForTest` seam parameter instead of a full handshake. The new tests do a real handshake.
- A handshake response is not bound to a pre-existing session, since it bootstraps one.
- Stale-connection protection for the retention step relies on the existing receive-loop generation checks plus a connected-socket and not-disconnected check, not on an explicit generation argument.
- No new `BabyNodeProtocolTest` case for `parseSessionId`. It is covered only through the instrumented tests.
- Not compiled or run.

## 16. Diff summary

- `BabyNodeProtocol.kt`: +32, the `parseSessionId` helper.
- `CarCanBusBluetooth.kt`: the session field, the clears, the packet construction moved under the lock with the session check, the response validation, and the handshake retention.
- `CarCanBusBluetoothConformanceTest.kt`: `session_id` in fake responses and a session ID on each install.
- `CarCanBusBluetoothSessionBindingTest.kt`: new file with the focused tests.
