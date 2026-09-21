package com.babynode.automotive

/**
 * CarCanMap
 *
 * ONE RESPONSIBILITY:
 * Map canonical automotive commands → CAN frames.
 *
 * This file is required by CarCommandDispatcher.
 */
object CarCanMap {

    /**
     * CAN frame structure
     */
    data class CanFrame(
        val id: Int,
        val data: ByteArray
    )

    /**
     * Static lookup table for canonical automotive commands
     */
    private val map: Map<String, CanFrame> = mapOf(

        // ============================================================
        // WINDOWS
        // ============================================================
        "WINDOW_DRIVER_DOWN" to CanFrame(0x101, byteArrayOf(0x01, 0x00)),
        "WINDOW_DRIVER_UP"   to CanFrame(0x101, byteArrayOf(0x02, 0x00)),
        "WINDOW_PASSENGER_DOWN" to CanFrame(0x102, byteArrayOf(0x01, 0x00)),
        "WINDOW_PASSENGER_UP"   to CanFrame(0x102, byteArrayOf(0x02, 0x00)),

        // ============================================================
        // LOCKS
        // ============================================================
        "LOCK_DOORS"   to CanFrame(0x301, byteArrayOf(0x01)),
        "UNLOCK_DOORS" to CanFrame(0x301, byteArrayOf(0x00)),

        // ============================================================
        // CLIMATE — BASIC
        // ============================================================
        "AC_ON"        to CanFrame(0x201, byteArrayOf(0x01)),
        "AC_OFF"       to CanFrame(0x201, byteArrayOf(0x00)),
        "FAN_UP"       to CanFrame(0x202, byteArrayOf(0x01)),
        "FAN_DOWN"     to CanFrame(0x202, byteArrayOf(0x02)),
        "TEMP_UP"      to CanFrame(0x203, byteArrayOf(0x01)),
        "TEMP_DOWN"    to CanFrame(0x203, byteArrayOf(0x02)),
        "DEFROST_FRONT" to CanFrame(0x204, byteArrayOf(0x01)),
        "DEFROST_REAR"  to CanFrame(0x205, byteArrayOf(0x01)),
        "RECIRCULATE_ON"  to CanFrame(0x206, byteArrayOf(0x01)),
        "RECIRCULATE_OFF" to CanFrame(0x206, byteArrayOf(0x00)),
        "MAX_AC"       to CanFrame(0x207, byteArrayOf(0x01)),
        "MAX_HEAT"     to CanFrame(0x208, byteArrayOf(0x01)),

        // ============================================================
        // LIGHTS
        // ============================================================
        "HEADLIGHTS_ON"  to CanFrame(0x401, byteArrayOf(0x01)),
        "HEADLIGHTS_OFF" to CanFrame(0x401, byteArrayOf(0x00)),
        "HIGH_BEAMS_ON"  to CanFrame(0x402, byteArrayOf(0x01)),
        "HIGH_BEAMS_OFF" to CanFrame(0x402, byteArrayOf(0x00)),
        "FOG_LIGHTS_ON"  to CanFrame(0x403, byteArrayOf(0x01)),
        "FOG_LIGHTS_OFF" to CanFrame(0x403, byteArrayOf(0x00)),
        "INTERIOR_LIGHTS_ON"  to CanFrame(0x404, byteArrayOf(0x01)),
        "INTERIOR_LIGHTS_OFF" to CanFrame(0x404, byteArrayOf(0x00)),

        // ============================================================
        // BODY
        // ============================================================
        "TRUNK_OPEN" to CanFrame(0x501, byteArrayOf(0x01)),
        "HOOD_OPEN"  to CanFrame(0x502, byteArrayOf(0x01)),
        "GAS_CAP_OPEN" to CanFrame(0x503, byteArrayOf(0x01)),

        // ============================================================
        // ROOF
        // ============================================================
        "SUNROOF_OPEN"  to CanFrame(0x601, byteArrayOf(0x01)),
        "SUNROOF_CLOSE" to CanFrame(0x601, byteArrayOf(0x00)),
        "MOONROOF_OPEN"  to CanFrame(0x602, byteArrayOf(0x01)),
        "MOONROOF_CLOSE" to CanFrame(0x602, byteArrayOf(0x00)),

        // ============================================================
        // WIPERS
        // ============================================================
        "WIPERS_ON"    to CanFrame(0x701, byteArrayOf(0x01)),
        "WIPERS_OFF"   to CanFrame(0x701, byteArrayOf(0x00)),
        "WASHER_SPRAY" to CanFrame(0x702, byteArrayOf(0x01)),

        // ============================================================
        // MIRRORS
        // ============================================================
        "MIRROR_FOLD"   to CanFrame(0x801, byteArrayOf(0x01)),
        "MIRROR_UNFOLD" to CanFrame(0x801, byteArrayOf(0x00)),

        // ============================================================
        // SEATS
        // ============================================================
        "SEAT_HEATER_ON"  to CanFrame(0x901, byteArrayOf(0x01)),
        "SEAT_HEATER_OFF" to CanFrame(0x901, byteArrayOf(0x00)),

        // ============================================================
        // CRUISE CONTROL — STATIC COMMANDS
        // ============================================================
        "CRUISE_ON"     to CanFrame(0xA01, byteArrayOf(0x01)),
        "CRUISE_OFF"    to CanFrame(0xA01, byteArrayOf(0x00)),
        "CRUISE_RESUME" to CanFrame(0xA02, byteArrayOf(0x01)),
        "CRUISE_CANCEL" to CanFrame(0xA03, byteArrayOf(0x01))
    )

    /**
     * Lookup canonical command → CAN frame
     * Supports dynamic CRUISE_SET_XX commands.
     */
    fun lookup(command: String): CanFrame? {
        // Dynamic cruise control speed:
        // e.g., "CRUISE_SET_65"
        if (command.startsWith("CRUISE_SET_")) {
            val speedStr = command.removePrefix("CRUISE_SET_")
            val speed = speedStr.toIntOrNull()
            if (speed != null) {
                // CAN ID 0xA10 — cruise set speed
                // Payload: [speed]
                return CanFrame(0xA10, byteArrayOf(speed.toByte()))
            }
        }

        return map[command]
    }
}
