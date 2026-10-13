package com.babynode.automotive

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLifecycleTest {

    private lateinit var app:
        BabyNodeApp

    @Before
    fun setUp() {
        app =
            ApplicationProvider
                .getApplicationContext()

        MainActivity
            .resetTransportFactoryForTest()

        assertTrue(
            app.clearTerminalRequestOutcomes()
        )
    }

    @After
    fun tearDown() {
        MainActivity
            .resetTransportFactoryForTest()

        app.clearTerminalRequestOutcomes()
    }

    @Test
    fun replacementClosesOldTransportAndDestructionClosesNewTransport() {
        val firstTerminalEvent =
            CarStatusEvent.Error(
                message =
                    "Old transport replaced: outcome unknown",
                sessionGeneration =
                    1001L,
                requestId =
                    41L,
                command =
                    CanonicalCommand(
                        command =
                            "LOCK_DOORS"
                    )
            )

        val secondTerminalEvent =
            CarStatusEvent.Error(
                message =
                    "Activity destroyed: outcome unknown",
                sessionGeneration =
                    1002L,
                requestId =
                    42L,
                command =
                    CanonicalCommand(
                        command =
                            "HEADLIGHTS_ON"
                    )
            )

        val firstTransport =
            RecordingLifecycleTransport(
                terminalEvents =
                    listOf(
                        firstTerminalEvent
                    )
            )

        val secondTransport =
            RecordingLifecycleTransport(
                terminalEvents =
                    listOf(
                        secondTerminalEvent
                    )
            )

        val transports =
            ArrayDeque<CarCanTransport>().apply {
                addLast(
                    firstTransport
                )

                addLast(
                    secondTransport
                )
            }

        MainActivity.installTransportFactoryForTest {
            transports.removeFirst()
        }

        val scenario =
            ActivityScenario.launch(
                MainActivity::class.java
            )

        try {
            scenario.onActivity { activity ->
                activity.replaceTransportForTest()
            }

            assertEquals(
                1,
                firstTransport.closeCount.get()
            )

            assertEquals(
                0,
                secondTransport.closeCount.get()
            )
        } finally {
            scenario.close()
        }

        assertEquals(
            1,
            firstTransport.closeCount.get()
        )

        assertEquals(
            1,
            secondTransport.closeCount.get()
        )

        assertEquals(
            listOf(
                secondTerminalEvent
            ),
            app.consumeTerminalRequestOutcomes()
        )

        assertTrue(
            app.consumeTerminalRequestOutcomes()
                .isEmpty()
        )
    }

    private class RecordingLifecycleTransport(
        private val terminalEvents:
            List<CarStatusEvent>
    ) : CarCanTransport {

        val closeCount =
            AtomicInteger(
                0
            )

        override suspend fun connect() {
        }

        override suspend fun disconnect() {
        }

        override suspend fun sendCommand(
            command: CanonicalCommand
        ): CarCommandRequest {
            error(
                "Lifecycle test does not send commands"
            )
        }

        override fun status():
            Flow<CarStatusEvent> {
            return emptyFlow()
        }

        override fun close():
            List<CarStatusEvent> {
            return when (
                closeCount.incrementAndGet()
            ) {
                1 ->
                    terminalEvents

                else ->
                    emptyList()
            }
        }
    }
}
