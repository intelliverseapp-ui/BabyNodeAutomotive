package com.babynode.automotive

import android.util.Log

/**
 * CarCommandDetector
 *
 * Determines whether natural-language input belongs to the
 * supported BabyNode Automotive Phase 1 command system.
 *
 * Responsibilities:
 * - Normalize speech-recognition text
 * - Detect supported automotive vocabulary
 * - Reject negated requests
 * - Reject commands outside the approved Phase 1 scope
 *
 * This detector does not:
 * - Generate canonical commands
 * - Contain vehicle-specific CAN mappings
 * - Transmit commands
 */
object CarCommandDetector {

    private const val TAG =
        "CarCommandDetector"

    // ============================================================
    // PHASE 1 AUTOMOTIVE VOCABULARY
    // ============================================================

    private val windowKeywords = listOf(
        "window",
        "windows",
        "driver window",
        "passenger window",
        "roll down",
        "roll up"
    )

    private val lockKeywords = listOf(
        "lock",
        "unlock",
        "door",
        "doors"
    )

    private val climateKeywords = listOf(
        "ac",
        "air conditioning",
        "fan",
        "climate",
        "heat",
        "heater",
        "cool",
        "temperature",
        "temp",
        "defrost",
        "defroster",
        "defog",
        "defogger",
        "rear defogger",
        "front defroster",
        "recirculate",
        "max ac",
        "max heat",
        "sync temperature",
        "dual climate"
    )

    private val lightingKeywords = listOf(
        "headlight",
        "headlights",
        "light",
        "lights",
        "bright lights",
        "high beam",
        "high beams",
        "fog light",
        "fog lights",
        "interior light",
        "interior lights",
        "dome light",
        "dome lights",
        "auto headlights",
        "automatic headlights"
    )

    private val bodyKeywords = listOf(
        "trunk",
        "hood",
        "gas cap",
        "fuel door"
    )

    private val roofKeywords = listOf(
        "sunroof",
        "moonroof"
    )

    private val wiperKeywords = listOf(
        "wiper",
        "wipers",
        "windshield wiper",
        "windshield wipers",
        "washer",
        "windshield washer",
        "spray"
    )

    private val mirrorKeywords = listOf(
        "mirror",
        "mirrors",
        "side mirror",
        "side mirrors",
        "fold mirror",
        "fold mirrors",
        "unfold mirror",
        "unfold mirrors"
    )

    private val seatKeywords = listOf(
        "seat heater",
        "seat heaters",
        "heated seat",
        "heated seats",
        "seat heat"
    )

    private val parkingKeywords = listOf(
        "parking sensor",
        "parking sensors"
    )

    private val cameraKeywords = listOf(
        "lanewatch",
        "lane watch"
    )

    private val dashKeywords = listOf(
        "dash brightness",
        "dashboard brightness",
        "brighten dash",
        "dim dash",
        "dimmer dash"
    )

    private val audioKeywords = listOf(
        "mute",
        "unmute",
        "volume",
        "volume up",
        "volume down",
        "increase volume",
        "decrease volume",
        "raise volume",
        "lower volume",
        "audio",
        "sound",
        "bluetooth audio",
        "fm radio",
        "am radio",
        "xm radio",
        "satellite radio",
        "usb audio",
        "navigation voice"
    )

    private val automotiveVocabulary =
        windowKeywords +
            lockKeywords +
            climateKeywords +
            lightingKeywords +
            bodyKeywords +
            roofKeywords +
            wiperKeywords +
            mirrorKeywords +
            seatKeywords +
            parkingKeywords +
            cameraKeywords +
            dashKeywords +
            audioKeywords

    // ============================================================
    // NORMALIZATION
    // ============================================================

    private fun normalizeText(
        text: String
    ): String {
        return text
            .lowercase()
            .replace(
                '’',
                '\''
            )
            .replace(
                Regex("""\bdon'?t\b"""),
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
    ): List<String> {
        if (normalizedText.isBlank()) {
            return emptyList()
        }

        return normalizedText
            .split(" ")
            .filter {
                it.isNotBlank()
            }
    }

    // ============================================================
    // NEGATION DETECTION
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
    // PHASE 1 SCOPE ENFORCEMENT
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
    // PHRASE MATCHING
    // ============================================================

    private fun containsPhrase(
        inputTokens: List<String>,
        keyword: String
    ): Boolean {
        val keywordTokens =
            tokenize(
                normalizeText(
                    keyword
                )
            )

        if (
            keywordTokens.isEmpty() ||
            keywordTokens.size > inputTokens.size
        ) {
            return false
        }

        if (keywordTokens.size == 1) {
            return keywordTokens.first() in inputTokens
        }

        return inputTokens
            .windowed(
                size = keywordTokens.size,
                step = 1,
                partialWindows = false
            )
            .any { tokenWindow ->
                tokenWindow == keywordTokens
            }
    }

    // ============================================================
    // PUBLIC DETECTION ENTRY POINT
    // ============================================================

    fun isAutomotive(
        text: String
    ): Boolean {
        val normalizedText =
            normalizeText(
                text
            )

        if (normalizedText.isBlank()) {
            Log.d(
                TAG,
                "Automotive input was empty"
            )

            return false
        }

        val inputTokens =
            tokenize(
                normalizedText
            )

        val tokenSet =
            inputTokens.toSet()

        if (
            containsNegation(
                normalizedText,
                tokenSet
            )
        ) {
            Log.w(
                TAG,
                "Negated automotive request rejected"
            )

            return false
        }

        if (
            containsOutOfScopeCommand(
                tokenSet
            )
        ) {
            Log.w(
                TAG,
                "Out-of-scope automotive request rejected"
            )

            return false
        }

        val matched =
            automotiveVocabulary.any { keyword ->
                containsPhrase(
                    inputTokens,
                    keyword
                )
            }

        if (matched) {
            Log.d(
                TAG,
                "Supported Phase 1 automotive intent detected"
            )
        } else {
            Log.d(
                TAG,
                "No supported Phase 1 automotive intent detected"
            )
        }

        return matched
    }
}