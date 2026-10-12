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
