package com.babynode.automotive

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BabyNodeAppTest {

    private lateinit var app:
        BabyNodeApp

    @Before
    fun setUp() {
        app =
            ApplicationProvider.getApplicationContext()

        assertTrue(
            app.clearApprovedBabyNodeCanAddress()
        )
    }

    @After
    fun tearDown() {
        app.clearApprovedBabyNodeCanAddress()
    }

    @Test
    fun validAddressIsNormalizedAndPersisted() {
        assertTrue(
            app.approveBabyNodeCanAddress(
                " aa:bb:cc:dd:ee:ff "
            )
        )

        assertEquals(
            "AA:BB:CC:DD:EE:FF",
            app.approvedBabyNodeCanAddress()
        )
    }

    @Test
    fun malformedAddressesAreRejected() {
        val malformedAddresses =
            listOf(
                "",
                "not-an-address",
                "AA:BB:CC:DD:EE",
                "AA:BB:CC:DD:EE:GG",
                "AABBCCDDEEFF"
            )

        malformedAddresses.forEach { address ->
            assertFalse(
                address,
                app.approveBabyNodeCanAddress(
                    address
                )
            )
        }

        assertNull(
            app.approvedBabyNodeCanAddress()
        )
    }

    @Test
    fun forgettingApprovedDeviceRemovesPersistedIdentity() {
        assertTrue(
            app.approveBabyNodeCanAddress(
                "11:22:33:44:55:66"
            )
        )

        assertTrue(
            app.clearApprovedBabyNodeCanAddress()
        )

        assertNull(
            app.approvedBabyNodeCanAddress()
        )
    }
}
