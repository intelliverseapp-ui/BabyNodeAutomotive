package com.babynode.automotive

import android.util.Log

/**
 * CarCommandDetector
 *
 * ONE RESPONSIBILITY:
 * Safely determine whether a natural-language voice command
 * belongs to the Automotive subsystem.
 *
 * Hardened against:
 * - negation ("don't unlock", "do not turn on headlights")
 * - substring false-positives ("unmute" containing "mute")
 * - ambiguous phrasing
 *
 * This detector ONLY determines automotive intent.
 * It does NOT map commands to CAN frames.
 */
object CarCommandDetector {

    private const val TAG = "CarCommandDetector"

    // ============================================================
    // NEGATION / BLOCKERS (HIGH SAFETY)
    // ============================================================
    private val negationTokens = listOf(
        "don't", "do not", "stop", "cancel", "no", "not", "never"
    )

    private fun containsNegation(text: String): Boolean {
        val t = text.lowercase()
        return negationTokens.any { t.contains(it) }
    }

    // ============================================================
    // WINDOWS
    // ============================================================
    private val windowKeywords = listOf(
        "window", "driver window", "passenger window",
        "roll down", "roll up"
    )

    // ============================================================
    // LOCKS
    // ============================================================
    private val lockKeywords = listOf(
        "lock", "unlock", "door", "doors"
    )

    // ============================================================
    // CLIMATE / AC / HEAT
    // ============================================================
    private val climateKeywords = listOf(
        "ac", "air conditioning", "fan", "climate",
        "heat", "cool", "temperature", "temp",
        "defrost", "defog", "rear defogger", "front defogger",
        "recirculate", "max ac", "max heat"
    )

    // ============================================================
    // LIGHTING
    // ============================================================
    private val lightingKeywords = listOf(
        "headlights", "headlight", "lights",
        "bright lights", "high beams", "high beam",
        "fog lights", "fog light",
        "interior lights", "interior light",
        "dome light"
    )

    // ============================================================
    // BODY (TRUNK / HOOD / GAS CAP)
    // ============================================================
    private val bodyKeywords = listOf(
        "trunk", "hood", "gas cap", "fuel door"
    )

    // ============================================================
    // ROOF (SUNROOF / MOONROOF)
    // ============================================================
    private val roofKeywords = listOf(
        "sunroof", "moonroof", "roof"
    )

    // ============================================================
    // WIPERS / WASHER
    // ============================================================
    private val wiperKeywords = listOf(
        "wipers", "wiper", "windshield", "washer", "spray"
    )

    // ============================================================
    // MIRRORS
    // ============================================================
    private val mirrorKeywords = listOf(
        "mirror", "side mirror", "fold mirror", "unfold mirror"
    )

    // ============================================================
    // SEATS
    // ============================================================
    private val seatKeywords = listOf(
        "seat heater", "heated seat", "cooling seat",
        "seat heat"
    )

    // ============================================================
    // AUDIO / INFOTAINMENT
    // ============================================================
    private val audioKeywords = listOf(
        "mute", "unmute",
        "volume", "volume up", "volume down",
        "increase volume", "decrease volume",
        "raise volume", "lower volume",
        "audio", "sound"
    )

    // ============================================================
    // Combined automotive vocabulary
    // ============================================================
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
        audioKeywords

    // ============================================================
    // Token-based matching (safe)
    // ============================================================
    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .split(" ")
            .filter { it.isNotBlank() }
    }

    // ============================================================
    // Detection (safe automotive intent)
    // ============================================================
    fun isAutomotive(text: String): Boolean {
        val lower = text.lowercase()

        // ------------------------------------------------------------
        // SAFETY: Negation blocks automotive intent
        // ------------------------------------------------------------
        if (containsNegation(lower)) {
            Log.w(TAG, "Negated automotive command ignored: \"$text\"")
            return false
        }

        val tokens = tokenize(lower)

        // ------------------------------------------------------------
        // PRIORITY: Match whole tokens, not substrings
        // ------------------------------------------------------------
        val match = automotiveVocabulary.any { keyword ->
            val keywordTokens = tokenize(keyword)
            keywordTokens.all { it in tokens }
        }

        if (match) {
            Log.d(TAG, "Automotive match detected for: \"$text\"")
        } else {
            Log.d(TAG, "NO automotive match for: \"$text\"")
        }

        return match
    }
}
