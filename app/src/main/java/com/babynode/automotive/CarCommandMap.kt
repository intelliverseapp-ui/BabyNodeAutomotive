package com.babynode.automotive

/**
 * CarCommandMap
 *
 * Converts natural-language driver requests into canonical
 * BabyNode Automotive commands.
 *
 * Architectural boundary:
 * - Android is command-aware.
 * - Android is CAN-agnostic.
 * - Vehicle-specific CAN IDs and payloads belong in BabyNodeCAN.
 *
 * Phase 1 includes driver-operated body, convenience, climate,
 * lighting, audio, and cabin controls.
 *
 * Phase 1 excludes brake, steering, airbag, engine-torque,
 * transmission, traction-control, eco/powertrain, and cruise-control
 * operations.
 */
object CarCommandMap {

    const val UNKNOWN_COMMAND =
        "UNKNOWN_AUTOMOTIVE_COMMAND"

    const val IGNORED_NEGATED_COMMAND =
        "IGNORED_NEGATED_COMMAND"

    const val BLOCKED_OUT_OF_SCOPE_COMMAND =
        "BLOCKED_OUT_OF_SCOPE_COMMAND"

    // ============================================================
    // PHASE 1 CANONICAL COMMAND ALLOWLIST
    // ============================================================
    private val phaseOneAllowlist = setOf(
        // Windows
        "WINDOW_DRIVER_DOWN",
        "WINDOW_DRIVER_UP",
        "WINDOW_PASSENGER_DOWN",
        "WINDOW_PASSENGER_UP",

        // Locks
        "LOCK_DOORS",
        "UNLOCK_DOORS",

        // Lighting
        "HEADLIGHTS_ON",
        "HEADLIGHTS_OFF",
        "HIGH_BEAMS_ON",
        "HIGH_BEAMS_OFF",
        "FOG_LIGHTS_ON",
        "FOG_LIGHTS_OFF",
        "INTERIOR_LIGHTS_ON",
        "INTERIOR_LIGHTS_OFF",
        "AUTO_HEADLIGHTS_ON",
        "AUTO_HEADLIGHTS_OFF",
        "AUTO_HIGH_BEAMS_ON",
        "AUTO_HIGH_BEAMS_OFF",

        // Body and convenience
        "TRUNK_OPEN",
        "HOOD_OPEN",
        "GAS_CAP_OPEN",

        // Climate
        "AC_ON",
        "AC_OFF",
        "FAN_UP",
        "FAN_DOWN",
        "TEMP_UP",
        "TEMP_DOWN",
        "DEFROST_FRONT",
        "DEFROST_REAR",
        "DEFROST_REAR_OFF",
        "CLIMATE_AUTO_ON",
        "CLIMATE_AUTO_OFF",
        "CLIMATE_SYNC_ON",
        "CLIMATE_SYNC_OFF",
        "CLIMATE_DUAL_ON",
        "CLIMATE_DUAL_OFF",

        // Driver-operated convenience
        "PARKING_SENSORS_ON",
        "PARKING_SENSORS_OFF",
        "DASH_BRIGHTNESS_UP",
        "DASH_BRIGHTNESS_DOWN",
        "LANEWATCH_ON",
        "LANEWATCH_OFF",
        "WIPERS_ON",
        "WIPERS_OFF",
        "WASHER_SPRAY",
        "MIRROR_FOLD",
        "MIRROR_UNFOLD",
        "SEAT_HEATER_ON",
        "SEAT_HEATER_OFF",

        // Audio and infotainment
        "NAV_VOICE_MUTE",
        "NAV_VOICE_UNMUTE",
        "AUDIO_MUTE",
        "AUDIO_UNMUTE",
        "AUDIO_VOLUME_UP",
        "AUDIO_VOLUME_DOWN",
        "AUDIO_SOURCE_BT",
        "AUDIO_SOURCE_FM",
        "AUDIO_SOURCE_AM",
        "AUDIO_SOURCE_XM",
        "AUDIO_SOURCE_USB"
    )

