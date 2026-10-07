package com.babynode.automotive

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * CarCommandDispatcher
 *
 * Pipeline:
 *
 * Natural Language
 *      ↓
 * CarCommandMap
 *      ↓
 * CanonicalCommand
 *      ↓
 * CarCanTransport
 *      ↓
 * BabyNodeCAN
 *      ↓
 * Vehicle-Specific CAN
 */
class CarCommandDispatcher(
    private val scope: CoroutineScope,
    private val transport: CarCanTransport
) {

    private val TAG = "CarCommandDispatcher"

    fun sendModuleConfig(moduleJsonId: String) {

        Log.i(
            TAG,
            "sendModuleConfig(): module=$moduleJsonId"
        )

        scope.launch {

            transport.sendCommand(
                CanonicalCommand(
                    command = "config.module",
                    value = moduleJsonId
                )
            )

            Log.i(
                TAG,
                "Module configuration dispatched"
            )
        }
    }

    fun handle(text: String) {

        Log.i(TAG, "DISPATCH ENTER: $text")

        val command = CarCommandMap.map(text)

        Log.i(
            TAG,
            "Mapped natural language to canonical command: $command"
        )

        if (command == "UNKNOWN_AUTOMOTIVE_COMMAND") {

            Log.e(
                TAG,
                "Unknown automotive command: \"$text\""
            )

            return
        }

        if (command == "IGNORED_NEGATED_COMMAND") {

            Log.w(
                TAG,
                "Negated automotive command ignored: \"$text\""
            )

            return
        }

        if (command == "PLACEHOLDER_FRAMES_DISABLED_FOR_LIVE_USE") {

            Log.w(
                TAG,
                "Placeholder command ignored"
            )

            return
        }

        logAutomotiveEvent(
            natural = text,
            command = command
        )

        scope.launch {

            transport.sendCommand(
                CanonicalCommand(
                    command = command
                )
            )

            Log.i(
                TAG,
                "Transport sendCommand() invoked: $command"
            )
        }

        Log.i(
            TAG,
            "Command dispatch requested: $command"
        )
    }

    private fun logAutomotiveEvent(
        natural: String,
        command: String
    ) {

        Log.i(
            TAG,
            "================ AUTOMOTIVE COMMAND ================"
        )

        Log.i(
            TAG,
            "Natural language : $natural"
        )

        Log.i(
            TAG,
            "Canonical command: $command"
        )

        Log.i(
            TAG,
            "===================================================="
        )
    }
}