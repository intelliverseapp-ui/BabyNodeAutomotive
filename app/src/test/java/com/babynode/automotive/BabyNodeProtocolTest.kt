package com.babynode.automotive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BabyNodeProtocolTest {

    @Test
    fun acceptsExpectedProtocolVersion() {
        assertTrue(
            BabyNodeProtocol.validateVersion(
                1
            )
        )
    }

    @Test
    fun rejectsMissingProtocolVersion() {
        assertFalse(
            BabyNodeProtocol.validateVersion(
                null
            )
        )
    }

    @Test
    fun rejectsWrongProtocolVersion() {
        assertFalse(
            BabyNodeProtocol.validateVersion(
                2
            )
        )
    }

    @Test
    fun rejectsStringProtocolVersion() {
        assertFalse(
            BabyNodeProtocol.validateVersion(
                "1"
            )
        )
    }

    @Test
    fun rejectsFractionalProtocolVersion() {
        assertFalse(
            BabyNodeProtocol.validateVersion(
                1.0
            )
        )
    }

    @Test
    fun acceptsValidRequestId() {
        assertTrue(
            BabyNodeProtocol.validateRequestId(
                1L
            )
        )
    }

    @Test
    fun rejectsZeroRequestId() {
        assertFalse(
            BabyNodeProtocol.validateRequestId(
                0L
            )
        )
    }

    @Test
    fun rejectsNegativeRequestId() {
        assertFalse(
            BabyNodeProtocol.validateRequestId(
                -1L
            )
        )
    }

    @Test
    fun rejectsFractionalRequestId() {
        assertFalse(
            BabyNodeProtocol.validateRequestId(
                1.5
            )
        )
    }

    @Test
    fun rejectsStringRequestId() {
        assertFalse(
            BabyNodeProtocol.validateRequestId(
                "1"
            )
        )
    }

    @Test
    fun rejectsOversizedRequestId() {
        assertFalse(
            BabyNodeProtocol.validateRequestId(
                BabyNodeProtocol.MAX_REQUEST_ID +
                    1L
            )
        )
    }

}