    // ============================================================
    // TOKENIZATION AND NORMALIZATION
    // ============================================================
    private fun normalizeText(
        text: String
    ): String {
        return text
            .lowercase()
            .replace('’', '\'')
            .replace(
                Regex("""\bdon['']?t\b"""),
                "do not"
            )
            .replace(
                Regex("""\bcannot\b"""),
                "can not"
            )
            .replace(
                Regex("""[^a-z0-9]+"""),
                " "
            )
            .trim()
            .replace(
                Regex("""\s+"""),
                " "
            )
    }

    private fun tokenize(
        normalizedText: String
    ): Set<String> {
        if (normalizedText.isBlank()) {
            return emptySet()
        }

        return normalizedText
            .split(" ")
            .filter {
                it.isNotBlank()
            }
            .toSet()
    }

    // ============================================================
    // NEGATION HANDLING
    // ============================================================
    private fun containsNegation(
        normalizedText: String,
        tokens: Set<String>
    ): Boolean {
        if ("do not" in normalizedText) {
            return true
        }

        if ("can not" in normalizedText) {
            return true
        }

        return tokens.any {
            it == "no" ||
                it == "not" ||
                it == "never" ||
                it == "stop"
        }
    }

    // ============================================================
    // OUT-OF-SCOPE PHASE 1 DETECTION
    // ============================================================
    private fun containsOutOfScopeCommand(
        tokens: Set<String>
    ): Boolean {
        if ("traction" in tokens) {
            return true
        }

        if (
            "eco" in tokens &&
            "mode" in tokens
        ) {
            return true
        }

        if ("cruise" in tokens) {
            return true
        }

        return tokens.any {
            it == "brake" ||
                it == "brakes" ||
                it == "steering" ||
                it == "airbag" ||
                it == "airbags" ||
                it == "torque" ||
                it == "transmission"
        }
    }

    // ============================================================
    // PUBLIC MAPPING ENTRY POINT
    // ============================================================
    fun map(
        text: String
    ): String {
        val normalizedText =
            normalizeText(
                text
            )

        if (normalizedText.isBlank()) {
            return UNKNOWN_COMMAND
        }

        val tokens =
            tokenize(
                normalizedText
            )

        if (
            containsNegation(
                normalizedText,
                tokens
            )
        ) {
            return IGNORED_NEGATED_COMMAND
        }

        if (
            containsOutOfScopeCommand(
                tokens
            )
        ) {
            return BLOCKED_OUT_OF_SCOPE_COMMAND
        }

        val candidate =
            mapPhaseOneCommand(
                tokens
            )

        return allowPhaseOneCommand(
            candidate
        )
    }

