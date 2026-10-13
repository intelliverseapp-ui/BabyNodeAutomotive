package com.babynode.automotive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionGenerationTest {

    @Test
    fun currentGenerationRemainsValid() {
        val generations =
            ConnectionGeneration()

        val current =
            generations.current()

        assertTrue(
            generations.isCurrent(
                current
            )
        )
    }

    @Test
    fun advancingInvalidatesOlderWork() {
        val generations =
            ConnectionGeneration()

        val older =
            generations.current()

        val newer =
            generations.advance()

        assertFalse(
            generations.isCurrent(
                older
            )
        )

        assertTrue(
            generations.isCurrent(
                newer
            )
        )
    }

    @Test
    fun everyAdvanceInvalidatesPreviousGeneration() {
        val generations =
            ConnectionGeneration()

        val first =
            generations.advance()

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
