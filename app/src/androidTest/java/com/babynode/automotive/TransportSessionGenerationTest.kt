package com.babynode.automotive

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransportSessionGenerationTest {

    @Test
    fun consecutiveAllocationsArePositiveAndIncreasing() {
        val app =
            ApplicationProvider
                .getApplicationContext<BabyNodeApp>()

        val first =
            app.allocateTransportSessionGeneration()

        val second =
            app.allocateTransportSessionGeneration()

        assertTrue(
            first > 0L
        )

        assertEquals(
            first + 1L,
            second
        )
    }

    @Test
    fun newlyConstructedTransportsReceiveDistinctGenerations() {
        val firstTransport =
            CarCanBusBluetooth()

        val secondTransport =
            CarCanBusBluetooth()

        try {
            assertTrue(
                firstTransport.sessionGeneration >
                    0L
            )

            assertTrue(
                secondTransport.sessionGeneration >
                    firstTransport.sessionGeneration
            )
        } finally {
            firstTransport.close()
            secondTransport.close()
        }
    }
}
