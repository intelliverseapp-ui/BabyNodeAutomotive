package com.babynode.automotive

/**
 * CarCommandMap
 *
 * UNIVERSAL NATURAL-LANGUAGE → CANONICAL AUTOMOTIVE COMMANDS
 *
 * OEM-specific CAN frames belong in CarCanMap.kt.
 */
object CarCommandMap {

    fun map(text: String): String {
        val t = text.lowercase()

        // ============================================================
        // WINDOWS
        // ============================================================
        if (t.contains("driver") && t.contains("window") && t.contains("down")) return "WINDOW_DRIVER_DOWN"
        if (t.contains("driver") && t.contains("window") && t.contains("up"))   return "WINDOW_DRIVER_UP"
        if (t.contains("passenger") && t.contains("window") && t.contains("down")) return "WINDOW_PASSENGER_DOWN"
        if (t.contains("passenger") && t.contains("window") && t.contains("up"))   return "WINDOW_PASSENGER_UP"

        // ============================================================
        // LOCKS
        // ============================================================
        if (t.contains("unlock")) return "UNLOCK_DOORS"
        if (t.contains("lock") && !t.contains("unlock")) return "LOCK_DOORS"

        // ============================================================
        // LIGHTING
        // ============================================================
        if (t.contains("turn") && t.contains("headlight") && t.contains("on")) return "HEADLIGHTS_ON"
        if (t.contains("turn") && t.contains("headlight") && t.contains("off")) return "HEADLIGHTS_OFF"

        if (t.contains("headlight") && t.contains("on")) return "HEADLIGHTS_ON"
        if (t.contains("headlight") && t.contains("off")) return "HEADLIGHTS_OFF"

        if (t.contains("high beam") || t.contains("bright light") || t.contains("brights")) {
            if (t.contains("on")) return "HIGH_BEAMS_ON"
            if (t.contains("off")) return "HIGH_BEAMS_OFF"
        }

        if (t.contains("fog light") || t.contains("fog lights")) {
            if (t.contains("on")) return "FOG_LIGHTS_ON"
            if (t.contains("off")) return "FOG_LIGHTS_OFF"
        }

        if (t.contains("interior light") || t.contains("dome light")) {
            if (t.contains("on")) return "INTERIOR_LIGHTS_ON"
            if (t.contains("off")) return "INTERIOR_LIGHTS_OFF"
        }

        // Auto headlights
        if (t.contains("auto") && t.contains("headlight")) {
            if (t.contains("on")) return "AUTO_HEADLIGHTS_ON"
            if (t.contains("off")) return "AUTO_HEADLIGHTS_OFF"
        }

        // Auto high beams
        if (t.contains("auto") && (t.contains("high beam") || t.contains("brights"))) {
            if (t.contains("on")) return "AUTO_HIGH_BEAMS_ON"
            if (t.contains("off")) return "AUTO_HIGH_BEAMS_OFF"
        }

        // ============================================================
        // BODY (TRUNK / HOOD / GAS CAP)
        // ============================================================
        if (t.contains("open") && t.contains("trunk")) return "TRUNK_OPEN"
        if (t.contains("open") && t.contains("hood")) return "HOOD_OPEN"
        if (t.contains("gas cap") || t.contains("fuel door")) return "GAS_CAP_OPEN"

        // ============================================================
        // CLIMATE / AC / HEAT
        // ============================================================
        if (t.contains("ac") && t.contains("on")) return "AC_ON"
        if (t.contains("ac") && t.contains("off")) return "AC_OFF"

        if (t.contains("fan") && t.contains("up")) return "FAN_UP"
        if (t.contains("fan") && t.contains("down")) return "FAN_DOWN"

        if (t.contains("temp") && t.contains("up")) return "TEMP_UP"
        if (t.contains("temp") && t.contains("down")) return "TEMP_DOWN"

        // Defrost / Defog
        if (t.contains("rear defogger") && t.contains("on")) return "DEFROST_REAR"
        if (t.contains("rear defogger") && t.contains("off")) return "DEFROST_REAR_OFF"

        if (t.contains("defrost") && t.contains("front")) return "DEFROST_FRONT"
        if (t.contains("defrost") && t.contains("rear")) return "DEFROST_REAR"
        if (t.contains("defog") && t.contains("front")) return "DEFROST_FRONT"
        if (t.contains("defog") && t.contains("rear")) return "DEFROST_REAR"

        // Climate modes
        if (t.contains("auto climate") && t.contains("on")) return "CLIMATE_AUTO_ON"
        if (t.contains("auto climate") && t.contains("off")) return "CLIMATE_AUTO_OFF"

        if (t.contains("sync") && t.contains("temp")) return "CLIMATE_SYNC_ON"
        if (t.contains("unsync") || (t.contains("sync") && t.contains("off"))) return "CLIMATE_SYNC_OFF"

        if (t.contains("dual") && t.contains("on")) return "CLIMATE_DUAL_ON"
        if (t.contains("dual") && t.contains("off")) return "CLIMATE_DUAL_OFF"

        // ============================================================
        // ECO MODE
        // ============================================================
        if (t.contains("eco mode") && t.contains("on")) return "ECO_MODE_ON"
        if (t.contains("eco mode") && t.contains("off")) return "ECO_MODE_OFF"

        // ============================================================
        // TRACTION CONTROL
        // ============================================================
        if (t.contains("traction") && t.contains("on")) return "TRACTION_CONTROL_ON"
        if (t.contains("traction") && t.contains("off")) return "TRACTION_CONTROL_OFF"

        // ============================================================
        // PARKING SENSORS
        // ============================================================
        if (t.contains("parking sensor") || t.contains("parking sensors")) {
            if (t.contains("on")) return "PARKING_SENSORS_ON"
            if (t.contains("off")) return "PARKING_SENSORS_OFF"
        }

        // ============================================================
        // DASH BRIGHTNESS
        // ============================================================
        if (t.contains("dash") && t.contains("bright")) return "DASH_BRIGHTNESS_UP"
        if (t.contains("dash") && (t.contains("dim") || t.contains("dark"))) return "DASH_BRIGHTNESS_DOWN"

        // ============================================================
        // BLIND SPOT / LANE CAMERA
        // ============================================================
        if (t.contains("lane watch") || t.contains("right camera") || t.contains("blind spot camera")) {
            if (t.contains("on")) return "LANEWATCH_ON"
            if (t.contains("off")) return "LANEWATCH_OFF"
        }

        // ============================================================
        // WIPERS / WASHER
        // ============================================================
        if (t.contains("wiper") && t.contains("on")) return "WIPERS_ON"
        if (t.contains("wiper") && t.contains("off")) return "WIPERS_OFF"
        if (t.contains("washer") || t.contains("spray")) return "WASHER_SPRAY"

        // ============================================================
        // MIRRORS
        // ============================================================
        if (t.contains("fold") && t.contains("mirror")) return "MIRROR_FOLD"
        if (t.contains("unfold") && t.contains("mirror")) return "MIRROR_UNFOLD"

        // ============================================================
        // SEATS
        // ============================================================
        if (t.contains("seat") && t.contains("heat") && t.contains("on")) return "SEAT_HEATER_ON"
        if (t.contains("seat") && t.contains("heat") && t.contains("off")) return "SEAT_HEATER_OFF"

        // ============================================================
        // CRUISE CONTROL
        // ============================================================
        if (t.contains("cruise") && t.contains("on")) return "CRUISE_ON"
        if (t.contains("cruise") && t.contains("off")) return "CRUISE_OFF"
        if (t.contains("resume") && t.contains("cruise")) return "CRUISE_RESUME"
        if (t.contains("cancel") && t.contains("cruise")) return "CRUISE_CANCEL"

        if (t.contains("cruise") && t.contains("set")) {
            val speed = extractSpeed(t)
            if (speed != null) return "CRUISE_SET_$speed"
        }

        // ============================================================
        // AUDIO / INFOTAINMENT
        // ============================================================
        if (t.contains("mute") && t.contains("navigation")) return "NAV_VOICE_MUTE"
        if (t.contains("unmute") && t.contains("navigation")) return "NAV_VOICE_UNMUTE"

        if (t.contains("mute") && t.contains("volume")) return "AUDIO_MUTE"
        if (t.contains("unmute") && t.contains("volume")) return "AUDIO_UNMUTE"

        if (t.contains("volume") && t.contains("up")) return "AUDIO_VOLUME_UP"
        if (t.contains("volume") && t.contains("down")) return "AUDIO_VOLUME_DOWN"

        if (t.contains("increase") && t.contains("volume")) return "AUDIO_VOLUME_UP"
        if (t.contains("decrease") && t.contains("volume")) return "AUDIO_VOLUME_DOWN"

        if (t.contains("raise") && t.contains("volume")) return "AUDIO_VOLUME_UP"
        if (t.contains("lower") && t.contains("volume")) return "AUDIO_VOLUME_DOWN"

        // Audio source switching
        if (t.contains("bluetooth audio")) return "AUDIO_SOURCE_BT"
        if (t.contains("fm radio") || (t.contains("fm") && t.contains("radio"))) return "AUDIO_SOURCE_FM"
        if (t.contains("am radio") || (t.contains("am") && t.contains("radio"))) return "AUDIO_SOURCE_AM"
        if (t.contains("xm radio") || t.contains("satellite radio")) return "AUDIO_SOURCE_XM"
        if (t.contains("usb") && t.contains("audio")) return "AUDIO_SOURCE_USB"

        // ============================================================
        // FALLBACK
        // ============================================================
        return "UNKNOWN_AUTOMOTIVE_COMMAND"
    }

    private fun extractSpeed(t: String): Int? {
        val regex = Regex("""\b(\d{2,3})\b""")
        val match = regex.find(t)
        return match?.value?.toIntOrNull()
    }
}
