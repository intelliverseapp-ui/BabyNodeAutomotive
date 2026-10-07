package com.babynode.automotive

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface CarConnectionState {

    data object Disconnected :
        CarConnectionState

    data class Connecting(
        val transportName: String
    ) : CarConnectionState

    data class Connected(
        val transportName: String
    ) : CarConnectionState

    data class Failed(
        val transportName: String,
        val message: String
    ) : CarConnectionState
}

/**
 * Holds the current connection state, latest transport event,
 * and a bounded transport-event history.
 *
 * This class intentionally contains no Android framework logging
 * so its behavior can be tested with ordinary JVM unit tests.
 */
class CarStatusBus {

    private val _connectionState =
        MutableStateFlow<CarConnectionState>(
            CarConnectionState.Disconnected
        )

    val connectionState:
        StateFlow<CarConnectionState> =
        _connectionState.asStateFlow()

    private val _latestEvent =
        MutableStateFlow<CarStatusEvent?>(
            null
        )

    val latestEvent:
        StateFlow<CarStatusEvent?> =
        _latestEvent.asStateFlow()

    private val _events =
        MutableStateFlow<List<CarStatusEvent>>(
            emptyList()
        )

    val events:
        StateFlow<List<CarStatusEvent>> =
        _events.asStateFlow()

    /**
     * Starts a new connection attempt.
     *
     * Calling this method clears any previous Failed state by
     * replacing it with Connecting.
     */
    fun markConnecting(
        transportName: String
    ) {
        _connectionState.value =
            CarConnectionState.Connecting(
                transportName =
                    transportName
            )
    }

    /**
     * Accepts a transport event and updates observable state.
     *
     * A Disconnected event does not overwrite an existing Failed
     * state. This preserves the useful failure message until a new
     * connection attempt begins or a connection succeeds.
     */
    fun accept(
        event: CarStatusEvent
    ) {
        _latestEvent.value =
            event

        _events.update { currentEvents ->
            (currentEvents + event)
                .takeLast(
                    MAX_EVENT_HISTORY
                )
        }

        when (event) {
            is CarStatusEvent.Connected -> {
                _connectionState.value =
                    CarConnectionState.Connected(
                        transportName =
                            event.transportName
                    )
            }

            is CarStatusEvent.Disconnected -> {
                val currentState =
                    _connectionState.value

                if (
                    currentState !is
                    CarConnectionState.Failed
                ) {
                    _connectionState.value =
                        CarConnectionState.Disconnected
                }
            }

            is CarStatusEvent.Error -> {
                handleError(
                    event
                )
            }

            is CarStatusEvent.CommandSent -> {
                // Command events do not change connection state.
            }

            is CarStatusEvent.CommandResponse -> {
                // Response events do not change connection state.
            }
        }
    }

    /**
     * Clears event history without changing the current connection
     * state. This can be connected to a future UI control if needed.
     */
    fun clearEventHistory() {
        _events.value =
            emptyList()

        _latestEvent.value =
            null
    }

    private fun handleError(
        event: CarStatusEvent.Error
    ) {
        when (
            val currentState =
                _connectionState.value
        ) {
            is CarConnectionState.Connecting -> {
                _connectionState.value =
                    CarConnectionState.Failed(
                        transportName =
                            currentState.transportName,
                        message =
                            event.message
                    )
            }

            is CarConnectionState.Connected -> {
                /*
                 * A command timeout, malformed response, or other
                 * command-level error does not necessarily mean the
                 * Bluetooth connection was lost.
                 *
                 * The transport must emit Disconnected when the
                 * socket actually closes.
                 */
            }

            is CarConnectionState.Failed -> {
                /*
                 * Preserve the first meaningful connection failure.
                 * A new connection attempt through markConnecting()
                 * will replace this state.
                 */
            }

            CarConnectionState.Disconnected -> {
                /*
                 * An error received while disconnected is preserved
                 * as latestEvent and in event history, but there is
                 * no active transport name from which to construct
                 * a reliable Failed connection state.
                 */
            }
        }
    }

    private companion object {
        const val MAX_EVENT_HISTORY =
            200
    }
}