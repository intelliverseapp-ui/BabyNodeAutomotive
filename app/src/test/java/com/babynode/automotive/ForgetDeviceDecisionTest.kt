package com.babynode.automotive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgetDeviceDecisionTest {

    @Test
    fun successfulRemovalAllowsTransportReplacement() {
        val decision =
            ForgetDeviceDecision.fromPersistenceResult(
                identityRemoved =
                    true
            )

        assertTrue(
            decision.identityForgotten
        )

        assertTrue(
            decision.replaceTransport
        )

        assertFalse(
            decision.showPersistenceError
        )
    }

    @Test
    fun failedRemovalPreventsTransportReplacement() {
        val decision =
            ForgetDeviceDecision.fromPersistenceResult(
                identityRemoved =
                    false
            )

        assertFalse(
            decision.identityForgotten
        )

        assertFalse(
            decision.replaceTransport
        )

        assertTrue(
            decision.showPersistenceError
        )
    }
}
