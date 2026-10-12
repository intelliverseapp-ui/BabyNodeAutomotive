package com.babynode.automotive

/**
 * Versioned capability contract currently supported by
 * BabyNodeAutomotive.
 *
 * This contract intentionally authorizes only the verified
 * development stub. It does not authorize physical vehicle
 * actuation.
 */
object BabyNodePeerContract {

    const val VERSION =
        1

    const val SUPPORTED_MODULE =
        "single"

    const val SUPPORTED_VEHICLE_COMMAND_CAPABILITY =
        "stub_only"

    const val allowsVehicleActuation =
        false

    fun supportsModule(
        moduleId: String
    ): Boolean {
        return moduleId ==
            SUPPORTED_MODULE
    }

    fun validateCapabilities(
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
            1L..BabyNodeProtocol.MAX_SESSION_ID &&
            module is String &&
            supportsModule(
                module
            ) &&
            canTransmit is Boolean &&
            canTransmit ==
                allowsVehicleActuation &&
            vehicleCommands is String &&
            vehicleCommands ==
                SUPPORTED_VEHICLE_COMMAND_CAPABILITY
    }
}
