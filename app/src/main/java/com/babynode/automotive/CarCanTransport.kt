package com.babynode.automotive

import android.util.Log
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the CAN transport layer.
 *
 * Transport selection (Mock/TCP) has been removed.
 * TCP is now always used, but this interface remains
 * as the unified abstraction for the transport pipeline.
 */
interface CarCanTransport {

    companion object {
        private const val TAG = "CarCanTransport"
    }

    /**
     * Connect to the underlying transport.
     *
     * Implementations (TCP) will override this.
     */
    suspend fun connect() {
        Log.i(TAG, "connect(): Default transport connect invoked")
    }

    /**
     * Disconnect from the underlying transport.
     */
    suspend fun disconnect() {
        Log.i(TAG, "disconnect(): Default transport disconnect invoked")
    }

    /**
     * Send a raw CAN frame.
     *
     * Dispatcher will call this for outgoing CAN traffic.
     */
    suspend fun sendFrame(frame: CarCanFrame) {
        Log.i(
            TAG,
            "sendFrame(): Default transport send invoked for frame id=${frame.id}, bytes=${frame.data.size}"
        )
    }

    /**
     * Observe status/events from the transport.
     *
     * TCP transport emits Connected, Disconnected, FrameReceived, etc.
     */
    fun status(): Flow<CarStatusEvent>

    /**
     * Release transport resources synchronously during teardown.
     */
    fun close() {
        Log.i(TAG, "close(): Default transport close invoked")
    }
}

data class CarCanFrame(
    val id: Int,
    val data: ByteArray
) {
    init {
        Log.i("CarCanFrame", "Constructed CAN frame id=$id bytes=${data.size}")
    }
}

/**
 * Unified status events emitted by the transport.
 */
sealed class CarStatusEvent {

    init {
        Log.i("CarStatusEvent", "Event created: ${this::class.simpleName}")
    }

    data class Connected(val transportName: String) : CarStatusEvent() {
        init { Log.i("CarStatusEvent", "Connected($transportName)") }
    }

    data class Disconnected(val transportName: String) : CarStatusEvent() {
        init { Log.i("CarStatusEvent", "Disconnected($transportName)") }
    }

    data class Error(val message: String, val throwable: Throwable? = null) : CarStatusEvent() {
        init { Log.e("CarStatusEvent", "Error: $message throwable=${throwable?.message}") }
    }

    data class FrameReceived(val frame: CarCanFrame) : CarStatusEvent() {
        init { Log.i("CarStatusEvent", "FrameReceived id=${frame.id} bytes=${frame.data.size}") }
    }

    data class FrameSent(val frame: CarCanFrame) : CarStatusEvent() {
        init { Log.i("CarStatusEvent", "FrameSent id=${frame.id} bytes=${frame.data.size}") }
    }
}
