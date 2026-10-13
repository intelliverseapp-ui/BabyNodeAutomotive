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
                    sessionGeneration =
                        request.sessionGeneration,
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
    fun rejectsSameRequestIdAndCommandFromDifferentSession() {
        val sessionOneRequest =
            CarCommandRequest(
                sessionGeneration =
                    1L,
                requestId =
                    42L,
                command =
                    CanonicalCommand(
                        command =
                            "LOCK_DOORS"
                    )
            )

        val sessionTwoResponse =
            CarStatusEvent.CommandResponse(
                sessionGeneration =
                    2L,
                requestId =
                    42L,
                command =
                    sessionOneRequest.command,
                status =
                    "ok"
            )

        assertFalse(
            CarRequestEventMatcher.matches(
                event =
                    sessionTwoResponse,
                request =
                    sessionOneRequest
            )
        )
    }

    @Test
    fun matchesRequestOnlyWithinSameSession() {
        val request =
            CarCommandRequest(
                sessionGeneration =
                    7L,
                requestId =
                    42L,
                command =
                    CanonicalCommand(
                        command =
                            "LOCK_DOORS"
                    )
            )

        val response =
            CarStatusEvent.CommandResponse(
                sessionGeneration =
                    7L,
                requestId =
                    request.requestId,
                command =
                    request.command,
                status =
                    "ok"
            )

        assertTrue(
            CarRequestEventMatcher.matches(
                event =
                    response,
                request =
                    request
            )
        )
    }

    @Test
    fun terminalSelectionRejectsOtherSessionEvents() {
        val request =
            CarCommandRequest(
                sessionGeneration =
                    11L,
                requestId =
                    1L,
                command =
                    CanonicalCommand(
                        command =
                            "HEADLIGHTS_ON"
                    )
            )

        val staleResponse =
            CarStatusEvent.CommandResponse(
                sessionGeneration =
                    10L,
                requestId =
                    request.requestId,
                command =
                    request.command,
                status =
                    "ok"
            )

        assertEquals(
            null,
            CarRequestEventMatcher.selectBest(
                events =
                    listOf(
                        staleResponse
                    ),
                request =
                    request
            )
        )
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
                sessionGeneration =
                    request.sessionGeneration,
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
                sessionGeneration =
                    request.sessionGeneration,
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
