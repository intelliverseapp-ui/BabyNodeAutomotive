package com.babynode.automotive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarStatusBusTest {

    @Test
    fun tracksConnectionTransitions() {
        val statusBus = CarStatusBus()

        statusBus.markConnecting("TCP")
        assertEquals(CarConnectionState.Connecting("TCP"), statusBus.connectionState.value)

        statusBus.accept(CarStatusEvent.Connected("TCP"))
        assertEquals(CarConnectionState.Connected("TCP"), statusBus.connectionState.value)

        statusBus.accept(CarStatusEvent.Disconnected("TCP"))
        assertEquals(CarConnectionState.Disconnected, statusBus.connectionState.value)
    }

    @Test
    fun connectionFailurePreservesFailureDetails() {
        val statusBus = CarStatusBus()
        statusBus.markConnecting("TCP")

        statusBus.accept(CarStatusEvent.Error("connection refused"))

        assertEquals(
            CarConnectionState.Failed("TCP", "connection refused"),
            statusBus.connectionState.value
        )
    }

    @Test
    fun eventHistoryRetainsOnlyTheMostRecentEntries() {
        val statusBus = CarStatusBus()
        repeat(205) { index ->
            statusBus.accept(CarStatusEvent.Error("error-$index"))
        }

        assertEquals(200, statusBus.events.value.size)
        assertEquals("error-5", (statusBus.events.value.first() as CarStatusEvent.Error).message)
        assertEquals("error-204", (statusBus.events.value.last() as CarStatusEvent.Error).message)
    }

    @Test
    fun transportReplacementResetsAllObservableStatus() {
        val bus =
            CarStatusBus()

        bus.markConnecting(
            "Bluetooth"
        )

        bus.accept(
            CarStatusEvent.Connected(
                transportName =
                    "Bluetooth"
            )
        )

        bus.accept(
            CarStatusEvent.CommandSent(
                requestId =
                    1L,
                command =
                    CanonicalCommand(
                        command =
                            "LOCK_DOORS"
                    )
            )
        )

        bus.resetForTransportReplacement()

        assertEquals(
            CarConnectionState.Disconnected,
            bus.connectionState.value
        )

        assertEquals(
            null,
            bus.latestEvent.value
        )

        assertTrue(
            bus.events.value.isEmpty()
        )
    }


    @Test
    fun findsResponseThatArrivedBeforeUiRegisteredRequest() {
        val bus =
            CarStatusBus()

        val request =
            CarCommandRequest(
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
                requestId =
                    request.requestId,
                command =
                    request.command,
                status =
                    "ok"
            )

        bus.accept(
            response
        )

        assertEquals(
            response,
            bus.latestEventFor(
                request
            )
        )
    }


    @Test
    fun unrelatedNewerEventsDoNotHideRequestResponse() {
        val bus =
            CarStatusBus()

        val request =
            CarCommandRequest(
                requestId =
                    42L,
                command =
                    CanonicalCommand(
                        command =
                            "LOCK_DOORS"
                    )
            )

        val matchingResponse =
            CarStatusEvent.CommandResponse(
                requestId =
                    request.requestId,
                command =
                    request.command,
                status =
                    "ok"
            )

        bus.accept(
            matchingResponse
        )

        bus.accept(
            CarStatusEvent.CommandResponse(
                requestId =
                    99L,
                command =
                    CanonicalCommand(
                        command =
                            "HEADLIGHTS_ON"
                    ),
                status =
                    "ok"
            )
        )

        bus.accept(
            CarStatusEvent.Connected(
                transportName =
                    "Bluetooth"
            )
        )

        assertEquals(
            matchingResponse,
            bus.latestEventFor(
                request
            )
        )
    }


    @Test
    fun transportReplacementPreventsRequestIdReuseCollision() {
        val bus =
            CarStatusBus()

        val oldRequest =
            CarCommandRequest(
                requestId =
                    1L,
                command =
                    CanonicalCommand(
                        command =
                            "LOCK_DOORS"
                    )
            )

        bus.accept(
            CarStatusEvent.CommandResponse(
                requestId =
                    oldRequest.requestId,
                command =
                    oldRequest.command,
                status =
                    "ok"
            )
        )

        bus.resetForTransportReplacement()

        val newRequest =
            CarCommandRequest(
                requestId =
                    1L,
                command =
                    CanonicalCommand(
                        command =
                            "LOCK_DOORS"
                    )
            )

        assertEquals(
            null,
            bus.latestEventFor(
                newRequest
            )
        )
    }


    @Test
    fun transportReplacementRetainsTerminalRequestOutcomes() {
        val bus =
            CarStatusBus()

        bus.accept(
            CarStatusEvent.Connected(
                transportName =
                    "Bluetooth"
            )
        )

        val terminalEvent =
            CarStatusEvent.Error(
                message =
                    "Transport closed: outcome unknown",
                requestId =
                    42L,
                command =
                    CanonicalCommand(
                        command =
                            "LOCK_DOORS"
                    )
            )

        bus.replaceTransportStatus(
            terminalEvents =
                listOf(
                    terminalEvent
                )
        )

        assertEquals(
            CarConnectionState.Disconnected,
            bus.connectionState.value
        )

        assertEquals(
            terminalEvent,
            bus.latestEvent.value
        )

        assertEquals(
            listOf(
                terminalEvent
            ),
            bus.events.value
        )
    }

}
