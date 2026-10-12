package com.babynode.automotive

/**
 * Central request-correlation policy shared by the status bus and
 * every user interface that follows a transport request.
 */
object CarRequestEventMatcher {

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
