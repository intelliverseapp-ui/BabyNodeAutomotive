package com.babynode.automotive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Aggregates status/events from the active transport(s)
 * and exposes a single stream for UI and debug screens.
 */
class CarStatusBus(
    private val scope: CoroutineScope
) {

    private val internalEvents = MutableSharedFlow<CarStatusEvent>(extraBufferCapacity = 64)

    fun eventsFromTransport(transport: CarCanTransport): Flow<CarStatusEvent> {
        return merge(internalEvents, transport.status())
    }

    fun emit(event: CarStatusEvent) {
        scope.launch {
            internalEvents.emit(event)
        }
    }
}
