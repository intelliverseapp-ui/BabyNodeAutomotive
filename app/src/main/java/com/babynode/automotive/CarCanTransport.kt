package com.babynode.automotive

import android.util.Log
import kotlinx.coroutines.flow.Flow

/**
 * Canonical command sent from BabyNode Automotive
 * to BabyNodeCAN over Bluetooth.
 */
data class CanonicalCommand(
    val command: String,
    val value: String? = null
) {
    init {
        Log.i(
            "CanonicalCommand",
            "Constructed command=$command value=$value"
        )
    }
}

/**
 * Bluetooth message envelope.
 */
data class BluetoothMessage(
    val id: Long,
    val type: String,
    val command: String,
    val value: String? = null
) {
    init {
        Log.i(
            "BluetoothMessage",
            "Created message id=$id type=$type command=$command"
        )
    }
}

/**
 * Abstraction over the BabyNode Automotive transport layer.
 *
 * Android sends canonical commands.
 * BabyNodeCAN converts those commands into vehicle-specific CAN traffic.
 */
interface CarCanTransport {

    companion object {
        private const val TAG = "CarCanTransport"
    }

    /**
     * Connect to the underlying transport.
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
     * Send a canonical command.
     */
    suspend fun sendCommand(command: CanonicalCommand) {
        Log.i(
            TAG,
            "sendCommand(): command=${command.command}, value=${command.value}"
        )
    }

    /**
     * Observe status/events from the transport.
     */
    fun status(): Flow<CarStatusEvent>

    /**
     * Release transport resources synchronously during teardown.
     */
    fun close() {
        Log.i(TAG, "close(): Default transport close invoked")
    }
}

/**
 * Unified status events emitted by the transport.
 */
sealed class CarStatusEvent {

    init {
        Log.i(
            "CarStatusEvent",
            "Event created: ${this::class.simpleName}"
        )
    }

    data class Connected(
        val transportName: String
    ) : CarStatusEvent() {
        init {
            Log.i(
                "CarStatusEvent",
                "Connected($transportName)"
            )
        }
    }

    data class Disconnected(
        val transportName: String
    ) : CarStatusEvent() {
        init {
            Log.i(
                "CarStatusEvent",
                "Disconnected($transportName)"
            )
        }
    }

    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : CarStatusEvent() {
        init {
            Log.e(
                "CarStatusEvent",
                "Error: $message throwable=${throwable?.message}"
            )
        }
    }

    data class CommandSent(
        val command: CanonicalCommand
    ) : CarStatusEvent() {
        init {
            Log.i(
                "CarStatusEvent",
                "CommandSent command=${command.command}"
            )
        }
    }

    data class CommandResponse(
        val commandId: Long,
        val status: String
    ) : CarStatusEvent() {
        init {
            Log.i(
                "CarStatusEvent",
                "CommandResponse id=$commandId status=$status"
            )
        }
    }
}