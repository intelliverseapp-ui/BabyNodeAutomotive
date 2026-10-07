package com.babynode.automotive

/**
 * CarCanMap
 *
 * UNIVERSAL CANONICAL COMMAND → PLACEHOLDER CAN FRAMES
 *
 * These frames are NOT REAL VEHICLE CAN FRAMES.
 * They are SAFE placeholders used ONLY for development and UI testing.
 *
 * BabyNode Automotive now sends canonical commands over Bluetooth,
 * but this file remains available for testing and future simulation use.
 */
object CarCanMap {

    // ============================================================
    // PLACEHOLDER FRAME SUPPORT
    // ============================================================
    // Enabled to allow command mapping during development.
    // Android no longer sends CAN frames directly, but other
    // legacy test paths may still reference this map.
    const val PLACEHOLDER_CAN_FRAMES = false

    // ============================================================
    // VALIDATION LIMITS
    // ============================================================
    private const val MIN_CRUISE_SPEED = 0
    private const val MAX_CRUISE_SPEED = 200

    private fun validateId(id: Int): Boolean {
        return id in 0x000..0x1FFFFFFF
    }

    private fun validateData(data: ByteArray): Boolean {
        return data.size in 0..64
    }

    data class CanFrame(
        val id: Int,
        val data: ByteArray
    )

    /**
     * Placeholder CAN map for ALL universal automotive commands.
     * IDs and payloads are FAKE and SAFE for testing.
     */
    private val map: Map<String, CanFrame> = mapOf(

        "WINDOW_DRIVER_DOWN" to CanFrame(0x101, byteArrayOf(0x01)),
        "WINDOW_DRIVER_UP" to CanFrame(0x101, byteArrayOf(0x02)),
        "WINDOW_PASSENGER_DOWN" to CanFrame(0x102, byteArrayOf(0x01)),
        "WINDOW_PASSENGER_UP" to CanFrame(0x102, byteArrayOf(0x02)),

        "LOCK_DOORS" to CanFrame(0x301, byteArrayOf(0x01)),
        "UNLOCK_DOORS" to CanFrame(0x301, byteArrayOf(0x00)),

        "HEADLIGHTS_ON" to CanFrame(0x401, byteArrayOf(0x01)),
        "HEADLIGHTS_OFF" to CanFrame(0x401, byteArrayOf(0x00)),

        "HIGH_BEAMS_ON" to CanFrame(0x402, byteArrayOf(0x01)),
        "HIGH_BEAMS_OFF" to CanFrame(0x402, byteArrayOf(0x00)),

        "FOG_LIGHTS_ON" to CanFrame(0x403, byteArrayOf(0x01)),
        "FOG_LIGHTS_OFF" to CanFrame(0x403, byteArrayOf(0x00)),

        "INTERIOR_LIGHTS_ON" to CanFrame(0x404, byteArrayOf(0x01)),
        "INTERIOR_LIGHTS_OFF" to CanFrame(0x404, byteArrayOf(0x00)),

        "AUTO_HEADLIGHTS_ON" to CanFrame(0x405, byteArrayOf(0x01)),
        "AUTO_HEADLIGHTS_OFF" to CanFrame(0x405, byteArrayOf(0x00)),

        "AUTO_HIGH_BEAMS_ON" to CanFrame(0x406, byteArrayOf(0x01)),
        "AUTO_HIGH_BEAMS_OFF" to CanFrame(0x406, byteArrayOf(0x00)),

        "TRUNK_OPEN" to CanFrame(0x501, byteArrayOf(0x01)),
        "HOOD_OPEN" to CanFrame(0x502, byteArrayOf(0x01)),
        "GAS_CAP_OPEN" to CanFrame(0x503, byteArrayOf(0x01)),

        "AC_ON" to CanFrame(0x201, byteArrayOf(0x01)),
        "AC_OFF" to CanFrame(0x201, byteArrayOf(0x00)),

        "FAN_UP" to CanFrame(0x202, byteArrayOf(0x01)),
        "FAN_DOWN" to CanFrame(0x202, byteArrayOf(0x02)),

        "TEMP_UP" to CanFrame(0x203, byteArrayOf(0x01)),
        "TEMP_DOWN" to CanFrame(0x203, byteArrayOf(0x02))
    )

    /**
     * Dynamic cruise control speed
     */
    fun lookup(command: String): CanFrame? {

        if (command.startsWith("CRUISE_SET_")) {

            val speedStr =
                command.removePrefix("CRUISE_SET_")

            val speed =
                speedStr.toIntOrNull()

            if (speed == null) return null
            if (speed !in MIN_CRUISE_SPEED..MAX_CRUISE_SPEED) return null

            val frame =
                CanFrame(
                    0xA10,
                    byteArrayOf(speed.toByte())
                )

            if (!validateId(frame.id)) return null
            if (!validateData(frame.data)) return null

            return frame
        }

        val frame =
            map[command] ?: return null

        if (!validateId(frame.id)) return null
        if (!validateData(frame.data)) return null

        return frame
    }
}