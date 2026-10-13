# BabyNode Malformed Protocol Test 01

Integration test addition for Review 04 Section 10 item 5: verifying that malformed or invalid peer packets fail closed without incorrectly resolving pending requests.

No production code was modified, no unrelated tests were modified, and no builds or tests were run.

---

## 1. Tests Added

All tests were added to `app/src/androidTest/java/com/babynode/automotive/CarCanBusBluetoothConformanceTest.kt`:

1. `malformedJsonReportsGeneralErrorAndPendingRequestIsResolvedByLaterValidResponse()`
2. `missingProtocolVersionIsIgnoredAndPendingRequestIsResolvedByLaterValidResponse()`
3. `wrongProtocolVersionIsIgnoredAndPendingRequestIsResolvedByLaterValidResponse()`
4. `nonnumericRequestIdIsIgnoredAndPendingRequestIsResolvedByLaterValidResponse()`
5. `malformedReasonIsIgnoredAndPendingRequestIsResolvedByLaterValidResponse()`
6. `invalidHandshakeCapabilitiesFailsClosedAndDoesNotInstallSession()`

---

## 2. Existing Test Infrastructure Reused

- **`CarCanBusBluetooth`**: Instantiated with test seams (`installConnectionForTest`, `peerSessionIdForTest`).
- **`CarRequestEventMatcher`**: Reused for validating that responses and errors correlate to the specific pending request.
- **`TEST_PEER_SESSION_ID`**: Reused the standard test peer session ID (`4_242L`) for packet creation and session binding verification.
- **`ScriptedSequenceResponseConnection`**: Added as an internal helper connection extending the established `RfcommConnection` pattern with piped input/output streams, allowing a scripted list of raw response strings (valid and invalid) to be emitted sequentially into the transport's receive stream upon socket flush.

---

## 3. Exact Behavior Observed for Malformed JSON

When the peer sends non-JSON text (e.g., `"{ not valid json \n"`):
1. `JSONObject(packet)` throws a `JSONException` caught by the outer catch block in `CarCanBusBluetooth.processIncomingPacket()`.
2. `processIncomingPacket()` emits a general (non-request-specific) `CarStatusEvent.Error`:
   - `message` starts with `"Invalid response from BabyNodeCAN:"`.
   - `requestId`, `sessionGeneration`, and `command` are `null` (`isRequestSpecific == false`).
3. No `CarStatusEvent.CommandResponse` is emitted.
4. The pending request in `pendingCommands` is **not** removed or resolved.
5. When a subsequent valid response for that request arrives, it is processed normally, matches the pending command, and emits `CarStatusEvent.CommandResponse(status = "ok")` exactly once.

---

## 4. Exact Behavior Observed for Missing / Wrong Version

### Missing Version
- When the response JSON omits `"version"`:
  - `BabyNodeProtocol.validateVersion(null)` evaluates to `false`.
  - `processIncomingPacket()` logs a warning and returns early.
  - The packet is silently ignored; no `CommandResponse` or request-specific `Error` is emitted.
  - The pending command remains in `pendingCommands`.
  - A subsequent valid response resolves the pending request exactly once.

### Wrong Version
- When the response JSON contains `"version": BabyNodeProtocol.VERSION + 1`:
  - `BabyNodeProtocol.validateVersion(...)` evaluates to `false`.
  - `processIncomingPacket()` logs a warning and returns early.
  - The packet is silently ignored; no `CommandResponse` or request-specific `Error` is emitted.
  - The pending command remains in `pendingCommands`.
  - A subsequent valid response resolves the pending request exactly once.

---

## 5. Exact Behavior Observed for Invalid ID Type

When the response JSON contains `"id": "one"`:
1. `BabyNodeProtocol.validateRequestId(idValue)` returns `false` (only `Byte`, `Short`, `Int`, and `Long` within `1L..2_147_483_647L` are valid).
2. `processIncomingPacket()` logs `"Response contains invalid id"` and returns early.
3. No `CommandResponse` or request-specific `Error` is emitted.
4. The pending request remains tracked in `pendingCommands`.
5. A subsequent valid response containing `id = 1L` resolves the pending request exactly once.

---

## 6. Exact Behavior Observed for Malformed Reason

When the response JSON contains a reason string with control characters (e.g., `"control\u0000character"`):
1. `BabyNodeProtocol.validateResponseReason(reasonValue)` returns `false` because characters `< 0x20` or `== 0x7F` are rejected.
2. `processIncomingPacket()` logs `"Response contains invalid reason"` and returns early.
3. No `CommandResponse` or request-specific `Error` is emitted.
4. The pending request remains tracked in `pendingCommands`.
5. A subsequent valid response resolves the pending request exactly once.