    // ============================================================
    // PHASE 1 COMMAND MAPPING
    // ============================================================
    private fun mapPhaseOneCommand(
        tokens: Set<String>
    ): String? {

        // --------------------------------------------------------
        // WINDOWS
        // --------------------------------------------------------
        if (
            "driver" in tokens &&
            hasWindowToken(tokens) &&
            "down" in tokens
        ) {
            return "WINDOW_DRIVER_DOWN"
        }

        if (
            "driver" in tokens &&
            hasWindowToken(tokens) &&
            "up" in tokens
        ) {
            return "WINDOW_DRIVER_UP"
        }

        if (
            "passenger" in tokens &&
            hasWindowToken(tokens) &&
            "down" in tokens
        ) {
            return "WINDOW_PASSENGER_DOWN"
        }

        if (
            "passenger" in tokens &&
            hasWindowToken(tokens) &&
            "up" in tokens
        ) {
            return "WINDOW_PASSENGER_UP"
        }

        // --------------------------------------------------------
        // LOCKS
        // --------------------------------------------------------
        if ("unlock" in tokens) {
            return "UNLOCK_DOORS"
        }

        if (
            "lock" in tokens &&
            "unlock" !in tokens
        ) {
            return "LOCK_DOORS"
        }

        // --------------------------------------------------------
        // SPECIFIC AUTOMATIC LIGHTING
        // --------------------------------------------------------
        if (
            "auto" in tokens &&
            "high" in tokens &&
            hasBeamToken(tokens)
        ) {
            if ("on" in tokens) {
                return "AUTO_HIGH_BEAMS_ON"
            }

            if ("off" in tokens) {
                return "AUTO_HIGH_BEAMS_OFF"
            }
        }

        if (
            "auto" in tokens &&
            hasHeadlightToken(tokens)
        ) {
            if ("on" in tokens) {
                return "AUTO_HEADLIGHTS_ON"
            }

            if ("off" in tokens) {
                return "AUTO_HEADLIGHTS_OFF"
            }
        }

        // --------------------------------------------------------
        // LIGHTING
        // --------------------------------------------------------
        if (hasHeadlightToken(tokens)) {
            if ("on" in tokens) {
                return "HEADLIGHTS_ON"
            }

            if ("off" in tokens) {
                return "HEADLIGHTS_OFF"
            }
        }

        if (
            "high" in tokens &&
            hasBeamToken(tokens)
        ) {
            if ("on" in tokens) {
                return "HIGH_BEAMS_ON"
            }

            if ("off" in tokens) {
                return "HIGH_BEAMS_OFF"
            }
        }

        if (
            "fog" in tokens &&
            hasLightToken(tokens)
        ) {
            if ("on" in tokens) {
                return "FOG_LIGHTS_ON"
            }

            if ("off" in tokens) {
                return "FOG_LIGHTS_OFF"
            }
        }

        if (
            "interior" in tokens &&
            hasLightToken(tokens)
        ) {
            if ("on" in tokens) {
                return "INTERIOR_LIGHTS_ON"
            }

            if ("off" in tokens) {
                return "INTERIOR_LIGHTS_OFF"
            }
        }

        // --------------------------------------------------------
        // BODY AND CONVENIENCE
        // --------------------------------------------------------
        if (
            "open" in tokens &&
            "trunk" in tokens
        ) {
            return "TRUNK_OPEN"
        }

        if (
            "open" in tokens &&
            "hood" in tokens
        ) {
            return "HOOD_OPEN"
        }

        if (
            "gas" in tokens &&
            "cap" in tokens
        ) {
            return "GAS_CAP_OPEN"
        }

        if (
            "fuel" in tokens &&
            "door" in tokens
        ) {
            return "GAS_CAP_OPEN"
        }

        // --------------------------------------------------------
        // CLIMATE
        // --------------------------------------------------------
        if (
            "ac" in tokens &&
            "on" in tokens
        ) {
            return "AC_ON"
        }

        if (
            "ac" in tokens &&
            "off" in tokens
        ) {
            return "AC_OFF"
        }

        if (
            "fan" in tokens &&
            "up" in tokens
        ) {
            return "FAN_UP"
        }

        if (
            "fan" in tokens &&
            "down" in tokens
        ) {
            return "FAN_DOWN"
        }

        if (
            hasTemperatureToken(tokens) &&
            "up" in tokens
        ) {
            return "TEMP_UP"
        }

        if (
            hasTemperatureToken(tokens) &&
            "down" in tokens
        ) {
            return "TEMP_DOWN"
        }

        // --------------------------------------------------------
        // DEFROST AND DEFOGGER
        // --------------------------------------------------------
        if (
            "rear" in tokens &&
            hasDefoggerToken(tokens) &&
            "off" in tokens
        ) {
            return "DEFROST_REAR_OFF"
        }

        if (
            "rear" in tokens &&
            hasDefoggerToken(tokens)
        ) {
            return "DEFROST_REAR"
        }

        if (
            hasDefrostToken(tokens) &&
            "front" in tokens
        ) {
            return "DEFROST_FRONT"
        }

        if (
            hasDefrostToken(tokens) &&
            "rear" in tokens &&
            "off" in tokens
        ) {
            return "DEFROST_REAR_OFF"
        }

        if (
            hasDefrostToken(tokens) &&
            "rear" in tokens
        ) {
            return "DEFROST_REAR"
        }

        // --------------------------------------------------------
        // CLIMATE MODES
        // --------------------------------------------------------
        if (
            "auto" in tokens &&
            "climate" in tokens &&
            "on" in tokens
        ) {
            return "CLIMATE_AUTO_ON"
        }

        if (
            "auto" in tokens &&
            "climate" in tokens &&
            "off" in tokens
        ) {
            return "CLIMATE_AUTO_OFF"
        }

        if (
            "unsync" in tokens ||
            (
                "sync" in tokens &&
                "off" in tokens
            )
        ) {
            return "CLIMATE_SYNC_OFF"
        }

        if (
            "sync" in tokens &&
            (
                hasTemperatureToken(tokens) ||
                "on" in tokens
            )
        ) {
            return "CLIMATE_SYNC_ON"
        }

        if (
            "dual" in tokens &&
            "on" in tokens
        ) {
            return "CLIMATE_DUAL_ON"
        }

        if (
            "dual" in tokens &&
            "off" in tokens
        ) {
            return "CLIMATE_DUAL_OFF"
        }

        // --------------------------------------------------------
        // PARKING SENSORS
        // --------------------------------------------------------
        if (
            "parking" in tokens &&
            hasSensorToken(tokens)
        ) {
            if ("on" in tokens) {
                return "PARKING_SENSORS_ON"
            }

            if ("off" in tokens) {
                return "PARKING_SENSORS_OFF"
            }
        }

        // --------------------------------------------------------
        // DASH BRIGHTNESS
        // --------------------------------------------------------
        if (
            "dash" in tokens &&
            (
                "bright" in tokens ||
                "brighter" in tokens
            )
        ) {
            return "DASH_BRIGHTNESS_UP"
        }

        if (
            "dash" in tokens &&
            (
                "dim" in tokens ||
                "dimmer" in tokens ||
                "dark" in tokens ||
                "darker" in tokens
            )
        ) {
            return "DASH_BRIGHTNESS_DOWN"
        }

        // --------------------------------------------------------
        // LANEWATCH
        // --------------------------------------------------------
        if (
            (
                "lanewatch" in tokens ||
                (
                    "lane" in tokens &&
                    "watch" in tokens
                )
            )
        ) {
            if ("on" in tokens) {
                return "LANEWATCH_ON"
            }

            if ("off" in tokens) {
                return "LANEWATCH_OFF"
            }
        }

        // --------------------------------------------------------
        // WIPERS AND WASHER
        // --------------------------------------------------------
        if (
            hasWiperToken(tokens) &&
            "on" in tokens
        ) {
            return "WIPERS_ON"
        }

        if (
            hasWiperToken(tokens) &&
            "off" in tokens
        ) {
            return "WIPERS_OFF"
        }

        if (
            "washer" in tokens ||
            "spray" in tokens
        ) {
            return "WASHER_SPRAY"
        }

        // --------------------------------------------------------
        // MIRRORS
        // --------------------------------------------------------
        if (
            "fold" in tokens &&
            hasMirrorToken(tokens)
        ) {
            return "MIRROR_FOLD"
        }

        if (
            "unfold" in tokens &&
            hasMirrorToken(tokens)
        ) {
            return "MIRROR_UNFOLD"
        }

        // --------------------------------------------------------
        // SEAT HEATING
        // --------------------------------------------------------
        if (
            hasSeatToken(tokens) &&
            hasHeatToken(tokens) &&
            "on" in tokens
        ) {
            return "SEAT_HEATER_ON"
        }

        if (
            hasSeatToken(tokens) &&
            hasHeatToken(tokens) &&
            "off" in tokens
        ) {
            return "SEAT_HEATER_OFF"
        }

        // --------------------------------------------------------
        // NAVIGATION VOICE
        // Safe ordering: unmute before mute
        // --------------------------------------------------------
        if (
            "navigation" in tokens &&
            "unmute" in tokens
        ) {
            return "NAV_VOICE_UNMUTE"
        }

        if (
            "navigation" in tokens &&
            "mute" in tokens
        ) {
            return "NAV_VOICE_MUTE"
        }

        // --------------------------------------------------------
        // AUDIO
        // Safe ordering: unmute before mute
        // --------------------------------------------------------
        if ("unmute" in tokens) {
            return "AUDIO_UNMUTE"
        }

        if ("mute" in tokens) {
            return "AUDIO_MUTE"
        }

        if (
            "volume" in tokens &&
            (
                "up" in tokens ||
                "increase" in tokens ||
                "raise" in tokens
            )
        ) {
            return "AUDIO_VOLUME_UP"
        }

        if (
            "volume" in tokens &&
            (
                "down" in tokens ||
                "decrease" in tokens ||
                "lower" in tokens
            )
        ) {
            return "AUDIO_VOLUME_DOWN"
        }

        // --------------------------------------------------------
        // AUDIO SOURCE
        // --------------------------------------------------------
        if (
            "bluetooth" in tokens &&
            "audio" in tokens
        ) {
            return "AUDIO_SOURCE_BT"
        }

        if (
            "fm" in tokens &&
            "radio" in tokens
        ) {
            return "AUDIO_SOURCE_FM"
        }

        if (
            "am" in tokens &&
            "radio" in tokens
        ) {
            return "AUDIO_SOURCE_AM"
        }

        if (
            "xm" in tokens ||
            "satellite" in tokens
        ) {
            return "AUDIO_SOURCE_XM"
        }

        if (
            "usb" in tokens &&
            "audio" in tokens
        ) {
            return "AUDIO_SOURCE_USB"
        }

        return null
    }

