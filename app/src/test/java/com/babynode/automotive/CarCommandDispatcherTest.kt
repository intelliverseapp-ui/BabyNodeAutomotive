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
    fun olderConfirmationCannotApproveNewerIdenticalCommand() {
        val transport =
            FakeTransport()

        val dispatcher =
            createDispatcher(
                transport
            )

        val configurationRequest =
            submitConfiguration(
                dispatcher =
                    dispatcher
            )

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId =
                    configurationRequest.requestId,
                command =
                    configurationRequest.command,
                status =
                    "ok"
            )
        )

        var firstResult:
            CarCommandDispatchResult? =
            null

        dispatcher.handle(
            text =
                "unlock the doors",
            onResult = {
                firstResult =
                    it
            }
        )

        val olderConfirmation =
            firstResult as
                CarCommandDispatchResult.ConfirmationRequired

        dispatcher.handle(
            text =
                "unlock the doors",
            onResult = {}
        )

        val commandsBeforeStaleConfirmation =
            transport.sentCommands.size

        var staleResult:
            CarCommandDispatchResult? =
            null

        dispatcher.confirmSensitiveCommand(
            confirmation =
                olderConfirmation,
            onResult = {
                staleResult =
                    it
            }
        )

        assertEquals(
            "invalid_confirmation",
            (
                staleResult as
                    CarCommandDispatchResult.Rejected
                ).reason
        )

        assertEquals(
            commandsBeforeStaleConfirmation,
            transport.sentCommands.size
        )
    }

    @Test
    fun reconstructedConfirmationIsRejected() {
        val transport =
            FakeTransport()

        val dispatcher =
            createDispatcher(
                transport
            )

        val configurationRequest =
            submitConfiguration(
                dispatcher =
                    dispatcher
            )

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId =
                    configurationRequest.requestId,
                command =
                    configurationRequest.command,
                status =
                    "ok"
            )
        )

        dispatcher.handle(
            text =
                "unlock the doors",
            onResult = {}
        )

        val reconstructedConfirmation =
            CarCommandDispatchResult.ConfirmationRequired(
                command =
                    CanonicalCommand(
                        command =
                            "UNLOCK_DOORS"
                    )
            )

        val commandsBeforeConfirmation =
            transport.sentCommands.size

        var result:
            CarCommandDispatchResult? =
            null

        dispatcher.confirmSensitiveCommand(
            confirmation =
                reconstructedConfirmation,
            onResult = {
                result =
                    it
            }
        )

        assertEquals(
            "invalid_confirmation",
            (
                result as
                    CarCommandDispatchResult.Rejected
                ).reason
        )

        assertEquals(
            commandsBeforeConfirmation,
            transport.sentCommands.size
        )
    }

    @Test
    fun newSubmissionInvalidatesOlderSensitiveConfirmation() {
        val transport =
            FakeTransport()

        val dispatcher =
            createDispatcher(
                transport
            )

        val configurationRequest =
            submitConfiguration(
                dispatcher =
                    dispatcher
            )

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId =
                    configurationRequest.requestId,
                command =
                    configurationRequest.command,
                status =
                    "ok"
            )
        )

        var sensitiveResult:
            CarCommandDispatchResult? =
            null

        dispatcher.handle(
            text =
                "unlock the doors",
            onResult = {
                sensitiveResult =
                    it
            }
        )

        val oldConfirmation =
            sensitiveResult as
                CarCommandDispatchResult.ConfirmationRequired

        dispatcher.handle(
            text =
                "lock the doors",
            onResult = {}
        )

        val commandsAfterNewSubmission =
            transport.sentCommands.size

        var staleResult:
            CarCommandDispatchResult? =
            null

        dispatcher.confirmSensitiveCommand(
            confirmation =
                oldConfirmation,
            onResult = {
                staleResult =
                    it
            }
        )

        assertEquals(
            "invalid_confirmation",
            (
                staleResult as
                    CarCommandDispatchResult.Rejected
                ).reason
        )

        assertEquals(
            commandsAfterNewSubmission,
            transport.sentCommands.size
        )
    }

    @Test
    fun cancelledSensitiveCommandCannotBeConfirmed() {
        val transport =
            FakeTransport()

        val dispatcher =
            createDispatcher(
                transport
            )

        val configurationRequest =
            submitConfiguration(
                dispatcher =
                    dispatcher
            )

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId =
                    configurationRequest.requestId,
                command =
                    configurationRequest.command,
                status =
                    "ok"
            )
        )

        var initialResult:
            CarCommandDispatchResult? =
            null

        dispatcher.handle(
            text =
                "unlock the doors",
            onResult = {
                initialResult =
                    it
            }
        )

        val confirmation =
            initialResult as
                CarCommandDispatchResult.ConfirmationRequired

        val commandsBeforeCancellation =
            transport.sentCommands.size

        assertTrue(
            dispatcher.cancelSensitiveCommand(
                confirmation =
                    confirmation
            )
        )

        var confirmationResult:
            CarCommandDispatchResult? =
            null

        dispatcher.confirmSensitiveCommand(
            confirmation =
                confirmation,
            onResult = {
                confirmationResult =
                    it
            }
        )

        assertEquals(
            "invalid_confirmation",
            (
                confirmationResult as
                    CarCommandDispatchResult.Rejected
                ).reason
        )

        assertEquals(
            commandsBeforeCancellation,
            transport.sentCommands.size
        )
    }

    @Test
    fun sensitiveCommandConfirmationCannotBeReplayed() {
        val transport =
            FakeTransport()

        val dispatcher =
            createDispatcher(
                transport
            )

        val configurationRequest =
            submitConfiguration(
                dispatcher =
                    dispatcher
            )

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId =
                    configurationRequest.requestId,
                command =
                    configurationRequest.command,
                status =
                    "ok"
            )
        )

        var initialResult:
            CarCommandDispatchResult? =
            null

        dispatcher.handle(
            text =
                "unlock the doors",
            onResult = {
                initialResult =
                    it
            }
        )

        val confirmation =
            initialResult as
                CarCommandDispatchResult.ConfirmationRequired

        dispatcher.confirmSensitiveCommand(
            confirmation =
                confirmation,
            onResult = {}
        )

        val commandsAfterFirstConfirmation =
            transport.sentCommands.size

        var replayResult:
            CarCommandDispatchResult? =
            null

        dispatcher.confirmSensitiveCommand(
            confirmation =
                confirmation,
            onResult = {
                replayResult =
                    it
            }
        )

        assertEquals(
            "invalid_confirmation",
            (
                replayResult as
                    CarCommandDispatchResult.Rejected
                ).reason
        )

        assertEquals(
            commandsAfterFirstConfirmation,
            transport.sentCommands.size
        )
    }

    @Test
    fun confirmedSensitiveCommandReachesTransportExactlyOnce() {
        val transport =
            FakeTransport()

        val dispatcher =
            createDispatcher(
                transport
            )

        val configurationRequest =
            submitConfiguration(
                dispatcher =
                    dispatcher
            )

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId =
                    configurationRequest.requestId,
                command =
                    configurationRequest.command,
                status =
                    "ok"
            )
        )

        var initialResult:
            CarCommandDispatchResult? =
            null

        dispatcher.handle(
            text =
                "unlock the doors",
            onResult = {
                initialResult =
                    it
            }
        )

        val confirmation =
            initialResult as
                CarCommandDispatchResult.ConfirmationRequired

        val commandsBeforeConfirmation =
            transport.sentCommands.size

        var confirmedResult:
            CarCommandDispatchResult? =
            null

        dispatcher.confirmSensitiveCommand(
            confirmation =
                confirmation,
            onResult = {
                confirmedResult =
                    it
            }
        )

        assertTrue(
            confirmedResult is
                CarCommandDispatchResult.Submitted
        )

        assertEquals(
            commandsBeforeConfirmation +
                1,
            transport.sentCommands.size
        )

        assertEquals(
            "UNLOCK_DOORS",
            transport.sentCommands.last().command
        )
    }

    @Test
    fun sensitiveCommandRequiresConfirmationBeforeTransport() {
        val transport =
            FakeTransport()

        val dispatcher =
            createDispatcher(
                transport
            )

        val configurationRequest =
            submitConfiguration(
                dispatcher =
                    dispatcher
            )

        dispatcher.acceptTransportEvent(
            CarStatusEvent.CommandResponse(
                requestId =
                    configurationRequest.requestId,
                command =
                    configurationRequest.command,
                status =
                    "ok"
            )
        )

        val commandsBefore =
            transport.sentCommands.size

        var result:
            CarCommandDispatchResult? =
            null

        dispatcher.handle(
            text =
                "unlock the doors",
            onResult = {
                result =
                    it
            }
        )

        val confirmation =
            result as
                CarCommandDispatchResult.ConfirmationRequired

        assertEquals(
            "UNLOCK_DOORS",
            confirmation.command.command
        )

        assertEquals(
            commandsBefore,
            transport.sentCommands.size
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
