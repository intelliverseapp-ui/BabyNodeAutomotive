# BabyNode Sensitive Confirmation Test 01

Test coverage addition for Review 04 Section 10 item 3: verifying that the affirmative sensitive-command voice confirmation path transmits exactly one vehicle command.

No production code was modified, no unrelated tests were modified, and no builds or tests were run.

---

## 1. Test Added

Added test method to [VoiceInputSectionTest.kt](/Users/intelliversemacpro14/Projects/BabyNodeAutomotive/app/src/androidTest/java/com/babynode/automotive/VoiceInputSectionTest.kt):

- `sensitiveVoiceCommandConfirmationTransmitsExactlyOnce()`

---

## 2. Existing Test Infrastructure Reused

The new test directly mirrors and reuses the infrastructure established in `sensitiveVoiceCommandCanBeCancelledBeforeTransmission()`:

1. **`RecordingTransport`**: Tracks all commands dispatched across the transport via `sentCommands: MutableList<CanonicalCommand>`.
2. **`CarCommandDispatcher`**: Configured with `CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)` and the test `RecordingTransport`.
3. **Bootstrap Sequence**:
   - Dispatches `protocol.handshake` via `dispatcher.sendProtocolHandshake`.
   - Accepts synthetic `CarStatusEvent.CommandResponse` with status `"ok"`.
   - Dispatches `config.module` ("single") via `dispatcher.sendModuleConfig`.
   - Accepts synthetic `CarStatusEvent.CommandResponse` with status `"ok"`.
4. **`recognitionLauncherForTest`**: Seam supplying the recognized voice utterance `"unlock the doors"`.
5. **Compose Test Rule**: `createComposeRule()` rendering `VoiceInputSection` inside `BabyNodeAutomotiveTheme`.

---

## 3. Pre-Confirmation Zero-Write Assertion

1. Immediately following successful handshake and module configuration, the test establishes a baseline:
   ```kotlin
   val commandsBeforeVoiceReview = transport.sentCommands.size
   ```
2. The user initiates voice review and submission:
   - Clicks `"Speak Command"`
   - Verifies `"Review recognized command: unlock the doors"` is displayed
   - Clicks `"Submit Voice Command"`
   - Verifies `"Confirm sensitive command: UNLOCK_DOORS"` is displayed
   - Verifies `"Confirm Command"` and `"Cancel Command"` buttons are displayed
3. Within `composeRule.runOnIdle`, the pre-confirmation state is asserted:
   ```kotlin
   composeRule.runOnIdle {
       assertEquals(
           commandsBeforeVoiceReview,
           transport.sentCommands.size
       )

       assertTrue(
           transport.sentCommands.none { command ->
               command.command == "UNLOCK_DOORS"
           }
       )
   }
   ```
   This guarantees that no network packet or command transmission occurs while awaiting confirmation.

---

## 4. How Exactly-One Transmission Is Proven After Confirmation

1. The test clicks `"Confirm Command"`:
   ```kotlin
   composeRule
       .onNodeWithText("Confirm Command")
       .performClick()
   ```
2. In `composeRule.runOnIdle`, the test verifies:
   - The total size of `transport.sentCommands` increased by exactly 1:
     ```kotlin
     assertEquals(
         commandsBeforeVoiceReview + 1,
         transport.sentCommands.size
     )
     ```
   - The slice of commands sent after the pre-voice baseline has size 1:
     ```kotlin
     val commandsAfterBaseline =
         transport.sentCommands.subList(
             commandsBeforeVoiceReview,
             transport.sentCommands.size
         )

     assertEquals(
         1,
         commandsAfterBaseline.size
     )
     ```
   - The single transmitted command is `"UNLOCK_DOORS"`:
     ```kotlin
     assertEquals(
         "UNLOCK_DOORS",
         commandsAfterBaseline.single().command
     )
     ```

No arbitrary sleeps or delays are used; synchronization is driven deterministically by `composeRule.runOnIdle`.

---

## 5. Confirmation That Production Code Was Not Changed

- No files in `app/src/main/` were modified.
- No other test files under `app/src/test/` or `app/src/androidTest/` were modified.
- The existing cancellation test `sensitiveVoiceCommandCanBeCancelledBeforeTransmission()` was preserved intact without modification.

---

## 6. Concise Git Diff Summary

