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
    private val closedPhraseGrammar =
        mapOf(
            "unlock the doors" to
                "UNLOCK_DOORS",
            "lock the doors" to
                "LOCK_DOORS",
            "open the trunk" to
                "TRUNK_OPEN",
            "driver window down" to
                "WINDOW_DRIVER_DOWN",
            "turn headlights on" to
                "HEADLIGHTS_ON"
        )

    private val embeddedQuestionMarkers =
        setOf(
            "whether",
            "wonder",
            "explain",
            "question",
            "should"
        )

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
    /**
     * Rejects syntax that describes, quotes, mentions, or questions
     * a command rather than expressing one approved imperative.
     *
     * This check runs before punctuation normalization so unsafe
     * syntax cannot be erased into an allowed phrase.
     */
    private fun containsDisallowedIntentSyntax(
        text: String
    ): Boolean {
        val trimmedText =
            text.trim()

        if (trimmedText.isBlank()) {
            return false
        }

        if (
            '?' in trimmedText ||
            '@' in trimmedText ||
            '"' in trimmedText ||
            '“' in trimmedText ||
            '”' in trimmedText
        ) {
            return true
        }

        val wrappedInSingleQuotes =
            trimmedText.length >=
                2 &&
                (
                    (
                        trimmedText.first() ==
                        '\'' &&
                        trimmedText.last() ==
                        '\''
                    ) ||
                    (
                        trimmedText.first() ==
                        '‘' &&
                        trimmedText.last() ==
                        '’'
                    )
                )

        return wrappedInSingleQuotes
    }

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
                Regex("""\bcan['’]?t\b"""),
                "can not"
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
                it == "stop" ||
                it == "cancel" ||
                it == "ignore" ||
                it == "wait" ||
                it == "undo" ||
                it == "without" ||
                it == "instead"
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
        if (
            containsDisallowedIntentSyntax(
                text
            )
        ) {
            return UNKNOWN_COMMAND
        }

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

        if (tokens.size == 1) {
            return UNKNOWN_COMMAND
        }

        val questionStarters =
            setOf(
                "can",
                "could",
                "would",
                "is",
                "are",
                "was",
                "were",
                "did",
                "does",
                "has",
                "have",
                "what",
                "when",
                "where",
                "who",
                "why",
                "how"
            )

        val firstToken =
            normalizedText
                .substringBefore(" ")

        if (firstToken in questionStarters) {
            return UNKNOWN_COMMAND
        }

        if (
            "and" in tokens ||
            "then" in tokens ||
            "also" in tokens
        ) {
            return UNKNOWN_COMMAND
        }

        val majorActionCount =
            listOf(
                "lock",
                "unlock",
                "open"
            ).count {
                it in tokens
            }

        if (majorActionCount > 1) {
            return UNKNOWN_COMMAND
        }

        if (
            (
                "lock" in tokens ||
                "unlock" in tokens
            ) &&
            "door" !in tokens &&
            "doors" !in tokens
        ) {
            return UNKNOWN_COMMAND
        }

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

        if (
            tokens.any {
                it in embeddedQuestionMarkers
            }
        ) {
            return UNKNOWN_COMMAND
        }

        val candidate =
            closedPhraseGrammar[
                normalizedText
            ]

        return allowPhaseOneCommand(
            candidate
        )
    }

    // ============================================================
    // SHARED PHASE 1 POLICY
    // ============================================================
    fun isPhaseOneCommand(
        command: String
    ): Boolean {
        return command in phaseOneAllowlist
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

        if (!isPhaseOneCommand(candidate)) {
            return BLOCKED_OUT_OF_SCOPE_COMMAND
        }

        return candidate
    }
}
