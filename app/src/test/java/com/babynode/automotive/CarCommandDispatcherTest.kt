package com.babynode.automotive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarCommandDispatcherTest {



    @Test
    fun moduleConfigurationIsBlockedBeforeHandshakeAcknowledgment() {
        val transport =
            FakeTransport()

        val dispatcher =
            createDispatcher(
                transport
            )

        var result:
            CarCommandDispatchResult? =
            null

        dispatcher.sendModuleConfig(
            moduleJsonId =
                "single",
            onResult = {
                result =
                    it
            }
        )

        assertEquals(
            "protocol_handshake_required",
            (
                result as
                    CarCommandDispatchResult.Rejected
                ).reason
        )

        assertTrue(
            transport.sentCommands.isEmpty()
        )
    }

    @Test
    fun handshakeMustBeAcknowledgedBeforeModuleConfiguration() {
        val transport =
            FakeTransport()

        val dispatcher =
            createDispatcher(
                transport
            )

        val handshakeRequest =
            submitHandshake(
                dispatcher =
                    dispatcher
            )

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId =
                    handshakeRequest.requestId,
                command =
                    handshakeRequest.command,
                status =
                    "ok"
            )
        )

        var configurationResult:
            CarCommandDispatchResult? =
            null

        dispatcher.sendModuleConfig(
            moduleJsonId =
                "single",
            onResult = {
                configurationResult =
                    it
            }
        )

        assertTrue(
            configurationResult is
                CarCommandDispatchResult.Submitted
        )

        assertEquals(
            listOf(
                "protocol.handshake",
                "config.module"
            ),
            transport
                .sentCommands
                .map {
                    it.command
                }
        )
    }

    @Test
    fun commandIsBlockedBeforeModuleAcknowledgment() {
        val transport = FakeTransport()
        val dispatcher = createDispatcher(transport)

        var result: CarCommandDispatchResult? = null

        dispatcher.handle(
            text = "lock the doors",
            onResult = {
                result = it
            }
        )

        assertEquals(
            "module_not_configured",
            (result as CarCommandDispatchResult.Rejected).reason
        )

        assertTrue(
            transport.sentCommands.isEmpty()
        )
    }

    @Test
    fun matchingOkAcknowledgmentAllowsVehicleCommand() {
        val transport = FakeTransport()
        val dispatcher = createDispatcher(transport)

        val configRequest =
            submitConfiguration(
                dispatcher = dispatcher
            )

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId = configRequest.requestId,
                command = configRequest.command,
                status = "ok"
            )
        )

        var result: CarCommandDispatchResult? = null

        dispatcher.handle(
            text = "lock the doors",
            onResult = {
                result = it
            }
        )

        assertTrue(
            result is CarCommandDispatchResult.Submitted
        )

        assertEquals(
            "LOCK_DOORS",
            transport.sentCommands.last().command
        )
    }

    @Test
    fun unrelatedResponseDoesNotUnlockVehicleCommands() {
        val transport = FakeTransport()
        val dispatcher = createDispatcher(transport)

        val configRequest =
            submitConfiguration(
                dispatcher = dispatcher
            )

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId = configRequest.requestId + 1L,
                command = configRequest.command,
                status = "ok"
            )
        )

        assertCommandRemainsBlocked(
            dispatcher = dispatcher,
            transport = transport
        )
    }

    @Test
    fun nonOkConfigurationResponseKeepsCommandsBlocked() {
        val transport = FakeTransport()
        val dispatcher = createDispatcher(transport)

        val configRequest =
            submitConfiguration(
                dispatcher = dispatcher
            )

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId = configRequest.requestId,
                command = configRequest.command,
                status = "error"
            )
        )

        assertCommandRemainsBlocked(
            dispatcher = dispatcher,
            transport = transport
        )
    }

    @Test
    fun configurationErrorKeepsCommandsBlocked() {
        val transport = FakeTransport()
        val dispatcher = createDispatcher(transport)

        val configRequest =
            submitConfiguration(
                dispatcher = dispatcher
            )

        dispatcher.acceptTransportEvent(
            CarStatusEvent.Error(
                message = "Configuration failed",
                requestId = configRequest.requestId,
                command = configRequest.command
            )
        )

        assertCommandRemainsBlocked(
            dispatcher = dispatcher,
            transport = transport
        )
    }

    @Test
    fun disconnectAfterAcknowledgmentBlocksCommandsAgain() {
        val transport = FakeTransport()
        val dispatcher = createDispatcher(transport)

        val configRequest =
            submitConfiguration(
                dispatcher = dispatcher
            )

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId = configRequest.requestId,
                command = configRequest.command,
                status = "ok"
            )
        )

        dispatcher.acceptTransportEvent(
            CarStatusEvent.Disconnected(
                transportName = "Bluetooth"
            )
        )

        assertCommandRemainsBlocked(
            dispatcher = dispatcher,
            transport = transport
        )
    }

    private fun createDispatcher(
        transport: FakeTransport
    ): CarCommandDispatcher {
        return CarCommandDispatcher(
            scope =
                CoroutineScope(
                    Dispatchers.Unconfined
                ),
            transport =
                transport
        )
    }


    private fun submitHandshake(
        dispatcher: CarCommandDispatcher
    ): CarCommandRequest {
        var result:
            CarCommandDispatchResult? =
            null

        dispatcher.sendProtocolHandshake(
            onResult = {
                result =
                    it
            }
        )

        return (
            result as
                CarCommandDispatchResult.Submitted
            ).request
    }

    private fun submitConfiguration(
        dispatcher: CarCommandDispatcher
    ): CarCommandRequest {
        val handshakeRequest =
            submitHandshake(
                dispatcher =
                    dispatcher
            )

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId =
                    handshakeRequest.requestId,
                command =
                    handshakeRequest.command,
                status =
                    "ok"
            )
        )

        var result: CarCommandDispatchResult? = null

        dispatcher.sendModuleConfig(
            moduleJsonId = "single",
            onResult = {
                result = it
            }
        )

        return (
            result as
                CarCommandDispatchResult.Submitted
            ).request
    }

    private fun assertCommandRemainsBlocked(
        dispatcher: CarCommandDispatcher,
        transport: FakeTransport
    ) {
        val commandsBefore =
            transport.sentCommands.size

        var result: CarCommandDispatchResult? = null

        dispatcher.handle(
            text = "lock the doors",
            onResult = {
                result = it
            }
        )

        assertEquals(
            "module_not_configured",
            (result as CarCommandDispatchResult.Rejected).reason
        )

        assertEquals(
            commandsBefore,
            transport.sentCommands.size
        )
    }

    private class FakeTransport :
        CarCanTransport {

        val sentCommands =
            mutableListOf<CanonicalCommand>()

        private var nextRequestId =
            1L

        override suspend fun connect() {
        }

        override suspend fun disconnect() {
        }

        override suspend fun sendCommand(
            command: CanonicalCommand
        ): CarCommandRequest {
            sentCommands +=
                command

            return CarCommandRequest(
                requestId =
                    nextRequestId++,
                command =
                    command
            )
        }

        override fun status():
            Flow<CarStatusEvent> {
            return emptyFlow()
        }

        override fun close() {
        }
    }
}
