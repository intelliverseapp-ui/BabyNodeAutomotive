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
