package com.babynode.automotive

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.babynode.automotive.ui.VoiceInputSection
import com.babynode.automotive.ui.theme.BabyNodeAutomotiveTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VoiceInputSectionTest {

    @get:Rule
    val composeRule =
        createComposeRule()

    @Test
    fun recognizedCommandCanBeReviewedAndCancelledWithoutTransmission() {
        val transport =
            RecordingTransport()

        val dispatcher =
            CarCommandDispatcher(
                scope =
                    CoroutineScope(
                        SupervisorJob() +
                            Dispatchers.Main
                    ),
                transport =
                    transport
            )

        composeRule.setContent {
            BabyNodeAutomotiveTheme(
                dynamicColor =
                    false
            ) {
                VoiceInputSection(
                    dispatcher =
                        dispatcher,
                    status =
                        null,
                    eventHistory =
                        emptyList(),
                    recognitionLauncherForTest = { deliverResult ->
                        deliverResult(
                            "unlock the doors"
                        )
                    }
                )
            }
        }

        composeRule
            .onNodeWithText(
                "Speak Command"
            )
            .performClick()

        composeRule
            .onNodeWithText(
                "Review recognized command: unlock the doors"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Submit Voice Command"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Cancel Voice Command"
            )
            .assertIsDisplayed()
            .performClick()

        composeRule
            .onNodeWithText(
                "Voice recognition cancelled"
            )
            .assertIsDisplayed()

        composeRule
            .onAllNodesWithText(
                "Review recognized command: unlock the doors"
            )
            .assertCountEquals(
                0
            )

        composeRule.runOnIdle {
            assertTrue(
                transport.sentCommands.isEmpty()
            )
        }
    }


    @Test
    fun sensitiveVoiceCommandCanBeCancelledBeforeTransmission() {
        val transport =
            RecordingTransport()

        val dispatcher =
            CarCommandDispatcher(
                scope =
                    CoroutineScope(
                        SupervisorJob() +
                            Dispatchers.Unconfined
                    ),
                transport =
                    transport
            )

        var handshakeResult:
            CarCommandDispatchResult? =
            null

        dispatcher.sendProtocolHandshake(
            onResult = { result ->
                handshakeResult =
                    result
            }
        )

        val handshakeRequest =
            (
                handshakeResult as
                    CarCommandDispatchResult.Submitted
                ).request

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId =
                    handshakeRequest.requestId,
                command =
                    handshakeRequest.command,
                status =
                    "ok"
            )
        )

        var configurationResult:
            CarCommandDispatchResult? =
            null

        dispatcher.sendModuleConfig(
            moduleJsonId =
                "single",
            onResult = { result ->
                configurationResult =
                    result
            }
        )

        val configurationRequest =
            (
                configurationResult as
                    CarCommandDispatchResult.Submitted
                ).request

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId =
                    configurationRequest.requestId,
                command =
                    configurationRequest.command,
                status =
                    "ok"
            )
        )

        val commandsBeforeVoiceReview =
            transport.sentCommands.size

        composeRule.setContent {
            BabyNodeAutomotiveTheme(
                dynamicColor =
                    false
            ) {
                VoiceInputSection(
                    dispatcher =
                        dispatcher,
                    status =
                        null,
                    eventHistory =
                        emptyList(),
                    recognitionLauncherForTest = { deliverResult ->
                        deliverResult(
                            "unlock the doors"
                        )
                    }
                )
            }
        }

        composeRule
            .onNodeWithText(
                "Speak Command"
            )
            .performClick()

        composeRule
            .onNodeWithText(
                "Review recognized command: unlock the doors"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Submit Voice Command"
            )
            .performClick()

        composeRule
            .onNodeWithText(
                "Confirm sensitive command: UNLOCK_DOORS"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Confirm Command"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Cancel Command"
            )
            .assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(
                commandsBeforeVoiceReview,
                transport.sentCommands.size
            )
        }

        composeRule
            .onNodeWithText(
                "Cancel Command"
            )
            .performClick()

        composeRule
            .onNodeWithText(
                "Request rejected: sensitive command cancelled before transmission"
            )
            .assertIsDisplayed()

        composeRule
            .onAllNodesWithText(
                "Confirm sensitive command: UNLOCK_DOORS"
            )
            .assertCountEquals(
                0
            )

        composeRule.runOnIdle {
            assertEquals(
                commandsBeforeVoiceReview,
                transport.sentCommands.size
            )

            assertTrue(
                transport.sentCommands.none { command ->
                    command.command ==
                        "UNLOCK_DOORS"
                }
            )
        }
    }


    @Test
    fun sensitiveVoiceCommandConfirmationTransmitsExactlyOnce() {
        val transport =
            RecordingTransport()

        val dispatcher =
            CarCommandDispatcher(
                scope =
                    CoroutineScope(
                        SupervisorJob() +
                            Dispatchers.Unconfined
                    ),
                transport =
                    transport
            )

        var handshakeResult:
            CarCommandDispatchResult? =
            null

        dispatcher.sendProtocolHandshake(
            onResult = { result ->
                handshakeResult =
                    result
            }
        )

        val handshakeRequest =
            (
                handshakeResult as
                    CarCommandDispatchResult.Submitted
                ).request

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId =
                    handshakeRequest.requestId,
                command =
                    handshakeRequest.command,
                status =
                    "ok"
            )
        )

        var configurationResult:
            CarCommandDispatchResult? =
            null

        dispatcher.sendModuleConfig(
            moduleJsonId =
                "single",
            onResult = { result ->
                configurationResult =
                    result
            }
        )

        val configurationRequest =
            (
                configurationResult as
                    CarCommandDispatchResult.Submitted
                ).request

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId =
                    configurationRequest.requestId,
                command =
                    configurationRequest.command,
                status =
                    "ok"
            )
        )

        val commandsBeforeVoiceReview =
            transport.sentCommands.size

        composeRule.setContent {
            BabyNodeAutomotiveTheme(
                dynamicColor =
                    false
            ) {
                VoiceInputSection(
                    dispatcher =
                        dispatcher,
                    status =
                        null,
                    eventHistory =
                        emptyList(),
                    recognitionLauncherForTest = { deliverResult ->
                        deliverResult(
                            "unlock the doors"
                        )
                    }
                )
            }
        }

        composeRule
            .onNodeWithText(
                "Speak Command"
            )
            .performClick()

        composeRule
            .onNodeWithText(
                "Review recognized command: unlock the doors"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Submit Voice Command"
            )
            .performClick()

        composeRule
            .onNodeWithText(
                "Confirm sensitive command: UNLOCK_DOORS"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Confirm Command"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Cancel Command"
            )
            .assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(
                commandsBeforeVoiceReview,
                transport.sentCommands.size
            )

            assertTrue(
                transport.sentCommands.none { command ->
                    command.command ==
                        "UNLOCK_DOORS"
                }
            )
        }

        composeRule
            .onNodeWithText(
                "Confirm Command"
            )
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                commandsBeforeVoiceReview + 1,
                transport.sentCommands.size
            )

            val commandsAfterBaseline =
                transport.sentCommands.subList(
                    commandsBeforeVoiceReview,
                    transport.sentCommands.size
                )

            assertEquals(
                1,
                commandsAfterBaseline.size
            )

            assertEquals(
                "UNLOCK_DOORS",
                commandsAfterBaseline.single().command
            )
        }
    }

    private class RecordingTransport :
        CarCanTransport {

        val sentCommands =
            mutableListOf<CanonicalCommand>()

        private var nextRequestId =
            1L

        override suspend fun connect() {
        }

        override suspend fun disconnect() {
        }

        override suspend fun sendCommand(
            command: CanonicalCommand
        ): CarCommandRequest {
            sentCommands +=
                command

            return CarCommandRequest(
                requestId =
                    nextRequestId++,
                command =
                    command
            )
        }

        override fun status():
            Flow<CarStatusEvent> {
            return emptyFlow()
        }

        override fun close():
            List<CarStatusEvent> {
            return emptyList()
        }
    }
}
