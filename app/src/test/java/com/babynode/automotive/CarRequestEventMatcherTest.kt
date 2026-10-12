package com.babynode.automotive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarRequestEventMatcherTest {

    private val request =
        CarCommandRequest(
            requestId =
                42L,
            command =
                CanonicalCommand(
                    command =
                        "LOCK_DOORS"
                )
        )

    @Test
    fun matchesExactRequestEvents() {
        val events =
            listOf(
                CarStatusEvent.CommandSent(
                    requestId =
                        request.requestId,
                    command =
                        request.command
                ),
                CarStatusEvent.CommandResponse(
                    requestId =
                        request.requestId,
                    command =
                        request.command,
                    status =
                        "ok"
                ),
                CarStatusEvent.Error(
                    message =
                        "outcome unknown",
                    requestId =
                        request.requestId,
                    command =
                        request.command
                )
            )

        events.forEach { event ->
            assertTrue(
                CarRequestEventMatcher.matches(
                    event =
                        event,
                    request =
                        request
                )
            )
        }
    }

    @Test
    fun terminalResponseOutranksLaterCommandSent() {
        val response =
            CarStatusEvent.CommandResponse(
                requestId =
                    request.requestId,
                command =
                    request.command,
                status =
                    "ok"
            )

        val sent =
            CarStatusEvent.CommandSent(
                requestId =
                    request.requestId,
                command =
                    request.command
            )

        assertEquals(
            response,
            CarRequestEventMatcher.selectBest(
                events =
                    listOf(
                        response,
                        sent
                    ),
                request =
                    request
            )
        )
    }

    @Test
    fun terminalErrorOutranksLaterCommandSent() {
        val error =
            CarStatusEvent.Error(
                message =
                    "Response timeout: outcome unknown",
                requestId =
                    request.requestId,
                command =
                    request.command
            )

        val sent =
            CarStatusEvent.CommandSent(
                requestId =
                    request.requestId,
                command =
                    request.command
            )

        assertEquals(
            error,
            CarRequestEventMatcher.selectBest(
                events =
                    listOf(
                        error,
                        sent
                    ),
                request =
                    request
            )
        )
    }

    @Test
    fun newestTerminalOutcomeWinsAmongTerminalEvents() {
        val olderResponse =
            CarStatusEvent.CommandResponse(
                requestId =
                    request.requestId,
                command =
                    request.command,
                status =
                    "ok"
            )

        val newerError =
            CarStatusEvent.Error(
                message =
                    "Connection closed: outcome unknown",
                requestId =
                    request.requestId,
                command =
                    request.command
            )

        assertEquals(
            newerError,
            CarRequestEventMatcher.selectBest(
                events =
                    listOf(
                        olderResponse,
                        newerError
                    ),
                request =
                    request
            )
        )
    }

    @Test
    fun commandSentIsReturnedWithoutTerminalOutcome() {
        val sent =
            CarStatusEvent.CommandSent(
                requestId =
                    request.requestId,
                command =
                    request.command
            )

        assertEquals(
            sent,
            CarRequestEventMatcher.selectBest(
                events =
                    listOf(
                        CarStatusEvent.Connected(
                            transportName =
                                "Bluetooth"
                        ),
                        sent
                    ),
                request =
                    request
            )
        )
    }


    @Test
    fun rejectsDifferentRequestIdentity() {
        assertFalse(
            CarRequestEventMatcher.matches(
                event =
                    CarStatusEvent.CommandResponse(
                        requestId =
                            99L,
                        command =
                            request.command,
                        status =
                            "ok"
                    ),
                request =
                    request
            )
        )

        assertFalse(
            CarRequestEventMatcher.matches(
                event =
                    CarStatusEvent.CommandResponse(
                        requestId =
                            request.requestId,
                        command =
                            CanonicalCommand(
                                command =
                                    "HEADLIGHTS_ON"
                            ),
                        status =
                            "ok"
                    ),
                request =
                    request
            )
        )
    }

    @Test
    fun rejectsConnectionEvents() {
        assertFalse(
            CarRequestEventMatcher.matches(
                event =
                    CarStatusEvent.Connected(
                        transportName =
                            "Bluetooth"
                    ),
                request =
                    request
            )
        )

        assertFalse(
            CarRequestEventMatcher.matches(
                event =
                    CarStatusEvent.Disconnected(
                        transportName =
                            "Bluetooth"
                    ),
                request =
                    request
            )
        )
    }
}
