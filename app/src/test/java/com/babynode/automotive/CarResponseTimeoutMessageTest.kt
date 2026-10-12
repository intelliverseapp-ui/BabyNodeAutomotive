package com.babynode.automotive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarResponseTimeoutMessageTest {

    @Test
    fun timeoutCommunicatesUnknownPhysicalOutcome() {
        val request =
            CarCommandRequest(
                requestId =
                    42L,
                command =
                    CanonicalCommand(
                        command =
                            "LOCK_DOORS"
                    )
            )

        val message =
            CarResponseTimeoutMessage.forRequest(
                request
            )

        assertTrue(
            message.contains(
                "outcome unknown",
                ignoreCase =
                    true
            )
        )

        assertTrue(
            message.contains(
                "may have executed",
                ignoreCase =
                    true
            )
        )

        assertFalse(
            message.contains(
                "did not execute",
                ignoreCase =
                    true
            )
        )

        assertFalse(
            message.contains(
                "completed successfully",
                ignoreCase =
                    true
            )
        )
    }
}
