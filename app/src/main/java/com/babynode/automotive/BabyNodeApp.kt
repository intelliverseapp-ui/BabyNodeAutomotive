package com.babynode.automotive

import android.app.Application
import android.content.Context

class BabyNodeApp : Application() {

    override fun onCreate() {
        super.onCreate()

        instance =
            this
    }

    /**
     * Returns the Bluetooth address of the BabyNodeCAN device
     * previously approved by this application.
     *
     * Returns null when no device identity has been stored.
     */
    fun approvedBabyNodeCanAddress():
        String? {
        val storedAddress =
            preferences.getString(
                KEY_APPROVED_BABYNODECAN_ADDRESS,
                null
            )
                ?.trim()
                ?.uppercase()

        return if (
            storedAddress != null &&
            BLUETOOTH_ADDRESS_PATTERN.matches(
                storedAddress
            )
        ) {
            storedAddress
        } else {
            null
        }
    }

    /**
     * Stores the Bluetooth address of the uniquely identified,
     * bonded BabyNodeCAN device.
     *
     * Invalid address strings are rejected.
     */
    fun approveBabyNodeCanAddress(
        address: String
    ): Boolean {
        val normalizedAddress =
            address
                .trim()
                .uppercase()

        if (
            !BLUETOOTH_ADDRESS_PATTERN.matches(
                normalizedAddress
            )
        ) {
            return false
        }

        preferences
            .edit()
            .putString(
                KEY_APPROVED_BABYNODECAN_ADDRESS,
                normalizedAddress
            )
            .apply()

        return true
    }

    /**
     * Clears the stored BabyNodeCAN identity.
     *
     * This can later support an explicit user-facing action such as
     * "Forget approved BabyNodeCAN."
     */
    fun clearApprovedBabyNodeCanAddress() {
        preferences
            .edit()
            .remove(
                KEY_APPROVED_BABYNODECAN_ADDRESS
            )
            .apply()
    }

    private val preferences by lazy {
        getSharedPreferences(
            PREFERENCES_FILE_NAME,
            Context.MODE_PRIVATE
        )
    }

    companion object {
        private const val PREFERENCES_FILE_NAME =
            "com.babynode.automotive.device_identity"

        private const val KEY_APPROVED_BABYNODECAN_ADDRESS =
            "approved_babynodecan_bluetooth_address"

        private val BLUETOOTH_ADDRESS_PATTERN =
            Regex(
                """^[0-9A-F]{2}(:[0-9A-F]{2}){5}$"""
            )

        lateinit var instance:
            BabyNodeApp
            private set
    }
}