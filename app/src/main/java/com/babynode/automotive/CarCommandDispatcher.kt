package com.babynode.automotive

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

        private val CANONICAL_COMMAND_PATTERN =
            Regex(
                pattern =
                    """^[A-Z][A-Z0-9_]{1,63}$"""
            )
    }

    /**
     * Sends the selected BabyNodeCAN module configuration.
     *
     * This is a protocol configuration command rather than a
     * user-operated vehicle command.
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
                    "Module configuration request submitted"
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
     * Maps natural-language input and submits it only when the
     * resulting command is approved for Phase 1.
     *
     * This function does not claim that the vehicle physically
     * executed the request. Transport and acknowledgment status
     * are reported through CarStatusEvent.
     */
    fun handle(
        text: String
    ) {
        val normalizedInput =
            text.trim()

        if (normalizedInput.isBlank()) {
            Log.w(
                TAG,
                "Empty command request rejected"
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

                return
            }

            CarCommandMap.IGNORED_NEGATED_COMMAND -> {
                Log.w(
                    TAG,
                    "Negated automotive request rejected"
                )

                return
            }

            CarCommandMap.BLOCKED_OUT_OF_SCOPE_COMMAND -> {
                Log.w(
                    TAG,
                    "Out-of-scope automotive request rejected"
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

            return
        }

        Log.i(
            TAG,
            "Approved Phase 1 command submitted: " +
                canonicalCommand
        )

        scope.launch {
            try {
                transport.sendCommand(
                    CanonicalCommand(
                        command =
                            canonicalCommand
                    )
                )

                Log.i(
                    TAG,
                    "Transport request completed for canonical command: " +
                        canonicalCommand
                )
            } catch (e: CancellationException) {
                Log.i(
                    TAG,
                    "Canonical command request cancelled"
                )

                throw e
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Canonical command request failed",
                    e
                )
            }
        }
    }

    /**
     * Defense-in-depth validation.
     *
     * CarCommandMap already enforces the Phase 1 allowlist.
     * This check prevents sentinel values and malformed command
     * names from reaching the Bluetooth transport.
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
            command == MODULE_CONFIG_COMMAND
        ) {
            return false
        }

        return CANONICAL_COMMAND_PATTERN.matches(
            command
        )
    }
}