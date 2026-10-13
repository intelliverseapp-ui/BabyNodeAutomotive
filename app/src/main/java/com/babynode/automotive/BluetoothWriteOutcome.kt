package com.babynode.automotive

/**
 * Classifies what can be stated safely after a Bluetooth write path
 * completes, fails, or is cancelled.
 */
enum class BluetoothWriteOutcome {
    DEFINITELY_NOT_TRANSMITTED,
    OUTCOME_UNKNOWN,
    TRANSMITTED;

    companion object {
        fun classify(
            writeAttemptStarted: Boolean,
            writeCompleted: Boolean
        ): BluetoothWriteOutcome {
            if (writeCompleted) {
                return TRANSMITTED
            }

            if (writeAttemptStarted) {
                return OUTCOME_UNKNOWN
            }

            return DEFINITELY_NOT_TRANSMITTED
        }
    }
}
