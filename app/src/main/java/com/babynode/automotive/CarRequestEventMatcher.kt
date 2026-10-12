package com.babynode.automotive

/**
 * Central request-correlation policy shared by the status bus and
 * every user interface that follows a transport request.
 */
object CarRequestEventMatcher {

    /**
     * Selects the strongest matching event for one request.
     *
     * Terminal responses and request-specific errors always outrank
     * nonterminal CommandSent events. Among events of equal priority,
     * the newest event wins.
     */
    fun selectBest(
        events: List<CarStatusEvent>,
        request: CarCommandRequest
    ): CarStatusEvent? {
        val matchingEvents =
            events.filter { event ->
                matches(
                    event =
                        event,
                    request =
                        request
                )
            }

        return matchingEvents
            .asReversed()
            .firstOrNull { event ->
                event is CarStatusEvent.CommandResponse ||
                    event is CarStatusEvent.Error
            }
            ?: matchingEvents.lastOrNull()
    }

    fun matches(
        event: CarStatusEvent,
        request: CarCommandRequest
    ): Boolean {
        return when (event) {
            is CarStatusEvent.CommandSent -> {
                event.requestId ==
                    request.requestId &&
                    event.command ==
                    request.command
            }

            is CarStatusEvent.CommandResponse -> {
                event.requestId ==
                    request.requestId &&
                    event.command ==
                    request.command
            }

            is CarStatusEvent.Error -> {
                event.requestId ==
                    request.requestId &&
                    event.command ==
                    request.command
            }

            is CarStatusEvent.DeviceApprovalRequired,
            is CarStatusEvent.Connected,
            is CarStatusEvent.Disconnected -> {
                false
            }
        }
    }
}
