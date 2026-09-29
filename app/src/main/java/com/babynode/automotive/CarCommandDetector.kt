package com.babynode.automotive

import android.util.Log

/**
 * CarCommandDetector
 *
 * ONE RESPONSIBILITY:
 * Determine whether a natural-language voice command
 * belongs to the Automotive subsystem.
 *
 * Scalable keyword-table architecture.
 */
object CarCommandDetector {

    private const val TAG = "CarCommandDetector"

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
    // Detection (pure automotive)
    // ============================================================
    fun isAutomotive(text: String): Boolean {
        val t = text.lowercase()

        val match = automotiveVocabulary.any { keyword ->
            t.contains(keyword)
        }

        if (match) {
            Log.d(TAG, "Automotive match detected for: \"$text\"")
        } else {
            Log.d(TAG, "NO automotive match for: \"$text\"")
        }

        return match
    }
}
