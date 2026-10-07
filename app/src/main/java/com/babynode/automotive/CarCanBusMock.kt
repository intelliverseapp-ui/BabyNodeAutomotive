package com.babynode.automotive

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Mock transport for testing without Bluetooth/ESP32 hardware.
 *
 * Simulates BabyNodeCAN acknowledgements using the
 * canonical command architecture.
 */
class CarCanBusMock : CarCanTransport {

    private val TAG = "CarCanBusMock"

    private val statusFlow =
        MutableSharedFlow<CarStatusEvent>(
            extraBufferCapacity = 64
        )

    override suspend fun connect() {

        Log.i(
            TAG,
            "connect(): Mock transport connecting"
        )

        statusFlow.emit(
            CarStatusEvent.Connected("Mock")
        )

        Log.i(
            TAG,
            "connect(): Mock transport connected"
        )
    }

    override suspend fun disconnect() {

        Log.i(
            TAG,
            "disconnect(): Mock transport disconnecting"
        )

        statusFlow.emit(
            CarStatusEvent.Disconnected("Mock")
        )

        Log.i(
            TAG,
            "disconnect(): Mock transport disconnected"
        )
    }

    override suspend fun sendCommand(
        command: CanonicalCommand
    ) {

        Log.i(
            TAG,
            "sendCommand(): command=${command.command}, value=${command.value}"
        )

        statusFlow.emit(
            CarStatusEvent.CommandSent(command)
        )

        Log.i(
            TAG,
            "sendCommand(): CommandSent event emitted"
        )

        statusFlow.emit(
            CarStatusEvent.CommandResponse(
                commandId = 0L,
                status = "ok"
            )
        )

        Log.i(
            TAG,
            "sendCommand(): Mock response emitted"
        )
    }

    override fun status(): Flow<CarStatusEvent> {

        Log.i(
            TAG,
            "status(): Returning mock status flow"
        )

        return statusFlow
    }

    override fun close() {

        Log.i(
            TAG,
            "close(): Mock transport closed"
        )
    }
}