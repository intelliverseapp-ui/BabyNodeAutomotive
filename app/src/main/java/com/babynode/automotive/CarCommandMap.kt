package com.babynode.automotive

/**
 * CarCommandMap
 *
 * ONE RESPONSIBILITY:
 * Convert natural-language automotive commands
 * into canonical internal command identifiers.
 *
 * Example:
 *  "driver side window down" → "WINDOW_DRIVER_DOWN"
 *
 * These canonical identifiers map directly to CarCanMap.
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
        if (t.contains("lock") && !t.contains("unlock")) return "LOCK_DOORS"
        if (t.contains("unlock")) return "UNLOCK_DOORS"

        // ============================================================
        // CLIMATE / AC / HEAT
        // ============================================================
        if (t.contains("ac") && t.contains("on")) return "AC_ON"
        if (t.contains("ac") && t.contains("off")) return "AC_OFF"

        if (t.contains("fan") && t.contains("up")) return "FAN_UP"
        if (t.contains("fan") && t.contains("down")) return "FAN_DOWN"

        if (t.contains("temp") && t.contains("up")) return "TEMP_UP"
        if (t.contains("temp") && t.contains("down")) return "TEMP_DOWN"

        if (t.contains("defrost") && t.contains("front")) return "DEFROST_FRONT"
        if (t.contains("defrost") && t.contains("rear")) return "DEFROST_REAR"
        if (t.contains("defog") && t.contains("front")) return "DEFROST_FRONT"
        if (t.contains("defog") && t.contains("rear")) return "DEFROST_REAR"

        if (t.contains("recirculate") && t.contains("on")) return "RECIRCULATE_ON"
        if (t.contains("recirculate") && t.contains("off")) return "RECIRCULATE_OFF"

        if (t.contains("max ac")) return "MAX_AC"
        if (t.contains("max heat")) return "MAX_HEAT"

        // ============================================================
        // LIGHTING
        // ============================================================
        if (t.contains("headlight") && t.contains("on")) return "HEADLIGHTS_ON"
        if (t.contains("headlight") && t.contains("off")) return "HEADLIGHTS_OFF"

        if (t.contains("high beam") || t.contains("bright light")) {
            if (t.contains("on")) return "HIGH_BEAMS_ON"
            if (t.contains("off")) return "HIGH_BEAMS_OFF"
        }

        if (t.contains("fog light")) {
            if (t.contains("on")) return "FOG_LIGHTS_ON"
            if (t.contains("off")) return "FOG_LIGHTS_OFF"
        }

        if (t.contains("interior light") || t.contains("dome light")) {
            if (t.contains("on")) return "INTERIOR_LIGHTS_ON"
            if (t.contains("off")) return "INTERIOR_LIGHTS_OFF"
        }

        // ============================================================
        // BODY (TRUNK / HOOD / GAS CAP)
        // ============================================================
        if (t.contains("trunk") && (t.contains("open") || t.contains("pop"))) return "TRUNK_OPEN"
        if (t.contains("hood") && (t.contains("open") || t.contains("pop"))) return "HOOD_OPEN"
        if (t.contains("gas cap") || t.contains("fuel door")) return "GAS_CAP_OPEN"

        // ============================================================
        // ROOF (SUNROOF / MOONROOF)
        // ============================================================
        if (t.contains("sunroof")) {
            if (t.contains("open")) return "SUNROOF_OPEN"
            if (t.contains("close")) return "SUNROOF_CLOSE"
        }

        if (t.contains("moonroof")) {
            if (t.contains("open")) return "MOONROOF_OPEN"
            if (t.contains("close")) return "MOONROOF_CLOSE"
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

        // "set cruise to 65"
        if (t.contains("cruise") && t.contains("set")) {
            val speed = extractSpeed(t)
            if (speed != null) return "CRUISE_SET_$speed"
        }

        // ============================================================
        // AUDIO / INFOTAINMENT
        // ============================================================
        if (t.contains("mute") && t.contains("volume")) return "AUDIO_MUTE"
        if (t.contains("unmute") && t.contains("volume")) return "AUDIO_UNMUTE"

        if (t.contains("volume") && t.contains("up")) return "AUDIO_VOLUME_UP"
        if (t.contains("volume") && t.contains("down")) return "AUDIO_VOLUME_DOWN"

        if (t.contains("increase") && t.contains("volume")) return "AUDIO_VOLUME_UP"
        if (t.contains("decrease") && t.contains("volume")) return "AUDIO_VOLUME_DOWN"

        if (t.contains("raise") && t.contains("volume")) return "AUDIO_VOLUME_UP"
        if (t.contains("lower") && t.contains("volume")) return "AUDIO_VOLUME_DOWN"

        // ============================================================
        // FALLBACK
        // ============================================================
        return "UNKNOWN_AUTOMOTIVE_COMMAND"
    }

    /**
     * Extract speed from phrases like:
     *  "set cruise to 65"
     *  "set cruise at 70"
     *  "cruise set 55"
     */
    private fun extractSpeed(t: String): Int? {
        val regex = Regex("""\b(\d{2,3})\b""")
        val match = regex.find(t)
        return match?.value?.toIntOrNull()
    }
}