---

## 7. Exact Behavior Observed for Invalid Handshake Capabilities

When the peer sends a handshake response with invalid/unsupported capabilities (e.g., `"module": "unsupported_module"`):
1. The packet passes top-level structural validations and is correlated with the pending `protocol.handshake` command.
2. Because `expectedCommand == PROTOCOL_HANDSHAKE_COMMAND && responseStatus == "ok"`, `BabyNodeProtocol.validateHandshakeCapabilities(...)` is invoked.
3. Capability validation fails because the module is not supported under `BabyNodePeerContract`.
4. The pending handshake command is removed from `pendingCommands` and its `responseReceived` deferred signal is completed.
5. `emitRequestError()` is called with:
   `"BabyNodeCAN handshake contains invalid session identity or unsupported capabilities"`.
6. Exactly one correlated `CarStatusEvent.Error` is emitted matching `handshakeRequest`.
7. No `CarStatusEvent.CommandResponse` is emitted.
8. Because validation failed before reaching the handshake success block, `negotiatedPeerSessionId` is **not** set.
9. Any subsequent post-handshake command (such as `LOCK_DOORS`) fails closed with an `IOException("BabyNodeCAN peer session is not negotiated")` before any bytes are transmitted.

---

## 8. How the Tests Prove Invalid Packets Cannot Incorrectly Resolve Pending Requests

For all invalid/malformed non-handshake packets:
- The tests verify that no `CarStatusEvent.CommandResponse` matching the request is emitted.
- The tests verify that no request-specific `CarStatusEvent.Error` matching the request is emitted.
- The tests verify that the pending request remains active until a legitimate response arrives.
- When `transport.close()` is called at the end of the test, it yields no unhandled terminal events for the request because the legitimate response already resolved it exactly once.

---

## 9. How Later Valid Responses Remain Authoritative Where Applicable

In each test for malformed JSON, missing version, wrong version, invalid ID type, and malformed reason:
1. The connection sends the invalid packet first, followed immediately by a valid response packet for the same request ID.
2. The tests wait for the arrival of events using bounded timeouts.
3. The tests verify that `CarRequestEventMatcher.selectBest(...)` selects the `CommandResponse(status = "ok")` from the legitimate response.
4. The tests verify that exactly one `CommandResponse` is emitted across the entire sequence.

---

## 10. Deterministic Waits and Collector Cleanup

### No Arbitrary Post-Result Sleeps
- No arbitrary post-result sleeps or fixed delays (such as `delay(200L)`) are used in any of the malformed-protocol tests.
- Assertions execute immediately once the expected events or terminal conditions are observed.

### Bounded Polling Inside withTimeout
- Asynchronous event delivery is awaited using deterministic bounded polling loops bounded by `withTimeout(5_000L)`.
- Inside the `withTimeout` loop, `delay(10L)` is used strictly as a polling interval while checking whether the expected event conditions are met, eliminating arbitrary post-result delays while ensuring tests do not block indefinitely.

### Collector Cleanup in finally Blocks
Each test launches an event collection coroutine using:
```kotlin
val collector = launch(start = CoroutineStart.UNDISPATCHED) {
    transport.status().collect { event -> observedEvents += event }
}
```
In every test, this coroutine is cleaned up deterministically in a `finally` block:
```kotlin
} finally {
    collector.cancelAndJoin()
    transport.close()
}
```
`collector.cancelAndJoin()` ensures that the coroutine is fully cancelled and awaited before exiting the `runBlocking` scope, preventing background leaks or hangs.

---

## 11. Confirmation That Production Code Was Not Changed

`git diff app/src/main/` returns empty. No production code was modified. Only `CarCanBusBluetoothConformanceTest.kt` was modified.

---

## 12. Remaining Limitations

- Tests run against synthetic piped RFCOMM streams (`ScriptedSequenceResponseConnection`) rather than live hardware Bluetooth sockets.
- Frame boundary framing relies on newline delimiter parsing implemented in `BoundedFrameReader`.

---

## 13. Concise Git Diff Summary

