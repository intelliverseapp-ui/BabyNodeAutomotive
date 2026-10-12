package com.babynode.automotive

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Result returned to the voice or typed interface for one deliberate
 * user submission.
 *
 * A Submitted result provides the exact transport request identity
 * that the originating UI must use when filtering later events.
 */
sealed interface CarCommandDispatchResult {

    data class Submitted(
        val request: CarCommandRequest
    ) : CarCommandDispatchResult

    data class ConfirmationRequired(
        val command: CanonicalCommand
    ) : CarCommandDispatchResult

    data class OutcomeUnknown(
        val request: CarCommandRequest,
        val message: String
    ) : CarCommandDispatchResult

    data class Rejected(
        val reason: String
    ) : CarCommandDispatchResult

    data class Failed(
        val message: String
    ) : CarCommandDispatchResult
}

/**
 * CarCommandDispatcher
 *
 * Pipeline:
 *
 * Natural-language request
 *      ↓
 * CarCommandMap
 *      ↓
 * Approved canonical command
 *      ↓
 * CarCanTransport
 *      ↓
 * BabyNodeCAN
 *
 * Android sends canonical commands only.
 * BabyNodeCAN owns vehicle-specific CAN mapping.
 */
class CarCommandDispatcher(
    private val scope: CoroutineScope,
    private val transport: CarCanTransport
) {

    @Volatile
    private var pendingProtocolHandshakeRequest:
        CarCommandRequest? = null

    @Volatile
    private var protocolHandshakeReady =
        false

    @Volatile
    private var pendingModuleConfigRequest:
        CarCommandRequest? = null

    @Volatile
    private var moduleConfigurationReady =
        false

    @Volatile
    private var pendingSensitiveConfirmation:
        CarCommandDispatchResult.ConfirmationRequired? = null

    companion object {
        private const val TAG =
            "CarCommandDispatcher"

        private const val PROTOCOL_HANDSHAKE_COMMAND =
            "protocol.handshake"

        private const val MODULE_CONFIG_COMMAND =
            "config.module"

        private val SENSITIVE_COMMANDS =
            setOf(
                "UNLOCK_DOORS",
                "TRUNK_OPEN"
            )

        private const val REJECTION_EMPTY =
            "empty_request"

        private const val REJECTION_UNKNOWN =
            "unknown_command"

        private const val REJECTION_NEGATED =
            "negated_command"

        private const val REJECTION_OUT_OF_SCOPE =
            "out_of_scope_command"

        private const val REJECTION_INVALID_CANONICAL =
            "invalid_canonical_command"

        private const val REJECTION_MODULE_NOT_CONFIGURED =
            "module_not_configured"

        private val CANONICAL_COMMAND_PATTERN =
            Regex(
                pattern =
                    """^[A-Z][A-Z0-9_]{1,63}$"""
            )
    }

    /**
     * Sends the protocol handshake required before module
     * configuration.
     */
    fun sendProtocolHandshake(
        onResult:
            (CarCommandDispatchResult) -> Unit = {}
    ) {
        protocolHandshakeReady =
            false

        pendingProtocolHandshakeRequest =
            null

        moduleConfigurationReady =
            false

        pendingModuleConfigRequest =
            null

        scope.launch {
            try {
                val request =
                    transport.sendCommand(
                        CanonicalCommand(
                            command =
                                PROTOCOL_HANDSHAKE_COMMAND
                        )
                    )

                pendingProtocolHandshakeRequest =
                    request

                onResult(
                    CarCommandDispatchResult.Submitted(
                        request =
                            request
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(
                    CarCommandDispatchResult.Failed(
                        message =
                            "Protocol handshake request failed: " +
                                (
                                    e.message
                                        ?: e.javaClass.simpleName
                                )
                    )
                )
            }
        }
    }

    /**
     * Sends the selected BabyNodeCAN module configuration.
     *
     * This is an internal protocol request. Its returned request ID
     * is intentionally not exposed to the voice or typed interfaces,
     * so its response cannot be treated as a user-command result.
     */
    fun sendModuleConfig(
        moduleJsonId: String,
        onResult:
            (CarCommandDispatchResult) -> Unit = {}
    ) {
        if (!protocolHandshakeReady) {
            Log.w(
                TAG,
                "Module configuration rejected before protocol handshake"
            )

            onResult(
                CarCommandDispatchResult.Rejected(
                    reason =
                        "protocol_handshake_required"
                )
            )

            return
        }

        moduleConfigurationReady =
            false

        pendingModuleConfigRequest =
            null

        val normalizedModuleId =
            moduleJsonId
                .trim()
                .lowercase()

        if (
            !BabyNodePeerContract.supportsModule(
                normalizedModuleId
            )
        ) {
            Log.e(
                TAG,
                "Unsupported module configuration rejected: " +
                    normalizedModuleId
            )

            onResult(
                CarCommandDispatchResult.Rejected(
                    reason =
                        "unsupported_module_configuration"
                )
            )

            return
        }

        scope.launch {
            try {
                val request =
                    transport.sendCommand(
                        CanonicalCommand(
                            command =
                                MODULE_CONFIG_COMMAND,
                            value =
                                normalizedModuleId
                        )
                    )

                Log.i(
                    TAG,
                    "Module configuration request submitted: " +
                        "id=${request.requestId}"
                )

                pendingModuleConfigRequest =
                    request

                onResult(
                    CarCommandDispatchResult.Submitted(
                        request =
                            request
                    )
                )
            } catch (e: CancellationException) {
                Log.i(
                    TAG,
                    "Module configuration request cancelled"
                )

                throw e
            } catch (e: Exception) {
                val message =
                    "Module configuration request failed: " +
                        (
                            e.message
                                ?: e.javaClass.simpleName
                        )

                Log.e(
                    TAG,
                    message,
                    e
                )

                onResult(
                    CarCommandDispatchResult.Failed(
                        message =
                            message
                    )
                )
            }
        }
    }

    /**
     * Accepts transport events used to validate the current
     * module-configuration request.
     */
    fun acceptTransportEvent(
        event: CarStatusEvent
    ) {
        if (event is CarStatusEvent.Disconnected) {
            protocolHandshakeReady =
                false

            pendingProtocolHandshakeRequest =
                null

            moduleConfigurationReady =
                false

            pendingModuleConfigRequest =
                null

            pendingSensitiveConfirmation =
                null

            return
        }

        val handshakeRequest =
            pendingProtocolHandshakeRequest

        val moduleRequest =
            pendingModuleConfigRequest

        when (event) {
            is CarStatusEvent.CommandResponse -> {
                if (
                    handshakeRequest != null &&
                    event.requestId ==
                    handshakeRequest.requestId &&
                    event.command ==
                    handshakeRequest.command
                ) {
                    protocolHandshakeReady =
                        event.status.equals(
                            "ok",
                            ignoreCase = true
                        )

                    pendingProtocolHandshakeRequest =
                        null

                    return
                }

                if (
                    moduleRequest != null &&
                    event.requestId ==
                    moduleRequest.requestId &&
                    event.command ==
                    moduleRequest.command
                ) {
                    moduleConfigurationReady =
                        event.status.equals(
                            "ok",
                            ignoreCase = true
                        )

                    pendingModuleConfigRequest =
                        null
                }
            }

            is CarStatusEvent.Error -> {
                if (
                    handshakeRequest != null &&
                    event.requestId ==
                    handshakeRequest.requestId &&
                    event.command ==
                    handshakeRequest.command
                ) {
                    protocolHandshakeReady =
                        false

                    pendingProtocolHandshakeRequest =
                        null

                    return
                }

                if (
                    moduleRequest != null &&
                    event.requestId ==
                    moduleRequest.requestId &&
                    event.command ==
                    moduleRequest.command
                ) {
                    moduleConfigurationReady =
                        false

                    pendingModuleConfigRequest =
                        null
                }
            }

            is CarStatusEvent.Disconnected -> {
                // Handled above.
            }

            is CarStatusEvent.DeviceApprovalRequired,
            is CarStatusEvent.Connected,
            is CarStatusEvent.CommandSent -> {
                // No protocol-state transition.
            }
        }
    }

    /**
     * Maps and submits one deliberate user request.
     *
     * The result callback is invoked with:
     *
     * - Submitted, containing the exact request ID and canonical
     *   command after the transport write succeeds
     * - Rejected, when mapping or safety validation rejects the text
     * - Failed, when the approved command cannot be transmitted
     *
     * This function never claims that a physical vehicle action
     * completed.
     */
    fun handle(
        text: String,
        onResult:
            (CarCommandDispatchResult) -> Unit
    ) {
        /*
         * Every new user submission invalidates any older sensitive
         * command that was awaiting confirmation.
         */
        pendingSensitiveConfirmation =
            null

        val normalizedInput =
            text.trim()

        if (normalizedInput.isBlank()) {
            Log.w(
                TAG,
                "Empty command request rejected"
            )

            onResult(
                CarCommandDispatchResult.Rejected(
                    reason =
                        REJECTION_EMPTY
                )
            )

            return
        }

        if (!moduleConfigurationReady) {
            Log.w(
                TAG,
                "Vehicle command rejected because module " +
                    "configuration is not acknowledged"
            )

            onResult(
                CarCommandDispatchResult.Rejected(
                    reason =
                        REJECTION_MODULE_NOT_CONFIGURED
                )
            )

            return
        }

        val canonicalCommand =
            CarCommandMap.map(
                normalizedInput
            )

        when (canonicalCommand) {
            CarCommandMap.UNKNOWN_COMMAND -> {
                Log.w(
                    TAG,
                    "Unknown automotive request rejected"
                )

                onResult(
                    CarCommandDispatchResult.Rejected(
                        reason =
                            REJECTION_UNKNOWN
                    )
                )

                return
            }

            CarCommandMap.IGNORED_NEGATED_COMMAND -> {
                Log.w(
                    TAG,
                    "Negated automotive request rejected"
                )

                onResult(
                    CarCommandDispatchResult.Rejected(
                        reason =
                            REJECTION_NEGATED
                    )
                )

                return
            }

            CarCommandMap.BLOCKED_OUT_OF_SCOPE_COMMAND -> {
                Log.w(
                    TAG,
                    "Out-of-scope automotive request rejected"
                )

                onResult(
                    CarCommandDispatchResult.Rejected(
                        reason =
                            REJECTION_OUT_OF_SCOPE
                    )
                )

                return
            }
        }

        if (
            !isValidCanonicalCommand(
                canonicalCommand
            )
        ) {
            Log.e(
                TAG,
                "Canonical-command safety validation failed"
            )

            onResult(
                CarCommandDispatchResult.Rejected(
                    reason =
                        REJECTION_INVALID_CANONICAL
                )
            )

            return
        }

        val command =
            CanonicalCommand(
                command =
                    canonicalCommand
            )

        if (
            canonicalCommand in
            SENSITIVE_COMMANDS
        ) {
            Log.i(
                TAG,
                "Sensitive command requires explicit confirmation: " +
                    canonicalCommand
            )

            val confirmation =
                CarCommandDispatchResult.ConfirmationRequired(
                    command =
                        command
                )

            pendingSensitiveConfirmation =
                confirmation

            onResult(
                confirmation
            )

            return
        }

        Log.i(
            TAG,
            "Approved Phase 1 command awaiting transport write: " +
                canonicalCommand
        )

        scope.launch {
            try {
                val request =
                    transport.sendCommand(
                        command
                    )

                Log.i(
                    TAG,
                    "User command transmitted: " +
                        "id=${request.requestId}, " +
                        "command=${request.command.command}"
                )

                onResult(
                    CarCommandDispatchResult.Submitted(
                        request =
                            request
                    )
                )
            } catch (
                e: CarCommandOutcomeUnknownException
            ) {
                Log.w(
                    TAG,
                    "Command transmission outcome unknown: " +
                        "id=${e.request.requestId}",
                    e
                )

                onResult(
                    CarCommandDispatchResult.OutcomeUnknown(
                        request =
                            e.request,
                        message =
                            (
                                e.message
                                    ?: "Command outcome unknown"
                            )
                    )
                )
            } catch (e: CancellationException) {
                Log.i(
                    TAG,
                    "Canonical command request cancelled"
                )

                onResult(
                    CarCommandDispatchResult.Failed(
                        message =
                            "Command request cancelled"
                    )
                )

                throw e
            } catch (e: Exception) {
                val message =
                    "Command could not be transmitted: " +
                        (
                            e.message
                                ?: e.javaClass.simpleName
                        )

                Log.e(
                    TAG,
                    message,
                    e
                )

                onResult(
                    CarCommandDispatchResult.Failed(
                        message =
                            message
                    )
                )
            }
        }
    }

    /**
     * Cancels the exact sensitive command currently awaiting
     * confirmation.
     *
     * Returns true only when the supplied confirmation matches the
     * pending command and cancellation consumes it.
     */
    fun cancelSensitiveCommand(
        confirmation:
            CarCommandDispatchResult.ConfirmationRequired
    ): Boolean {
        val command =
            confirmation.command

        val pendingConfirmation =
            pendingSensitiveConfirmation

        if (
            pendingConfirmation == null ||
            pendingConfirmation !== confirmation
        ) {
            Log.w(
                TAG,
                "Invalid or stale sensitive-command cancellation rejected"
            )

            return false
        }

        pendingSensitiveConfirmation =
            null

        Log.i(
            TAG,
            "Sensitive command cancelled before transport: " +
                command.command
        )

        return true
    }

    /**
     * Transmits one sensitive command after deliberate confirmation.
     *
     * Only the exact command most recently returned through
     * ConfirmationRequired is accepted. Confirmation is consumed
     * before the transport write begins.
     */
    fun confirmSensitiveCommand(
        confirmation:
            CarCommandDispatchResult.ConfirmationRequired,
        onResult:
            (CarCommandDispatchResult) -> Unit
    ) {
        val command =
            confirmation.command

        val pendingConfirmation =
            pendingSensitiveConfirmation

        if (
            pendingConfirmation == null ||
            pendingConfirmation !== confirmation ||
            command.command !in SENSITIVE_COMMANDS ||
            !CarCommandMap.isPhaseOneCommand(
                command.command
            )
        ) {
            Log.w(
                TAG,
                "Invalid or stale sensitive-command confirmation rejected"
            )

            onResult(
                CarCommandDispatchResult.Rejected(
                    reason =
                        "invalid_confirmation"
                )
            )

            return
        }

        if (!moduleConfigurationReady) {
            pendingSensitiveConfirmation =
                null

            Log.w(
                TAG,
                "Sensitive-command confirmation rejected because " +
                    "module configuration is not acknowledged"
            )

            onResult(
                CarCommandDispatchResult.Rejected(
                    reason =
                        REJECTION_MODULE_NOT_CONFIGURED
                )
            )

            return
        }

        pendingSensitiveConfirmation =
            null

        scope.launch {
            try {
                val request =
                    transport.sendCommand(
                        command
                    )

                Log.i(
                    TAG,
                    "Confirmed sensitive command transmitted: " +
                        "id=${request.requestId}, " +
                        "command=${request.command.command}"
                )

                onResult(
                    CarCommandDispatchResult.Submitted(
                        request =
                            request
                    )
                )
            } catch (
                e: CarCommandOutcomeUnknownException
            ) {
                Log.w(
                    TAG,
                    "Confirmed command outcome unknown: " +
                        "id=${e.request.requestId}",
                    e
                )

                onResult(
                    CarCommandDispatchResult.OutcomeUnknown(
                        request =
                            e.request,
                        message =
                            (
                                e.message
                                    ?: "Confirmed command outcome unknown"
                            )
                    )
                )
            } catch (e: CancellationException) {
                Log.i(
                    TAG,
                    "Confirmed sensitive command request cancelled"
                )

                onResult(
                    CarCommandDispatchResult.Failed(
                        message =
                            "Confirmed command request cancelled"
                    )
                )

                throw e
            } catch (e: Exception) {
                val message =
                    "Confirmed sensitive command failed: " +
                        (
                            e.message
                                ?: e.javaClass.simpleName
                        )

                Log.e(
                    TAG,
                    message,
                    e
                )

                onResult(
                    CarCommandDispatchResult.Failed(
                        message =
                            message
                    )
                )
            }
        }
    }

    /**
     * Compatibility entry point for call sites that do not yet need
     * the returned request identity.
     *
     * Voice and typed interfaces must use the callback overload above
     * so they can correlate their own transport events.
     */
    fun handle(
        text: String
    ) {
        handle(
            text =
                text,
            onResult = { result ->
                when (result) {
                    is CarCommandDispatchResult.Submitted -> {
                        Log.i(
                            TAG,
                            "Compatibility submission completed: " +
                                "id=${result.request.requestId}"
                        )
                    }

                    is CarCommandDispatchResult.ConfirmationRequired -> {
                        Log.w(
                            TAG,
                            "Compatibility submission stopped for " +
                                "explicit confirmation: " +
                                result.command.command
                        )
                    }

                    is CarCommandDispatchResult.OutcomeUnknown -> {
                        Log.w(
                            TAG,
                            "Compatibility submission outcome unknown: " +
                                "id=${result.request.requestId}"
                        )
                    }

                    is CarCommandDispatchResult.Rejected -> {
                        Log.w(
                            TAG,
                            "Compatibility submission rejected: " +
                                result.reason
                        )
                    }

                    is CarCommandDispatchResult.Failed -> {
                        Log.e(
                            TAG,
                            result.message
                        )
                    }
                }
            }
        )
    }

    /**
     * Defense-in-depth validation.
     *
     * CarCommandMap already enforces the Phase 1 allowlist. This
     * check prevents sentinel values, protocol commands, and malformed
     * canonical names from reaching the Bluetooth transport.
     */
    private fun isValidCanonicalCommand(
        command: String
    ): Boolean {
        if (command.isBlank()) {
            return false
        }

        if (
            command == CarCommandMap.UNKNOWN_COMMAND ||
            command == CarCommandMap.IGNORED_NEGATED_COMMAND ||
            command == CarCommandMap.BLOCKED_OUT_OF_SCOPE_COMMAND
        ) {
            return false
        }

        if (
            command.equals(
                MODULE_CONFIG_COMMAND,
                ignoreCase = true
            )
        ) {
            return false
        }

        if (
            !CarCommandMap.isPhaseOneCommand(
                command
            )
        ) {
            return false
        }

        return CANONICAL_COMMAND_PATTERN.matches(
            command
        )
    }
}