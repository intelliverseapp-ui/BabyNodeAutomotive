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


    @Test
    fun acceptsExpectedHandshakeCapabilities() {
        assertTrue(
            BabyNodeProtocol.validateHandshakeCapabilities(
                sessionId =
                    42L,
                module =
                    "single",
                canTransmit =
                    false,
                vehicleCommands =
                    "stub_only"
            )
        )
    }

    @Test
    fun rejectsMissingHandshakeSessionIdentity() {
        assertFalse(
            BabyNodeProtocol.validateHandshakeCapabilities(
                sessionId =
                    null,
                module =
                    "single",
                canTransmit =
                    false,
                vehicleCommands =
                    "stub_only"
            )
        )
    }

    @Test
    fun rejectsZeroHandshakeSessionIdentity() {
        assertFalse(
            BabyNodeProtocol.validateHandshakeCapabilities(
                sessionId =
                    0L,
                module =
                    "single",
                canTransmit =
                    false,
                vehicleCommands =
                    "stub_only"
            )
        )
    }

    @Test
    fun rejectsOversizedHandshakeSessionIdentity() {
        assertFalse(
            BabyNodeProtocol.validateHandshakeCapabilities(
                sessionId =
                    BabyNodeProtocol.MAX_SESSION_ID +
                        1L,
                module =
                    "single",
                canTransmit =
                    false,
                vehicleCommands =
                    "stub_only"
            )
        )
    }

    @Test
    fun rejectsUnsupportedHandshakeModule() {
        assertFalse(
            BabyNodeProtocol.validateHandshakeCapabilities(
                sessionId =
                    42L,
                module =
                    "dual",
                canTransmit =
                    false,
                vehicleCommands =
                    "stub_only"
            )
        )
    }

    @Test
    fun rejectsHandshakeClaimingCanTransmission() {
        assertFalse(
            BabyNodeProtocol.validateHandshakeCapabilities(
                sessionId =
                    42L,
                module =
                    "single",
                canTransmit =
                    true,
                vehicleCommands =
                    "stub_only"
            )
        )
    }

    @Test
    fun rejectsUnsupportedVehicleCommandCapability() {
        assertFalse(
            BabyNodeProtocol.validateHandshakeCapabilities(
                sessionId =
                    42L,
                module =
                    "single",
                canTransmit =
                    false,
                vehicleCommands =
                    "implemented"
            )
        )
    }

}