```diff
diff --git a/app/src/androidTest/java/com/babynode/automotive/CarCanBusBluetoothConformanceTest.kt b/app/src/androidTest/java/com/babynode/automotive/CarCanBusBluetoothConformanceTest.kt
index e6029f6..80b0bbd 100644
--- a/app/src/androidTest/java/com/babynode/automotive/CarCanBusBluetoothConformanceTest.kt
+++ b/app/src/androidTest/java/com/babynode/automotive/CarCanBusBluetoothConformanceTest.kt
@@ -2016,6 +2016,913 @@ class CarCanBusBluetoothConformanceTest {
             }
         }
 
+    @Test
+    fun malformedJsonReportsGeneralErrorAndPendingRequestIsResolvedByLaterValidResponse() =
+        runBlocking {
+            val connection =
+                ScriptedSequenceResponseConnection(
+                    listOf(
+                        "{ not valid json \n",
+                        validResponsePacket(
+                            id = 1L,
+                            command = "LOCK_DOORS",
+                            status = "ok",
+                            sessionId = TEST_PEER_SESSION_ID
+                        )
+                    )
+                )
+
+            val transport =
+                CarCanBusBluetooth()
+
+            val observedEvents =
+                Collections.synchronizedList(
+                    mutableListOf<CarStatusEvent>()
+                )
+
+            val collector =
+                launch(
+                    start =
+                        CoroutineStart.UNDISPATCHED
+                ) {
+                    transport
+                        .status()
+                        .collect { event ->
+                            observedEvents +=
+                                event
+                        }
+                }
+
+            try {
+                transport.installConnectionForTest(
+                    connection =
+                        connection,
+                    peerSessionIdForTest =
+                        TEST_PEER_SESSION_ID
+                )
+
+                val request =
+                    transport.sendCommand(
+                        CanonicalCommand(
+                            command =
+                                "LOCK_DOORS"
+                        )
+                    )
+
+                withTimeout(
+                    5_000L
+                ) {
+                    while (
+                        observedEvents.none {
+                            it is
+                                CarStatusEvent.CommandResponse &&
+                                CarRequestEventMatcher.matches(
+                                    event =
+                                        it,
+                                    request =
+                                        request
+                                )
+                        } ||
+                        observedEvents.none {
+                            it is
+                                CarStatusEvent.Error &&
+                                !it.isRequestSpecific
+                        }
+                    ) {
+                        delay(
+                            10L
+                        )
+                    }
+                }
+
+                val generalErrors =
+                    observedEvents.filter { event ->
+                        event is CarStatusEvent.Error &&
+                            !event.isRequestSpecific
+                    }
+
+                assertEquals(
+                    1,
+                    generalErrors.size
+                )
+
+                val generalError =
+                    generalErrors.single() as CarStatusEvent.Error
+
+                assertTrue(
+                    generalError.message.contains(
+                        "Invalid response from BabyNodeCAN",
+                        ignoreCase =
+                            true
+                    )
+                )
+
+                val responses =
+                    observedEvents.filter { event ->
+                        event is CarStatusEvent.CommandResponse &&
+                            CarRequestEventMatcher.matches(
+                                event =
+                                    event,
+                                request =
+                                    request
+                            )
+                    }
+
+                assertEquals(
+                    1,
+                    responses.size
+                )
+
+                val response =
+                    responses.single() as CarStatusEvent.CommandResponse
+
+                assertEquals(
+                    "ok",
+                    response.status
+                )
+
+                assertEquals(
+                    response,
+                    CarRequestEventMatcher.selectBest(
+                        events =
+                            observedEvents.toList(),
+                        request =
+                            request
+                    )
+                )
+
+                assertFalse(
+                    transport
+                        .close()
+                        .any { event ->
+                            CarRequestEventMatcher.matches(
+                                event =
+                                    event,
+                                request =
+                                    request
+                            )
+                        }
+                )
+            } finally {
+                collector.cancelAndJoin()
+                transport.close()
+            }
+        }
+
+    @Test
+    fun missingProtocolVersionIsIgnoredAndPendingRequestIsResolvedByLaterValidResponse() =
+        runBlocking {
+            val invalidPacket =
+                JSONObject()
+                    .put("id", 1L)
+                    .put("type", "response")
+                    .put("command", "LOCK_DOORS")
+                    .put("status", "ok")
+                    .put("session_id", TEST_PEER_SESSION_ID)
+                    .toString() + "\n"
+
+            val connection =
+                ScriptedSequenceResponseConnection(
+                    listOf(
+                        invalidPacket,
+                        validResponsePacket(
+                            id = 1L,
+                            command = "LOCK_DOORS",
+                            status = "ok",
+                            sessionId = TEST_PEER_SESSION_ID
+                        )
+                    )
+                )
+...
```
