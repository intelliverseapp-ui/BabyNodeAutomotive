package com.babynode.automotive

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * CarCommandDispatcher
 *
 * ONE RESPONSIBILITY:
 * Execute either:
 *   - Raw TCP SEND commands
 *   - Automotive natural-language commands
 *
 * Pipeline:
 *   Natural language → CarCommandMap → CAN frame → CarCanTransport
 *   Raw SEND → CarCanTransport
 */
class CarCommandDispatcher(
    private val scope: CoroutineScope,
    private val transport: CarCanTransport
) {

    private val TAG = "CarCommandDispatcher"

    fun handle(text: String) {
        Log.i(TAG, "DISPATCH ENTER: $text")

        // ============================================================
        // RAW SEND COMMAND PATH (ALWAYS TAKES PRIORITY)
        // ============================================================
        if (text.startsWith("SEND ", ignoreCase = true)) {
            Log.i(TAG, "Raw SEND command detected → bypassing automotive parser")

            val parts = text.trim().split(Regex("\\s+"))
            if (parts.size < 4) {
                Log.e(TAG, "SEND command malformed: $text")
                return
            }

            try {
                val id = parts[1].toInt()
                val dlc = parts[2].toInt()

                if (dlc < 0 || dlc > 64) {
                    Log.e(TAG, "SEND command invalid DLC: $dlc")
                    return
                }

                val bytes = parts.drop(3).map {
                    val b = it.toInt()
                    if (b !in 0..255) throw IllegalArgumentException("Byte out of range")
                    b.toByte()
                }.toByteArray()

                if (bytes.size != dlc) {
                    Log.e(TAG, "SEND command DLC mismatch: expected $dlc bytes, got ${bytes.size}")
                    return
                }

                val frame = CarCanFrame(id = id, data = bytes)

                scope.launch {
                    Log.i(TAG, "Dispatching RAW SEND frame: id=$id dlc=$dlc")
                    transport.sendFrame(frame)
                }

                Log.i(TAG, "RAW SEND command executed successfully")
                return

            } catch (e: Exception) {
                Log.e(TAG, "SEND command parse error: ${e.message}")
                return
            }
        }

        // ============================================================
        // AUTOMOTIVE NATURAL LANGUAGE PATH
        // ============================================================
        Log.i(TAG, "AUTOMOTIVE ENTER: $text")

        val command = CarCommandMap.map(text)
        Log.i(TAG, "Mapped natural language to canonical command: $command")

        if (command == "UNKNOWN_AUTOMOTIVE_COMMAND") {
            Log.e(TAG, "Unknown automotive command: \"$text\" — dispatch aborted")
            return
        }

        val frame = CarCanMap.lookup(command)
        if (frame == null) {
            Log.e(TAG, "No CAN mapping found for canonical command: $command — dispatch aborted")
            return
        }

        logAutomotiveEvent(text, command, frame)

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

        Log.i(TAG, "Command execution requested: $command")
    }

    /**
     * Unified automotive logging
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
