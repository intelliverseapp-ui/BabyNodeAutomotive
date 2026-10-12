package com.babynode.automotive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketGenerationTest {

    @Test
    fun newlyAllocatedGenerationBecomesCurrent() {
        val generations =
            SocketGeneration()

        val generation =
            generations.advance()

        assertTrue(
            generations.isCurrent(
                generation
            )
        )
    }

    @Test
    fun advancingInvalidatesPriorSocketGeneration() {
        val generations =
            SocketGeneration()

        val oldGeneration =
            generations.advance()

        val newGeneration =
            generations.advance()

        assertFalse(
            generations.isCurrent(
                oldGeneration
            )
        )

        assertTrue(
            generations.isCurrent(
                newGeneration
            )
        )
    }

    @Test
    fun invalidationMakesCurrentReceiveLoopStale() {
        val generations =
            SocketGeneration()

        val activeGeneration =
            generations.advance()

        generations.invalidate()

        assertFalse(
            generations.isCurrent(
                activeGeneration
            )
        )
    }

    @Test
    fun staleGenerationCannotBecomeCurrentAgain() {
        val generations =
            SocketGeneration()

        val first =
            generations.advance()

        generations.invalidate()

        val second =
            generations.advance()

        assertFalse(
            generations.isCurrent(
                first
            )
        )

        assertTrue(
            generations.isCurrent(
                second
            )
        )
    }
}
