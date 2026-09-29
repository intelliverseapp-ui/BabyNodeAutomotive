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
 * Now includes unified automotive logging.
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
        Log.i(TAG, "Canonical automotive command: $command")

        if (command == "UNKNOWN_AUTOMOTIVE_COMMAND") {
            Log.e(TAG, "Unknown automotive command: \"$text\"")
            return
        }

        // Step 2 — Lookup CAN frame
        val frame = CarCanMap.lookup(command)
        if (frame == null) {
            Log.e(TAG, "No CAN mapping for: $command")
            return
        }

        // Step 3 — Unified automotive logging
        logAutomotiveEvent(text, command, frame)

        // Step 4 — Send CAN frame via transport abstraction
        scope.launch {
            transport.sendFrame(
                CarCanFrame(
                    id = frame.id,
                    data = frame.data
                )
            )
        }

        // Step 5 — Automotive feedback (log only)
        Log.i(TAG, "Executing automotive command: $command")
    }

    /**
     * Unified automotive logging
     *
     * Logs:
     *  - Natural language
     *  - Canonical command
     *  - CAN frame ID
     *  - CAN payload (bytes)
     *  - USB serial packet (hex)
     */
    private fun logAutomotiveEvent(
        natural: String,
        command: String,
        frame: CarCanMap.CanFrame
    ) {
        val idHex = "0x${frame.id.toString(16)}"
        val payloadHex = frame.data.joinToString(" ") { "0x%02X".format(it) }

        // Build the USB packet exactly as CarCanBus used to send it
        val usbPacket = buildUsbPacketPreview(frame.id, frame.data)
        val usbHex = usbPacket.joinToString(" ") { "0x%02X".format(it) }

        Log.i(TAG, "================ AUTOMOTIVE COMMAND ================")
        Log.i(TAG, "Natural language : $natural")
        Log.i(TAG, "Canonical command: $command")
        Log.i(TAG, "CAN frame ID     : $idHex")
        Log.i(TAG, "CAN payload      : $payloadHex")
        Log.i(TAG, "USB packet       : $usbHex")
        Log.i(TAG, "====================================================")
    }

    /**
     * Build USB packet preview (same format as CarCanBus)
     */
    private fun buildUsbPacketPreview(frameId: Int, data: ByteArray): ByteArray {
        val header = byteArrayOf(
            0xAA.toByte(),
            ((frameId shr 8) and 0xFF).toByte(),
            (frameId and 0xFF).toByte(),
            data.size.toByte()
        )
        return header + data
    }
}
