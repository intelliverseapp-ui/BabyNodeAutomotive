package com.babynode.automotive

/**
 * Determines whether the app may continue after attempting to remove
 * the approved BabyNodeCAN identity from persistent storage.
 */
data class ForgetDeviceDecision(
    val identityForgotten: Boolean,
    val replaceTransport: Boolean,
    val showPersistenceError: Boolean
) {

    companion object {

        fun fromPersistenceResult(
            identityRemoved: Boolean
        ): ForgetDeviceDecision {
            return if (identityRemoved) {
                ForgetDeviceDecision(
                    identityForgotten =
                        true,
                    replaceTransport =
                        true,
                    showPersistenceError =
                        false
                )
            } else {
                ForgetDeviceDecision(
                    identityForgotten =
                        false,
                    replaceTransport =
                        false,
                    showPersistenceError =
                        true
                )
            }
        }
    }
}
