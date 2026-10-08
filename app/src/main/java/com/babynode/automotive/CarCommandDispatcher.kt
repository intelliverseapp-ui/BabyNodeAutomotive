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

    companion object {
        private const val TAG =
            "CarCommandDispatcher"

        private const val MODULE_CONFIG_COMMAND =
            "config.module"

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

        private val CANONICAL_COMMAND_PATTERN =
            Regex(
                pattern =
                    """^[A-Z][A-Z0-9_]{1,63}$"""
            )
    }

    /**
     * Sends the selected BabyNodeCAN module configuration.
     *
     * This is an internal protocol request. Its returned request ID
     * is intentionally not exposed to the voice or typed interfaces,
     * so its response cannot be treated as a user-command result.
     */
    fun sendModuleConfig(
        moduleJsonId: String
    ) {
        val normalizedModuleId =
            moduleJsonId
                .trim()
                .lowercase()

        if (
            normalizedModuleId != "single" &&
            normalizedModuleId != "dual"
        ) {
            Log.e(
                TAG,
                "Invalid module configuration rejected"
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
            } catch (e: CancellationException) {
                Log.i(
                    TAG,
                    "Module configuration request cancelled"
                )

                throw e
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Module configuration request failed",
                    e
                )
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

        return CANONICAL_COMMAND_PATTERN.matches(
            command
        )
    }
}