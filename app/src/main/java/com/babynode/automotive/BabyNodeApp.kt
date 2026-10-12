package com.babynode.automotive

import android.app.Application
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class BabyNodeApp : Application() {

    override fun onCreate() {
        super.onCreate()

        instance =
            this
    }

    /**
     * Allocates a positive transport session generation that remains
     * unique across transport replacement and process restart.
     *
     * Persistence is synchronous so a generation is never returned
     * before its successor has been durably reserved.
     */
    @Synchronized
    fun allocateTransportSessionGeneration():
        Long {
        val storedNextGeneration =
            preferences.getLong(
                KEY_NEXT_TRANSPORT_SESSION_GENERATION,
                1L
            )

        val allocatedGeneration =
            storedNextGeneration.takeIf {
                it > 0L
            } ?: 1L

        val nextGeneration =
            if (
                allocatedGeneration ==
                Long.MAX_VALUE
            ) {
                throw IllegalStateException(
                    "Transport session generation exhausted"
                )
            } else {
                allocatedGeneration +
                    1L
            }

        val persisted =
            preferences
                .edit()
                .putLong(
                    KEY_NEXT_TRANSPORT_SESSION_GENERATION,
                    nextGeneration
                )
                .commit()

        check(
            persisted
        ) {
            "Transport session generation could not be persisted"
        }

        return allocatedGeneration
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

        return preferences
            .edit()
            .putString(
                KEY_APPROVED_BABYNODECAN_ADDRESS,
                normalizedAddress
            )
            .commit()
    }

    /**
     * Returns the previously selected BabyNodeCAN module type.
     *
     * Returns null when no module selection has been stored.
     */
    fun selectedModuleName():
        String? {
        return preferences
            .getString(
                KEY_SELECTED_MODULE,
                null
            )
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
    }

    /**
     * Persists the selected BabyNodeCAN module type.
     */
    fun saveSelectedModuleName(
        moduleName: String
    ): Boolean {
        val normalizedName =
            moduleName.trim()

        if (normalizedName.isBlank()) {
            return false
        }

        preferences
            .edit()
            .putString(
                KEY_SELECTED_MODULE,
                normalizedName
            )
            .apply()

        return true
    }

    /**
     * Persists request-specific terminal outcomes produced while
     * closing the active transport.
     *
     * Commands are recorded only for user visibility. They are never
     * submitted or replayed from this storage.
     */
    fun saveTerminalRequestOutcomes(
        events: List<CarStatusEvent>
    ): Boolean {
        val encodedEvents =
            JSONArray()

        events.forEach { event ->
            val sessionGeneration: Long
            val requestId: Long
            val command: CanonicalCommand
            val eventType: String

            val encodedEvent =
                JSONObject()

            when (event) {
                is CarStatusEvent.Error -> {
                    if (!event.isRequestSpecific) {
                        return@forEach
                    }

                    sessionGeneration =
                        event.sessionGeneration
                            ?: return@forEach
                    requestId =
                        event.requestId
                            ?: return@forEach
                    command =
                        event.command
                            ?: return@forEach
                    eventType =
                        EVENT_TYPE_ERROR

                    encodedEvent.put(
                        "message",
                        event.message
                    )
                }

                is CarStatusEvent.CommandResponse -> {
                    sessionGeneration =
                        event.sessionGeneration
                    requestId =
                        event.requestId
                    command =
                        event.command
                    eventType =
                        EVENT_TYPE_COMMAND_RESPONSE

                    encodedEvent.put(
                        "status",
                        event.status
                    )
                }

                else -> return@forEach
            }

            encodedEvent
                .put(
                    "event_type",
                    eventType
                )
                .put(
                    "session_generation",
                    sessionGeneration
                )
                .put(
                    "request_id",
                    requestId
                )
                .put(
                    "command",
                    command.command
                )

            command.value?.let { value ->
                encodedEvent.put(
                    "value",
                    value
                )
            }

            encodedEvents.put(
                encodedEvent
            )
        }

        return preferences
            .edit()
            .putString(
                KEY_TERMINAL_REQUEST_OUTCOMES,
                encodedEvents.toString()
            )
            .commit()
    }

    /**
     * Returns and removes terminal outcomes saved during prior
     * Activity destruction.
     *
     * Stored commands are reconstructed only as status information.
     * Nothing in this method performs transport submission.
     */
    fun consumeTerminalRequestOutcomes():
        List<CarStatusEvent> {
        val encodedEvents =
            preferences.getString(
                KEY_TERMINAL_REQUEST_OUTCOMES,
                null
            )
                ?: return emptyList()

        val cleared =
            clearTerminalRequestOutcomes()

        if (!cleared) {
            return emptyList()
        }

        return runCatching {
            val array =
                JSONArray(
                    encodedEvents
                )

            buildList {
                for (
                    index in
                    0 until array.length()
                ) {
                    val encodedEvent =
                        array.getJSONObject(
                            index
                        )

                    val sessionGeneration =
                        encodedEvent.getLong(
                            "session_generation"
                        )

                    val requestId =
                        encodedEvent.getLong(
                            "request_id"
                        )

                    val commandName =
                        encodedEvent.getString(
                            "command"
                        )

                    val eventType =
                        encodedEvent.getString(
                            "event_type"
                        )

                    val commandValue =
                        if (
                            encodedEvent.has(
                                "value"
                            )
                        ) {
                            encodedEvent.getString(
                                "value"
                            )
                        } else {
                            null
                        }

                    if (
                        BabyNodeProtocol.validateRequestId(
                            requestId
                        ) &&
                        commandName.isNotBlank()
                    ) {
                        val restoredCommand =
                            CanonicalCommand(
                                command =
                                    commandName,
                                value =
                                    commandValue
                            )

                        when (eventType) {
                            EVENT_TYPE_ERROR -> {
                                val message =
                                    encodedEvent.getString(
                                        "message"
                                    )

                                if (message.isNotBlank()) {
                                    add(
                                        CarStatusEvent.Error(
                                            message =
                                                message,
                                            sessionGeneration =
                                                sessionGeneration,
                                            requestId =
                                                requestId,
                                            command =
                                                restoredCommand
                                        )
                                    )
                                }
                            }

                            EVENT_TYPE_COMMAND_RESPONSE -> {
                                val status =
                                    encodedEvent.getString(
                                        "status"
                                    )

                                if (status.isNotBlank()) {
                                    add(
                                        CarStatusEvent.CommandResponse(
                                            sessionGeneration =
                                                sessionGeneration,
                                            requestId =
                                                requestId,
                                            command =
                                                restoredCommand,
                                            status =
                                                status
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    /**
     * Removes all persisted terminal request outcomes.
     */
    fun clearTerminalRequestOutcomes():
        Boolean {
        return preferences
            .edit()
            .remove(
                KEY_TERMINAL_REQUEST_OUTCOMES
            )
            .commit()
    }

    /**
     * Clears the stored BabyNodeCAN identity.
     *
     * This can later support an explicit user-facing action such as
     * "Forget approved BabyNodeCAN."
     */
    fun clearApprovedBabyNodeCanAddress():
        Boolean {
        return preferences
            .edit()
            .remove(
                KEY_APPROVED_BABYNODECAN_ADDRESS
            )
            .commit()
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

        private const val KEY_SELECTED_MODULE =
            "selected_babynodecan_module"

        private const val KEY_TERMINAL_REQUEST_OUTCOMES =
            "terminal_request_outcomes"

        private const val EVENT_TYPE_ERROR =
            "error"

        private const val EVENT_TYPE_COMMAND_RESPONSE =
            "command_response"

        private const val KEY_NEXT_TRANSPORT_SESSION_GENERATION =
            "next_transport_session_generation"

        private val BLUETOOTH_ADDRESS_PATTERN =
            Regex(
                """^[0-9A-F]{2}(:[0-9A-F]{2}){5}$"""
            )

        lateinit var instance:
            BabyNodeApp
            private set
    }
}