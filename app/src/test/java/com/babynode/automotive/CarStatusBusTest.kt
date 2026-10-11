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

}
