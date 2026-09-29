package com.babynode.automotive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Mock transport for testing without USB/WiFi/DuoCAN hardware.
 *
 * ONE RESPONSIBILITY:
 * Simulate sending and receiving CAN frames.
 */
class CarCanBusMock(
    private val scope: CoroutineScope
) : CarCanTransport {

    private val statusFlow = MutableSharedFlow<CarStatusEvent>(extraBufferCapacity = 64)

    override suspend fun connect() {
        scope.launch {
            statusFlow.emit(CarStatusEvent.Connected("MOCK"))
        }
    }

    override suspend fun disconnect() {
        scope.launch {
            statusFlow.emit(CarStatusEvent.Disconnected("MOCK"))
        }
    }

    override suspend fun sendFrame(frame: CarCanFrame) {
        // Simulate sending
        scope.launch {
            statusFlow.emit(CarStatusEvent.FrameSent(frame))
        }

        // Simulate DuoCAN ACK
        scope.launch {
            val ack = CarCanFrame(
                id = 0xFFFF,
                data = byteArrayOf(
                    0xAC.toByte(), // mock ACK header
                    0x01.toByte()  // mock ACK payload
                )
            )
            statusFlow.emit(CarStatusEvent.FrameReceived(ack))
        }
    }

    override fun status(): Flow<CarStatusEvent> = statusFlow
}
