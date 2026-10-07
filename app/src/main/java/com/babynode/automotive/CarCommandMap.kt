package com.babynode.automotive


/**
 * CarCommandMap
 *
 * Hardened natural-language → canonical automotive command mapping.
 *
 * This version prevents:
 * - substring false positives (“unmute” containing “mute”)
 * - negation (“don’t unlock the doors”)
 * - ambiguous phrasing
 * - unsafe cruise speeds
 * - mapping when placeholder CAN frames are active
 *
 * OEM-specific CAN frames belong in CarCanMap.kt.
 */
object CarCommandMap {


    // ============================================================
    // SAFETY: BLOCK MAPPING WHEN PLACEHOLDER FRAMES ARE ACTIVE
    // ============================================================
    private const val PLACEHOLDER_CAN_FRAMES = CarCanMap.PLACEHOLDER_CAN_FRAMES


    // ============================================================
    // NEGATION TOKENS
    // ============================================================
    private val negationTokens = listOf(
        "don't", "do not", "stop", "cancel", "no", "not", "never"
    )


    private fun containsNegation(text: String): Boolean {
        val t = text.lowercase()
        return negationTokens.any { t.contains(it) }
    }


    // ============================================================
    // TOKENIZATION
    // ============================================================
    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .split(" ")
            .filter { it.isNotBlank() }
    }


    // ============================================================
    // MAIN MAPPING ENTRY POINT
    // ============================================================
    fun map(text: String): String {


        val lower = text.lowercase()
        val tokens = tokenize(lower)


        // ------------------------------------------------------------
        // SAFETY: Negation blocks mapping
        // ------------------------------------------------------------
        if (containsNegation(lower)) {
            return "IGNORED_NEGATED_COMMAND"
        }


        // ------------------------------------------------------------
        // SAFETY: Block mapping when placeholder frames are active
        // ------------------------------------------------------------
        if (PLACEHOLDER_CAN_FRAMES) {
            return "PLACEHOLDER_FRAMES_DISABLED_FOR_LIVE_USE"
        }


        // ============================================================
        // WINDOWS
        // ============================================================
        if ("driver" in tokens && "window" in tokens && "down" in tokens) return "WINDOW_DRIVER_DOWN"
        if ("driver" in tokens && "window" in tokens && "up" in tokens)   return "WINDOW_DRIVER_UP"
        if ("passenger" in tokens && "window" in tokens && "down" in tokens) return "WINDOW_PASSENGER_DOWN"
        if ("passenger" in tokens && "window" in tokens && "up" in tokens)   return "WINDOW_PASSENGER_UP"


        // ============================================================
        // LOCKS (safe ordering)
        // ============================================================
        if ("unlock" in tokens) return "UNLOCK_DOORS"
        if ("lock" in tokens && "unlock" !in tokens) return "LOCK_DOORS"


        // ============================================================
        // LIGHTING
        // ============================================================
        if ("headlight" in tokens || "headlights" in tokens) {
            if ("on" in tokens) return "HEADLIGHTS_ON"
            if ("off" in tokens) return "HEADLIGHTS_OFF"
        }


        if ("high" in tokens && "beam" in tokens) {
            if ("on" in tokens) return "HIGH_BEAMS_ON"
            if ("off" in tokens) return "HIGH_BEAMS_OFF"
        }


        if ("fog" in tokens && "light" in tokens) {
            if ("on" in tokens) return "FOG_LIGHTS_ON"
            if ("off" in tokens) return "FOG_LIGHTS_OFF"
        }


        if ("interior" in tokens && "light" in tokens) {
            if ("on" in tokens) return "INTERIOR_LIGHTS_ON"
            if ("off" in tokens) return "INTERIOR_LIGHTS_OFF"
        }


        if ("auto" in tokens && "headlight" in tokens) {
            if ("on" in tokens) return "AUTO_HEADLIGHTS_ON"
            if ("off" in tokens) return "AUTO_HEADLIGHTS_OFF"
        }


        if ("auto" in tokens && "high" in tokens && "beam" in tokens) {
            if ("on" in tokens) return "AUTO_HIGH_BEAMS_ON"
            if ("off" in tokens) return "AUTO_HIGH_BEAMS_OFF"
        }


        // ============================================================
        // BODY
        // ============================================================
        if ("open" in tokens && "trunk" in tokens) return "TRUNK_OPEN"
        if ("open" in tokens && "hood" in tokens) return "HOOD_OPEN"
        if ("gas" in tokens && "cap" in tokens) return "GAS_CAP_OPEN"
        if ("fuel" in tokens && "door" in tokens) return "GAS_CAP_OPEN"


        // ============================================================
        // CLIMATE
        // ============================================================
        if ("ac" in tokens && "on" in tokens) return "AC_ON"
        if ("ac" in tokens && "off" in tokens) return "AC_OFF"


        if ("fan" in tokens && "up" in tokens) return "FAN_UP"
        if ("fan" in tokens && "down" in tokens) return "FAN_DOWN"


        if ("temp" in tokens && "up" in tokens) return "TEMP_UP"
        if ("temp" in tokens && "down" in tokens) return "TEMP_DOWN"


        // Defrost
        if ("rear" in tokens && "defogger" in tokens && "on" in tokens) return "DEFROST_REAR"
        if ("rear" in tokens && "defogger" in tokens && "off" in tokens) return "DEFROST_REAR_OFF"


        if ("defrost" in tokens && "front" in tokens) return "DEFROST_FRONT"
        if ("defrost" in tokens && "rear" in tokens) return "DEFROST_REAR"


        // Climate modes
        if ("auto" in tokens && "climate" in tokens && "on" in tokens) return "CLIMATE_AUTO_ON"
        if ("auto" in tokens && "climate" in tokens && "off" in tokens) return "CLIMATE_AUTO_OFF"


        // Sync vs unsync (safe ordering)
        if ("unsync" in tokens) return "CLIMATE_SYNC_OFF"
        if ("sync" in tokens && "temp" in tokens) return "CLIMATE_SYNC_ON"


        if ("dual" in tokens && "on" in tokens) return "CLIMATE_DUAL_ON"
        if ("dual" in tokens && "off" in tokens) return "CLIMATE_DUAL_OFF"


        // ============================================================
        // ECO MODE
        // ============================================================
        if ("eco" in tokens && "mode" in tokens && "on" in tokens) return "ECO_MODE_ON"
        if ("eco" in tokens && "mode" in tokens && "off" in tokens) return "ECO_MODE_OFF"


        // ============================================================
        // TRACTION CONTROL
        // ============================================================
        if ("traction" in tokens && "on" in tokens) return "TRACTION_CONTROL_ON"
        if ("traction" in tokens && "off" in tokens) return "TRACTION_CONTROL_OFF"


        // ============================================================
        // PARKING SENSORS
        // ============================================================
        if ("parking" in tokens && "sensor" in tokens) {
            if ("on" in tokens) return "PARKING_SENSORS_ON"
            if ("off" in tokens) return "PARKING_SENSORS_OFF"
        }


        // ============================================================
        // DASH BRIGHTNESS
        // ============================================================
        if ("dash" in tokens && "bright" in tokens) return "DASH_BRIGHTNESS_UP"
        if ("dash" in tokens && ("dim" in tokens || "dark" in tokens)) return "DASH_BRIGHTNESS_DOWN"


        // ============================================================
        // LANEWATCH / CAMERA
        // ============================================================
        if ("lane" in tokens && "watch" in tokens) {
            if ("on" in tokens) return "LANEWATCH_ON"
            if ("off" in tokens) return "LANEWATCH_OFF"
        }


        // ============================================================
        // WIPERS
        // ============================================================
        if ("wiper" in tokens && "on" in tokens) return "WIPERS_ON"
        if ("wiper" in tokens && "off" in tokens) return "WIPERS_OFF"
        if ("washer" in tokens || "spray" in tokens) return "WASHER_SPRAY"


        // ============================================================
        // MIRRORS
        // ============================================================
        if ("fold" in tokens && "mirror" in tokens) return "MIRROR_FOLD"
        if ("unfold" in tokens && "mirror" in tokens) return "MIRROR_UNFOLD"


        // ============================================================
        // SEATS
        // ============================================================
        if ("seat" in tokens && "heat" in tokens && "on" in tokens) return "SEAT_HEATER_ON"
        if ("seat" in tokens && "heat" in tokens && "off" in tokens) return "SEAT_HEATER_OFF"


        // ============================================================
        // CRUISE CONTROL
        // ============================================================
        if ("cruise" in tokens && "on" in tokens) return "CRUISE_ON"
        if ("cruise" in tokens && "off" in tokens) return "CRUISE_OFF"
        if ("resume" in tokens && "cruise" in tokens) return "CRUISE_RESUME"
        if ("cancel" in tokens && "cruise" in tokens) return "CRUISE_CANCEL"


        if ("cruise" in tokens && "set" in tokens) {
            val speed = extractSpeed(lower)
            if (speed != null && speed in 0..200) {
                return "CRUISE_SET_$speed"
            }
        }


        // ============================================================
        // AUDIO / INFOTAINMENT
        // ============================================================
        if ("navigation" in tokens && "mute" in tokens) return "NAV_VOICE_MUTE"
        if ("navigation" in tokens && "unmute" in tokens) return "NAV_VOICE_UNMUTE"


        // Safe ordering: unmute before mute
        if ("unmute" in tokens && "volume" in tokens) return "AUDIO_UNMUTE"
        if ("mute" in tokens && "volume" in tokens) return "AUDIO_MUTE"


        if ("volume" in tokens && "up" in tokens) return "AUDIO_VOLUME_UP"
        if ("volume" in tokens && "down" in tokens) return "AUDIO_VOLUME_DOWN"


        if ("increase" in tokens && "volume" in tokens) return "AUDIO_VOLUME_UP"
        if ("decrease" in tokens && "volume" in tokens) return "AUDIO_VOLUME_DOWN"


        if ("raise" in tokens && "volume" in tokens) return "AUDIO_VOLUME_UP"
        if ("lower" in tokens && "volume" in tokens) return "AUDIO_VOLUME_DOWN"


        // Audio source switching
        if ("bluetooth" in tokens && "audio" in tokens) return "AUDIO_SOURCE_BT"
        if ("fm" in tokens && "radio" in tokens) return "AUDIO_SOURCE_FM"
        if ("am" in tokens && "radio" in tokens) return "AUDIO_SOURCE_AM"
        if ("xm" in tokens || "satellite" in tokens) return "AUDIO_SOURCE_XM"
        if ("usb" in tokens && "audio" in tokens) return "AUDIO_SOURCE_USB"


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