```diff
diff --git a/app/src/androidTest/java/com/babynode/automotive/VoiceInputSectionTest.kt b/app/src/androidTest/java/com/babynode/automotive/VoiceInputSectionTest.kt
index d420876..b301d78 100644
--- a/app/src/androidTest/java/com/babynode/automotive/VoiceInputSectionTest.kt
+++ b/app/src/androidTest/java/com/babynode/automotive/VoiceInputSectionTest.kt
@@ -284,6 +284,185 @@ class VoiceInputSectionTest {
         }
     }
 
+
+    @Test
+    fun sensitiveVoiceCommandConfirmationTransmitsExactlyOnce() {
+        val transport =
+            RecordingTransport()
+
+        val dispatcher =
+            CarCommandDispatcher(
+                scope =
+                    CoroutineScope(
+                        SupervisorJob() +
+                            Dispatchers.Unconfined
+                    ),
+                transport =
+                    transport
+            )
+
+        var handshakeResult:
+            CarCommandDispatchResult? =
+            null
+
+        dispatcher.sendProtocolHandshake(
+            onResult = { result ->
+                handshakeResult =
+                    result
+            }
+        )
+
+        val handshakeRequest =
+            (
+                handshakeResult as
+                    CarCommandDispatchResult.Submitted
+                ).request
+
+        dispatcher.acceptTransportEvent(
+            CarStatusEvent.CommandResponse(
+                requestId =
+                    handshakeRequest.requestId,
+                command =
+                    handshakeRequest.command,
+                status =
+                    "ok"
+            )
+        )
+
+        var configurationResult:
+            CarCommandDispatchResult? =
+            null
+
+        dispatcher.sendModuleConfig(
+            moduleJsonId =
+                "single",
+            onResult = { result ->
+                configurationResult =
+                    result
+            }
+        )
+
+        val configurationRequest =
+            (
+                configurationResult as
+                    CarCommandDispatchResult.Submitted
+                ).request
+
+        dispatcher.acceptTransportEvent(
+            CarStatusEvent.CommandResponse(
+                requestId =
+                    configurationRequest.requestId,
+                command =
+                    configurationRequest.command,
+                status =
+                    "ok"
+            )
+        )
+
+        val commandsBeforeVoiceReview =
+            transport.sentCommands.size
+
+        composeRule.setContent {
+            BabyNodeAutomotiveTheme(
+                dynamicColor =
+                    false
+            ) {
+                VoiceInputSection(
+                    dispatcher =
+                        dispatcher,
+                    status =
+                        null,
+                    eventHistory =
+                        emptyList(),
+                    recognitionLauncherForTest = { deliverResult ->
+                        deliverResult(
+                            "unlock the doors"
+                        )
+                    }
+                )
+            }
+        }
+
+        composeRule
+            .onNodeWithText(
+                "Speak Command"
+            )
+            .performClick()
+
+        composeRule
+            .onNodeWithText(
+                "Review recognized command: unlock the doors"
+            )
+            .assertIsDisplayed()
+
+        composeRule
+            .onNodeWithText(
+                "Submit Voice Command"
+            )
+            .performClick()
+
+        composeRule
+            .onNodeWithText(
+                "Confirm sensitive command: UNLOCK_DOORS"
+            )
+            .assertIsDisplayed()
+
+        composeRule
+            .onNodeWithText(
+                "Confirm Command"
+            )
+            .assertIsDisplayed()
+
+        composeRule
+            .onNodeWithText(
+                "Cancel Command"
+            )
+            .assertIsDisplayed()
+
+        composeRule.runOnIdle {
+            assertEquals(
+                commandsBeforeVoiceReview,
+                transport.sentCommands.size
+            )
+
+            assertTrue(
+                transport.sentCommands.none { command ->
+                    command.command ==
+                        "UNLOCK_DOORS"
+                }
+            )
+        }
+
+        composeRule
+            .onNodeWithText(
+                "Confirm Command"
+            )
+            .performClick()
+
+        composeRule.runOnIdle {
+            assertEquals(
+                commandsBeforeVoiceReview + 1,
+                transport.sentCommands.size
+            )
+
+            val commandsAfterBaseline =
+                transport.sentCommands.subList(
+                    commandsBeforeVoiceReview,
+                    transport.sentCommands.size
+                )
+
+            assertEquals(
+                1,
+                commandsAfterBaseline.size
+            )
+
+            assertEquals(
+                "UNLOCK_DOORS",
+                commandsAfterBaseline.single().command
+            )
+        }
+    }
+
     private class RecordingTransport :
         CarCanTransport {
```
