package com.babynode.automotive

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over any CAN transport (Mock, TCP).
 *
 * USB has been removed from the architecture.
 */
interface CarCanTransport {

    /**
     * Connect to the underlying transport.
     */
    suspend fun connect()

    /**
     * Disconnect from the underlying transport.
     */
    suspend fun disconnect()

    /**
     * Send a raw CAN frame.
     */
    suspend fun sendFrame(frame: CarCanFrame)

    /**
     * Observe status/events from the transport.
     */
    fun status(): Flow<CarStatusEvent>
}

data class CarCanFrame(
    val id: Int,
    val data: ByteArray
)

/**
 * Unified status events emitted by any transport.
 */
sealed class CarStatusEvent {
    data class Connected(val transportName: String) : CarStatusEvent()
    data class Disconnected(val transportName: String) : CarStatusEvent()
    data class Error(val message: String, val throwable: Throwable? = null) : CarStatusEvent()
    data class FrameReceived(val frame: CarCanFrame) : CarStatusEvent()
    data class FrameSent(val frame: CarCanFrame) : CarStatusEvent()
}
