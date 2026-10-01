package com.babynode.automotive

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Mock transport for testing without TCP/DuoCAN hardware.
 *
 * ONE RESPONSIBILITY:
 * Simulate sending and receiving CAN frames exactly like the real TCP transport.
 */
class CarCanBusMock : CarCanTransport {

    private val TAG = "CarCanBusMock"

    private val statusFlow = MutableSharedFlow<CarStatusEvent>(extraBufferCapacity = 64)

    override suspend fun connect() {
        Log.i(TAG, "connect(): Mock transport connecting")
        statusFlow.emit(CarStatusEvent.Connected("Mock"))
        Log.i(TAG, "connect(): Mock transport connected")
    }

    override suspend fun disconnect() {
        Log.i(TAG, "disconnect(): Mock transport disconnecting")
        statusFlow.emit(CarStatusEvent.Disconnected("Mock"))
        Log.i(TAG, "disconnect(): Mock transport disconnected")
    }

    override suspend fun sendFrame(frame: CarCanFrame) {
        Log.i(TAG, "sendFrame(): Sending mock CAN frame id=${frame.id}, bytes=${frame.data.size}")
        statusFlow.emit(CarStatusEvent.FrameSent(frame))
        Log.i(TAG, "sendFrame(): FrameSent event emitted")

        // Simulated ACK frame
        val ack = CarCanFrame(
            id = 0xFFFF,
            data = byteArrayOf(
                0xAC.toByte(),
                0x01.toByte()
            )
        )

        Log.i(TAG, "sendFrame(): Emitting mock ACK id=0xFFFF payload=AC 01")
        statusFlow.emit(CarStatusEvent.FrameReceived(ack))
        Log.i(TAG, "sendFrame(): FrameReceived (ACK) event emitted")
    }

    override fun status(): Flow<CarStatusEvent> {
        Log.i(TAG, "status(): Returning mock status flow")
        return statusFlow
    }

    override fun close() {
        Log.i(TAG, "close(): Mock transport closed")
    }
}
