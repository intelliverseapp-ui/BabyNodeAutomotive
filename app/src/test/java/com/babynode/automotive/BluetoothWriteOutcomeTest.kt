package com.babynode.automotive

import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothWriteOutcomeTest {

    @Test
    fun noWriteAttemptMeansDefinitelyNotTransmitted() {
        assertEquals(
            BluetoothWriteOutcome.DEFINITELY_NOT_TRANSMITTED,
            BluetoothWriteOutcome.classify(
                writeAttemptStarted =
                    false,
                writeCompleted =
                    false
            )
        )
    }

    @Test
    fun startedWriteMeansOutcomeUnknown() {
        assertEquals(
            BluetoothWriteOutcome.OUTCOME_UNKNOWN,
            BluetoothWriteOutcome.classify(
                writeAttemptStarted =
                    true,
                writeCompleted =
                    false
            )
        )
    }

    @Test
    fun completedWriteMeansTransmitted() {
        assertEquals(
            BluetoothWriteOutcome.TRANSMITTED,
            BluetoothWriteOutcome.classify(
                writeAttemptStarted =
                    true,
                writeCompleted =
                    true
            )
        )
    }
}
