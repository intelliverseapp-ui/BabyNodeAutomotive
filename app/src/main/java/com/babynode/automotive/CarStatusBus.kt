package com.babynode.automotive

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface CarConnectionState {
    data object Disconnected : CarConnectionState
    data class Connecting(val transportName: String) : CarConnectionState
    data class Connected(val transportName: String) : CarConnectionState
    data class Failed(val transportName: String, val message: String) : CarConnectionState
}

/**
 * Holds the current connection state and a bounded history of transport events.
 */
class CarStatusBus {

    private val TAG = "CarStatusBus"

    private val _connectionState =
        MutableStateFlow<CarConnectionState>(CarConnectionState.Disconnected)
    val connectionState: StateFlow<CarConnectionState> = _connectionState.asStateFlow()

    private val _latestEvent = MutableStateFlow<CarStatusEvent?>(null)
    val latestEvent: StateFlow<CarStatusEvent?> = _latestEvent.asStateFlow()

    private val _events = MutableStateFlow<List<CarStatusEvent>>(emptyList())
    val events: StateFlow<List<CarStatusEvent>> = _events.asStateFlow()

    fun markConnecting(transportName: String) {
        Log.i(TAG, "markConnecting(): transport=$transportName")
        _connectionState.value = CarConnectionState.Connecting(transportName)
        Log.i(TAG, "ConnectionState → Connecting($transportName)")
    }

    fun accept(event: CarStatusEvent) {
        Log.i(TAG, "accept(): event=$event")

        // Update latest event
        _latestEvent.value = event

        // Update bounded history
        _events.update { current ->
            val updated = (current + event).takeLast(MAX_EVENT_HISTORY)
            Log.i(TAG, "Event history updated (size=${updated.size})")
            updated
        }

        // Update connection state based on event type
        when (event) {

            is CarStatusEvent.Connected -> {
                Log.i(TAG, "ConnectionState → Connected(${event.transportName})")
                _connectionState.value = CarConnectionState.Connected(event.transportName)
            }

            is CarStatusEvent.Disconnected -> {
                Log.i(TAG, "ConnectionState → Disconnected")
                _connectionState.value = CarConnectionState.Disconnected
            }

            is CarStatusEvent.Error -> {
                val state = _connectionState.value
                Log.e(TAG, "Transport error: ${event.message}")

                if (state is CarConnectionState.Connecting) {
                    Log.e(TAG, "ConnectionState → Failed(${state.transportName}, ${event.message})")
                    _connectionState.value =
                        CarConnectionState.Failed(state.transportName, event.message)
                }
            }

            is CarStatusEvent.FrameReceived -> {
                Log.i(TAG, "FrameReceived: id=${event.frame.id}, bytes=${event.frame.data.size}")
            }

            is CarStatusEvent.FrameSent -> {
                Log.i(TAG, "FrameSent: id=${event.frame.id}, bytes=${event.frame.data.size}")
            }
        }
    }

    private companion object {
        const val MAX_EVENT_HISTORY = 200
    }
}
