package com.babynode.automotive

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
