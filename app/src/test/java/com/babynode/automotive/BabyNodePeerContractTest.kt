package com.babynode.automotive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BabyNodePeerContractTest {

    @Test
    fun supportsCurrentSingleModuleStubContract() {
        assertTrue(
            BabyNodePeerContract.supportsModule(
                "single"
            )
        )

        assertTrue(
            BabyNodePeerContract.validateCapabilities(
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
    fun rejectsUnimplementedDualModuleContract() {
        assertFalse(
            BabyNodePeerContract.supportsModule(
                "dual"
            )
        )

        assertFalse(
            BabyNodePeerContract.validateCapabilities(
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
    fun rejectsUnapprovedActuatorCapability() {
        assertFalse(
            BabyNodePeerContract.validateCapabilities(
                sessionId =
                    42L,
                module =
                    "single",
                canTransmit =
                    true,
                vehicleCommands =
                    "implemented"
            )
        )
    }

    @Test
    fun currentContractDoesNotAuthorizeVehicleActuation() {
        assertFalse(
            BabyNodePeerContract.allowsVehicleActuation
        )
    }
}