    // ============================================================
    // FINAL PHASE 1 SAFETY GATE
    // ============================================================
    private fun allowPhaseOneCommand(
        candidate: String?
    ): String {
        if (candidate == null) {
            return UNKNOWN_COMMAND
        }

        if (candidate !in phaseOneAllowlist) {
            return BLOCKED_OUT_OF_SCOPE_COMMAND
        }

        return candidate
    }

    // ============================================================
    // TOKEN HELPERS
    // ============================================================
    private fun hasWindowToken(
        tokens: Set<String>
    ): Boolean {
        return "window" in tokens ||
            "windows" in tokens
    }

    private fun hasHeadlightToken(
        tokens: Set<String>
    ): Boolean {
        return "headlight" in tokens ||
            "headlights" in tokens
    }

    private fun hasBeamToken(
        tokens: Set<String>
    ): Boolean {
        return "beam" in tokens ||
            "beams" in tokens
    }

    private fun hasLightToken(
        tokens: Set<String>
    ): Boolean {
        return "light" in tokens ||
            "lights" in tokens
    }

    private fun hasTemperatureToken(
        tokens: Set<String>
    ): Boolean {
        return "temp" in tokens ||
            "temperature" in tokens
    }

    private fun hasDefoggerToken(
        tokens: Set<String>
    ): Boolean {
        return "defogger" in tokens ||
            "defog" in tokens
    }

    private fun hasDefrostToken(
        tokens: Set<String>
    ): Boolean {
        return "defrost" in tokens ||
            "defroster" in tokens
    }

    private fun hasSensorToken(
        tokens: Set<String>
    ): Boolean {
        return "sensor" in tokens ||
            "sensors" in tokens
    }

    private fun hasWiperToken(
        tokens: Set<String>
    ): Boolean {
        return "wiper" in tokens ||
            "wipers" in tokens
    }

    private fun hasMirrorToken(
        tokens: Set<String>
    ): Boolean {
        return "mirror" in tokens ||
            "mirrors" in tokens
    }

    private fun hasSeatToken(
        tokens: Set<String>
    ): Boolean {
        return "seat" in tokens ||
            "seats" in tokens
    }

    private fun hasHeatToken(
        tokens: Set<String>
    ): Boolean {
        return "heat" in tokens ||
            "heater" in tokens ||
            "heated" in tokens
    }
}