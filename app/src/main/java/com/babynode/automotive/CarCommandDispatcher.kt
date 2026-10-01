package com.babynode.automotive

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * CarCommandDispatcher
 *
 * ONE RESPONSIBILITY:
 * Take a canonical automotive command and execute it.
 *
 * Pipeline:
 *   Natural language → CarCommandMap → CAN frame → CarCanTransport
 *
 * Now includes unified automotive logging (CAN-only, USB removed).
 */
class CarCommandDispatcher(
    private val scope: CoroutineScope,
    private val transport: CarCanTransport
) {

    private val TAG = "CarCommandDispatcher"

    fun handle(text: String) {
        Log.i(TAG, "AUTOMOTIVE ENTER: $text")

        // Step 1 — Convert natural language → canonical command
        val command = CarCommandMap.map(text)
        Log.i(TAG, "Mapped natural language to canonical command: $command")

        if (command == "UNKNOWN_AUTOMOTIVE_COMMAND") {
            Log.e(TAG, "Unknown automotive command: \"$text\" — dispatch aborted")
            return
        }

        // Step 2 — Lookup CAN frame
        val frame = CarCanMap.lookup(command)
        if (frame == null) {
            Log.e(TAG, "No CAN mapping found for canonical command: $command — dispatch aborted")
            return
        }

        // Step 3 — Unified automotive logging
        logAutomotiveEvent(text, command, frame)

        // Step 4 — Send CAN frame via transport abstraction
        scope.launch {
            Log.i(TAG, "Dispatching CAN frame via transport: id=${frame.id}, bytes=${frame.data.size}")
            transport.sendFrame(
                CarCanFrame(
                    id = frame.id,
                    data = frame.data
                )
            )
            Log.i(TAG, "Transport sendFrame() invoked for command: $command")
        }

        // Step 5 — Automotive feedback (log only)
        Log.i(TAG, "Command execution requested: $command")
    }

    /**
     * Unified automotive logging
     *
     * Logs:
     *  - Natural language
     *  - Canonical command
     *  - CAN frame ID
     *  - CAN payload (bytes)
     */
    private fun logAutomotiveEvent(
        natural: String,
        command: String,
        frame: CarCanMap.CanFrame
    ) {
        val idHex = "0x${frame.id.toString(16)}"
        val payloadHex = frame.data.joinToString(" ") { "0x%02X".format(it) }

        Log.i(TAG, "================ AUTOMOTIVE COMMAND ================")
        Log.i(TAG, "Natural language : $natural")
        Log.i(TAG, "Canonical command: $command")
        Log.i(TAG, "CAN frame ID     : $idHex")
        Log.i(TAG, "CAN payload      : $payloadHex")
        Log.i(TAG, "====================================================")
    }
}
