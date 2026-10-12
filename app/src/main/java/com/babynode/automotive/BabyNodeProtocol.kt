package com.babynode.automotive

object BabyNodeProtocol {

    const val VERSION =
        1

    const val MIN_REQUEST_ID =
        1L

    const val MAX_REQUEST_ID =
        2_147_483_647L

    fun validateRequestId(
        id: Any?
    ): Boolean {
        val value =
            when (id) {
                is Byte ->
                    id.toLong()

                is Short ->
                    id.toLong()

                is Int ->
                    id.toLong()

                is Long ->
                    id

                else ->
                    return false
            }

        return value in
            MIN_REQUEST_ID..MAX_REQUEST_ID
    }

    const val MAX_SESSION_ID =
        4_294_967_295L

    fun validateHandshakeCapabilities(
        sessionId: Any?,
        module: Any?,
        canTransmit: Any?,
        vehicleCommands: Any?
    ): Boolean {
        val normalizedSessionId =
            when (sessionId) {
                is Byte ->
                    sessionId.toLong()

                is Short ->
                    sessionId.toLong()

                is Int ->
                    sessionId.toLong()

                is Long ->
                    sessionId

                else ->
                    return false
            }

        return normalizedSessionId in
            1L..MAX_SESSION_ID &&
            module is String &&
            module == "single" &&
            canTransmit is Boolean &&
            !canTransmit &&
            vehicleCommands is String &&
            vehicleCommands == "stub_only"
    }

    const val MAX_REASON_LENGTH =
        256

    fun validateResponseReason(
        reason: Any?
    ): Boolean {
        if (
            reason == null ||
            reason === org.json.JSONObject.NULL
        ) {
            return true
        }

        if (reason !is String) {
            return false
        }

        if (reason.length > MAX_REASON_LENGTH) {
            return false
        }

        return reason.none {
            character ->
            character.code < 0x20 ||
                character.code == 0x7F
        }
    }

    private val VALID_RESPONSE_STATUSES =
        setOf(
            "ok",
            "error",
            "unsupported"
        )

    fun validateResponseType(
        type: Any?
    ): Boolean {
        return type is String &&
            type == "response"
    }

    fun validateResponseStatus(
        status: Any?
    ): Boolean {
        return status is String &&
            status in VALID_RESPONSE_STATUSES
    }

    fun validateResponseCommand(
        command: Any?,
        expectedCommand: String
    ): Boolean {
        return command is String &&
            command == expectedCommand
    }

    fun validateVersion(
        version: Any?
    ): Boolean {
        return when (version) {
            is Byte ->
                version.toInt() == VERSION

            is Short ->
                version.toInt() == VERSION

            is Int ->
                version == VERSION

            is Long ->
                version == VERSION.toLong()

            else ->
                false
        }
    }
}
