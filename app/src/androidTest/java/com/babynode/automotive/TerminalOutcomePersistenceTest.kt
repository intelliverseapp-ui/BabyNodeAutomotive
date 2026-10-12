package com.babynode.automotive

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalOutcomePersistenceTest {

    private lateinit var app:
        BabyNodeApp

    @Before
    fun setUp() {
        app =
            ApplicationProvider.getApplicationContext()

        assertTrue(
            app.clearTerminalRequestOutcomes()
        )
    }

    @After
    fun tearDown() {
        app.clearTerminalRequestOutcomes()
    }

    @Test
    fun terminalOutcomeSurvivesActivityRecreationBoundary() {
        val event =
            CarStatusEvent.Error(
                message =
                    "Transport closed: outcome unknown",
                sessionGeneration =
                    1001L,
                requestId =
                    42L,
                command =
                    CanonicalCommand(
                        command =
                            "LOCK_DOORS"
                    )
            )

        assertTrue(
            app.saveTerminalRequestOutcomes(
                listOf(
                    event
                )
            )
        )

        assertEquals(
            listOf(
                event
            ),
            app.consumeTerminalRequestOutcomes()
        )
    }

    @Test
    fun terminalOutcomesAreConsumedOnlyOnce() {
        val event =
            CarStatusEvent.Error(
                message =
                    "Transport closed: outcome unknown",
                sessionGeneration =
                    1002L,
                requestId =
                    73L,
                command =
                    CanonicalCommand(
                        command =
                            "HEADLIGHTS_ON"
                    )
            )

        assertTrue(
            app.saveTerminalRequestOutcomes(
                listOf(
                    event
                )
            )
        )

        assertEquals(
            listOf(
                event
            ),
            app.consumeTerminalRequestOutcomes()
        )

        assertTrue(
            app.consumeTerminalRequestOutcomes()
                .isEmpty()
        )
    }

    @Test
    fun nonRequestEventsAreNotPersisted() {
        assertTrue(
            app.saveTerminalRequestOutcomes(
                listOf(
                    CarStatusEvent.Disconnected(
                        transportName =
                            "Bluetooth"
                    )
                )
            )
        )

        assertTrue(
            app.consumeTerminalRequestOutcomes()
                .isEmpty()
        )
    }
}
