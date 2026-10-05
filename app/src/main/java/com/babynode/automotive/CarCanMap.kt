package com.babynode.automotive

/**
 * CarCanMap
 *
 * UNIVERSAL CANONICAL COMMAND → PLACEHOLDER CAN FRAMES
 *
 * OEM-specific CAN frames will replace these once real CAN logs
 * are captured using PCAN-Explorer 7.
 */
object CarCanMap {

    data class CanFrame(
        val id: Int,
        val data: ByteArray
    )

    /**
     * Placeholder CAN map for ALL universal automotive commands.
     * IDs and payloads are FAKE and SAFE for testing.
     */
    private val map: Map<String, CanFrame> = mapOf(

        // ============================================================
        // WINDOWS
        // ============================================================
        "WINDOW_DRIVER_DOWN" to CanFrame(0x101, byteArrayOf(0x01)),
        "WINDOW_DRIVER_UP"   to CanFrame(0x101, byteArrayOf(0x02)),
        "WINDOW_PASSENGER_DOWN" to CanFrame(0x102, byteArrayOf(0x01)),
        "WINDOW_PASSENGER_UP"   to CanFrame(0x102, byteArrayOf(0x02)),

        // ============================================================
        // LOCKS
        // ============================================================
        "LOCK_DOORS"   to CanFrame(0x301, byteArrayOf(0x01)),
        "UNLOCK_DOORS" to CanFrame(0x301, byteArrayOf(0x00)),

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

        // Auto headlights
        "AUTO_HEADLIGHTS_ON"  to CanFrame(0x405, byteArrayOf(0x01)),
        "AUTO_HEADLIGHTS_OFF" to CanFrame(0x405, byteArrayOf(0x00)),

        // Auto high beams
        "AUTO_HIGH_BEAMS_ON"  to CanFrame(0x406, byteArrayOf(0x01)),
        "AUTO_HIGH_BEAMS_OFF" to CanFrame(0x406, byteArrayOf(0x00)),

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
        // CLIMATE
        // ============================================================
        "AC_ON"        to CanFrame(0x201, byteArrayOf(0x01)),
        "AC_OFF"       to CanFrame(0x201, byteArrayOf(0x00)),

        "FAN_UP"       to CanFrame(0x202, byteArrayOf(0x01)),
        "FAN_DOWN"     to CanFrame(0x202, byteArrayOf(0x02)),

        "TEMP_UP"      to CanFrame(0x203, byteArrayOf(0x01)),
        "TEMP_DOWN"    to CanFrame(0x203, byteArrayOf(0x02)),

        "DEFROST_FRONT" to CanFrame(0x204, byteArrayOf(0x01)),
        "DEFROST_REAR"  to CanFrame(0x205, byteArrayOf(0x01)),
        "DEFROST_REAR_OFF" to CanFrame(0x205, byteArrayOf(0x00)),

        "RECIRCULATE_ON"  to CanFrame(0x206, byteArrayOf(0x01)),
        "RECIRCULATE_OFF" to CanFrame(0x206, byteArrayOf(0x00)),

        "MAX_AC"       to CanFrame(0x207, byteArrayOf(0x01)),
        "MAX_HEAT"     to CanFrame(0x208, byteArrayOf(0x01)),

        // Climate modes
        "CLIMATE_AUTO_ON"  to CanFrame(0x209, byteArrayOf(0x01)),
        "CLIMATE_AUTO_OFF" to CanFrame(0x209, byteArrayOf(0x00)),

        "CLIMATE_SYNC_ON"  to CanFrame(0x20A, byteArrayOf(0x01)),
        "CLIMATE_SYNC_OFF" to CanFrame(0x20A, byteArrayOf(0x00)),

        "CLIMATE_DUAL_ON"  to CanFrame(0x20B, byteArrayOf(0x01)),
        "CLIMATE_DUAL_OFF" to CanFrame(0x20B, byteArrayOf(0x00)),

        // ============================================================
        // ECO MODE
        // ============================================================
        "ECO_MODE_ON"  to CanFrame(0x30A, byteArrayOf(0x01)),
        "ECO_MODE_OFF" to CanFrame(0x30A, byteArrayOf(0x00)),

        // ============================================================
        // TRACTION CONTROL
        // ============================================================
        "TRACTION_CONTROL_ON"  to CanFrame(0x30B, byteArrayOf(0x01)),
        "TRACTION_CONTROL_OFF" to CanFrame(0x30B, byteArrayOf(0x00)),

        // ============================================================
        // PARKING SENSORS
        // ============================================================
        "PARKING_SENSORS_ON"  to CanFrame(0x30C, byteArrayOf(0x01)),
        "PARKING_SENSORS_OFF" to CanFrame(0x30C, byteArrayOf(0x00)),

        // ============================================================
        // DASH BRIGHTNESS
        // ============================================================
        "DASH_BRIGHTNESS_UP"   to CanFrame(0x30D, byteArrayOf(0x01)),
        "DASH_BRIGHTNESS_DOWN" to CanFrame(0x30D, byteArrayOf(0x00)),

        // ============================================================
        // BLIND SPOT / LANE CAMERA
        // ============================================================
        "LANEWATCH_ON"  to CanFrame(0x30E, byteArrayOf(0x01)),
        "LANEWATCH_OFF" to CanFrame(0x30E, byteArrayOf(0x00)),

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
        // CRUISE CONTROL
        // ============================================================
        "CRUISE_ON"     to CanFrame(0xA01, byteArrayOf(0x01)),
        "CRUISE_OFF"    to CanFrame(0xA01, byteArrayOf(0x00)),
        "CRUISE_RESUME" to CanFrame(0xA02, byteArrayOf(0x01)),
        "CRUISE_CANCEL" to CanFrame(0xA03, byteArrayOf(0x01)),

        // ============================================================
        // AUDIO / INFOTAINMENT
        // ============================================================
        "AUDIO_MUTE"        to CanFrame(0xB00, byteArrayOf(0x01)),
        "AUDIO_UNMUTE"      to CanFrame(0xB00, byteArrayOf(0x00)),
        "AUDIO_VOLUME_UP"   to CanFrame(0xB01, byteArrayOf(0x01)),
        "AUDIO_VOLUME_DOWN" to CanFrame(0xB01, byteArrayOf(0x02)),

        // Navigation voice
        "NAV_VOICE_MUTE"   to CanFrame(0xB02, byteArrayOf(0x01)),
        "NAV_VOICE_UNMUTE" to CanFrame(0xB02, byteArrayOf(0x00)),

        // Audio source switching
        "AUDIO_SOURCE_BT"  to CanFrame(0xB10, byteArrayOf(0x01)),
        "AUDIO_SOURCE_FM"  to CanFrame(0xB11, byteArrayOf(0x01)),
        "AUDIO_SOURCE_AM"  to CanFrame(0xB12, byteArrayOf(0x01)),
        "AUDIO_SOURCE_XM"  to CanFrame(0xB13, byteArrayOf(0x01)),
        "AUDIO_SOURCE_USB" to CanFrame(0xB14, byteArrayOf(0x01))
    )

    /**
     * Dynamic cruise control speed
     */
    fun lookup(command: String): CanFrame? {
        if (command.startsWith("CRUISE_SET_")) {
            val speedStr = command.removePrefix("CRUISE_SET_")
            val speed = speedStr.toIntOrNull()
            if (speed != null) {
                return CanFrame(0xA10, byteArrayOf(speed.toByte()))
            }
        }

        return map[command]
    }
}
