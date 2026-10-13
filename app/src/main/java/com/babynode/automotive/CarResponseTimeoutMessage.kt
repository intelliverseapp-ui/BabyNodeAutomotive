package com.babynode.automotive

/**
 * Produces safety-accurate wording for a command whose response did
 * not arrive before the timeout.
 */
object CarResponseTimeoutMessage {

    fun forRequest(
        request: CarCommandRequest
    ): String {
        return "BabyNodeCAN response timeout: " +
            "id=${request.requestId} " +
            "command=${request.command.command}; " +
            "outcome unknown because the command may have executed"
    }
}
