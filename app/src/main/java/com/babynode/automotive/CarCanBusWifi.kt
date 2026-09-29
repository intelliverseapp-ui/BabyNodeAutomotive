package com.babynode.automotive

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Wi-Fi implementation stub.
 *
 * This compiles now and will be filled in later when the gateway is ready.
 */
class CarCanBusWifi : CarCanTransport {

    private val statusFlow = MutableSharedFlow<CarStatusEvent>(extraBufferCapacity = 64)

    override suspend fun connect() {
        // TODO: implement Wi-Fi connection later
        statusFlow.emit(CarStatusEvent.Connected("Wi-Fi (stub)"))
    }

    override suspend fun disconnect() {
        statusFlow.emit(CarStatusEvent.Disconnected("Wi-Fi (stub)"))
    }

    override suspend fun sendFrame(frame: CarCanFrame) {
        // TODO: implement Wi-Fi send later
        statusFlow.emit(CarStatusEvent.Error("Wi-Fi transport not implemented yet"))
    }

    override fun status(): Flow<CarStatusEvent> = statusFlow
}
