package com.babynode.automotive

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.babynode.automotive.ui.AutomotiveScreen
import com.babynode.automotive.ui.theme.BabyNodeAutomotiveTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test

class AutomotiveScreenVisibilityTest {

    @get:Rule
    val composeRule =
        createComposeRule()

    @Test
    fun releaseSurfaceHidesDebugTools() {
        composeRule.setContent {
            BabyNodeAutomotiveTheme(
                dynamicColor =
                    false
            ) {
                AutomotiveScreen(
                    dispatcher =
                        createDispatcher(),
                    moduleType =
                        ModuleType.SINGLE_CAN,
                    connectionState =
                        CarConnectionState.Disconnected,
                    status =
                        null,
                    eventHistory =
                        emptyList(),
                    debugToolsVisible =
                        false
                )
            }
        }

        composeRule
            .onNodeWithText(
                "Voice Input"
            )
            .assertIsDisplayed()

        composeRule
            .onAllNodesWithText(
                "Command Tester"
            )
            .assertCountEquals(
                0
            )

        composeRule
            .onAllNodesWithText(
                "CAN Debug"
            )
            .assertCountEquals(
                0
            )
    }

    @Test
    fun debugSurfaceShowsDebugTools() {
        composeRule.setContent {
            BabyNodeAutomotiveTheme(
                dynamicColor =
                    false
            ) {
                AutomotiveScreen(
                    dispatcher =
                        createDispatcher(),
                    moduleType =
                        ModuleType.SINGLE_CAN,
                    connectionState =
                        CarConnectionState.Disconnected,
                    status =
                        null,
                    eventHistory =
                        emptyList(),
                    debugToolsVisible =
                        true
                )
            }
        }

        composeRule
            .onNodeWithText(
                "Command Tester"
            )
            .performScrollTo()
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "CAN Debug"
            )
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun createDispatcher():
        CarCommandDispatcher {
        return CarCommandDispatcher(
            scope =
                CoroutineScope(
                    SupervisorJob() +
                        Dispatchers.Main
                ),
            transport =
                VisibilityTestTransport()
        )
    }

    private class VisibilityTestTransport :
        CarCanTransport {

        override suspend fun connect() {
        }

        override suspend fun disconnect() {
        }

        override suspend fun sendCommand(
            command: CanonicalCommand
        ): CarCommandRequest {
            error(
                "Visibility test does not send commands"
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
