package com.babynode.automotive.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babynode.automotive.CarCommandDetector
import com.babynode.automotive.CarCommandDispatchResult
import com.babynode.automotive.CarCommandDispatcher
import com.babynode.automotive.CarCommandRequest
import com.babynode.automotive.CarStatusEvent

private const val TAG =
    "VoiceInputSection"

private enum class VoiceRequestState {
    READY,
    LISTENING,
    CANCELLED,
    NO_SPEECH,
    DISPATCHING,
    REJECTED,
    SUBMITTED,
    FAILED
}

@Composable
fun VoiceInputSection(
    dispatcher: CarCommandDispatcher,
    status: CarStatusEvent?
) {
    var recognizedText by remember {
        mutableStateOf("")
    }

    var requestState by remember {
        mutableStateOf(
            VoiceRequestState.READY
        )
    }

    var activeRequest by remember {
        mutableStateOf<CarCommandRequest?>(
            null
        )
    }

    var transportStatusForCurrentRequest by remember {
        mutableStateOf<CarStatusEvent?>(
            null
        )
    }

    var rejectionReason by remember {
        mutableStateOf<String?>(
            null
        )
    }

    var failureMessage by remember {
        mutableStateOf<String?>(
            null
        )
    }

    /*
     * Accept only events whose request ID and canonical command
     * match the exact voice request returned by the dispatcher.
     *
     * Automatic config.module traffic, typed commands, older voice
     * commands, duplicate responses, and unrelated transport events
     * are ignored by this section.
     */
    LaunchedEffect(
        status,
        activeRequest
    ) {
        val currentRequest =
            activeRequest

        val currentStatus =
            status

        if (
            currentRequest != null &&
            currentStatus != null &&
            eventMatchesRequest(
                event =
                    currentStatus,
                request =
                    currentRequest
            )
        ) {
            transportStatusForCurrentRequest =
                currentStatus
        }
    }

    val voiceLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.StartActivityForResult()
        ) { result ->

            activeRequest =
                null

            transportStatusForCurrentRequest =
                null

            rejectionReason =
                null

            failureMessage =
                null

            if (
                result.resultCode !=
                Activity.RESULT_OK
            ) {
                Log.i(
                    TAG,
                    "Voice recognition was cancelled or unsuccessful"
                )

                requestState =
                    VoiceRequestState.CANCELLED

                return@rememberLauncherForActivityResult
            }

            val matches =
                result.data
                    ?.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS
                    )

            val spoken =
                matches
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()

            recognizedText =
                spoken

            if (spoken.isBlank()) {
                Log.i(
                    TAG,
                    "Voice recognition returned no speech"
                )

                requestState =
                    VoiceRequestState.NO_SPEECH

                return@rememberLauncherForActivityResult
            }

            if (
                !CarCommandDetector.isAutomotive(
                    spoken
                )
            ) {
                Log.i(
                    TAG,
                    "Voice request rejected by Phase 1 detector"
                )

                rejectionReason =
                    "not a supported Phase 1 command"

                requestState =
                    VoiceRequestState.REJECTED

                return@rememberLauncherForActivityResult
            }

            Log.i(
                TAG,
                "Supported voice request awaiting dispatch result"
            )

            requestState =
                VoiceRequestState.DISPATCHING

            dispatcher.handle(
                text =
                    spoken,
                onResult = { dispatchResult ->
                    when (dispatchResult) {
                        is CarCommandDispatchResult.Submitted -> {
                            val request =
                                dispatchResult.request

                            activeRequest =
                                request

                            requestState =
                                VoiceRequestState.SUBMITTED

                            /*
                             * A very fast response may already be the
                             * latest global status by the time the
                             * dispatcher returns the request identity.
                             * Capture it immediately when it matches.
                             */
                            if (
                                status != null &&
                                eventMatchesRequest(
                                    event =
                                        status,
                                    request =
                                        request
                                )
                            ) {
                                transportStatusForCurrentRequest =
                                    status
                            }

                            Log.i(
                                TAG,
                                "Voice request correlated: " +
                                    "id=${request.requestId}, " +
                                    "command=${request.command.command}"
                            )
                        }

                        is CarCommandDispatchResult.Rejected -> {
                            activeRequest =
                                null

                            transportStatusForCurrentRequest =
                                null

                            rejectionReason =
                                dispatchRejectionText(
                                    dispatchResult.reason
                                )

                            requestState =
                                VoiceRequestState.REJECTED

                            Log.i(
                                TAG,
                                "Voice request rejected by dispatcher"
                            )
                        }

                        is CarCommandDispatchResult.Failed -> {
                            activeRequest =
                                null

                            transportStatusForCurrentRequest =
                                null

                            failureMessage =
                                dispatchResult.message

                            requestState =
                                VoiceRequestState.FAILED

                            Log.e(
                                TAG,
                                "Voice request dispatch failed"
                            )
                        }
                    }
                }
            )
        }

    Column(
        modifier =
            Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Voice Input",
            style =
                MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        Button(
            onClick = {
                Log.i(
                    TAG,
                    "Starting voice recognition"
                )

                recognizedText =
                    ""

                requestState =
                    VoiceRequestState.LISTENING

                activeRequest =
                    null

                transportStatusForCurrentRequest =
                    null

                rejectionReason =
                    null

                failureMessage =
                    null

                val recognitionIntent =
                    Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                    ).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )

                        putExtra(
                            RecognizerIntent.EXTRA_PROMPT,
                            "Speak an automotive command"
                        )
                    }

                voiceLauncher.launch(
                    recognitionIntent
                )
            }
        ) {
            Text(
                text = "Speak Command"
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    16.dp
                )
        )

        Text(
            text =
                if (recognizedText.isBlank()) {
                    "Heard: Nothing yet"
                } else {
                    "Heard: $recognizedText"
                },
            style =
                MaterialTheme.typography.bodyLarge
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        Text(
            text =
                voiceRequestStatusText(
                    requestState =
                        requestState,
                    rejectionReason =
                        rejectionReason,
                    failureMessage =
                        failureMessage
                ),
            style =
                MaterialTheme.typography.bodyMedium
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        Text(
            text =
                voiceTransportStatusText(
                    requestState =
                        requestState,
                    request =
                        activeRequest,
                    transportStatus =
                        transportStatusForCurrentRequest
                ),
            style =
                MaterialTheme.typography.bodyMedium
        )
    }
}

private fun eventMatchesRequest(
    event: CarStatusEvent,
    request: CarCommandRequest
): Boolean {
    return when (event) {
        is CarStatusEvent.CommandSent -> {
            event.requestId ==
                request.requestId &&
                event.command ==
                request.command
        }

        is CarStatusEvent.CommandResponse -> {
            event.requestId ==
                request.requestId &&
                event.command ==
                request.command
        }

        is CarStatusEvent.Error -> {
            event.requestId ==
                request.requestId &&
                event.command ==
                request.command
        }

        is CarStatusEvent.Connected -> {
            false
        }

        is CarStatusEvent.Disconnected -> {
            false
        }
    }
}

private fun dispatchRejectionText(
    reason: String
): String {
    return when (reason) {
        "empty_request" -> {
            "empty request"
        }

        "unknown_command" -> {
            "not a supported Phase 1 command"
        }

        "negated_command" -> {
            "negated command was not transmitted"
        }

        "out_of_scope_command" -> {
            "command is outside the Phase 1 safety scope"
        }

        "invalid_canonical_command" -> {
            "command failed safety validation"
        }

        else -> {
            "request was rejected"
        }
    }
}

private fun voiceRequestStatusText(
    requestState: VoiceRequestState,
    rejectionReason: String?,
    failureMessage: String?
): String {
    return when (requestState) {
        VoiceRequestState.READY -> {
            "Ready for voice command"
        }

        VoiceRequestState.LISTENING -> {
            "Listening..."
        }

        VoiceRequestState.CANCELLED -> {
            "Voice recognition cancelled"
        }

        VoiceRequestState.NO_SPEECH -> {
            "No speech detected"
        }

        VoiceRequestState.DISPATCHING -> {
            "Validating and transmitting request"
        }

        VoiceRequestState.REJECTED -> {
            "Request rejected: " +
                (
                    rejectionReason
                        ?: "request was not approved"
                )
        }

        VoiceRequestState.SUBMITTED -> {
            "Request submitted"
        }

        VoiceRequestState.FAILED -> {
            "Request failed: " +
                (
                    failureMessage
                        ?: "transport failure"
                )
        }
    }
}

private fun voiceTransportStatusText(
    requestState: VoiceRequestState,
    request: CarCommandRequest?,
    transportStatus: CarStatusEvent?
): String {
    when (requestState) {
        VoiceRequestState.READY -> {
            return "Transport: No voice request submitted"
        }

        VoiceRequestState.LISTENING -> {
            return "Transport: Waiting for voice request"
        }

        VoiceRequestState.CANCELLED,
        VoiceRequestState.NO_SPEECH -> {
            return "Transport: No command submitted"
        }

        VoiceRequestState.DISPATCHING -> {
            return "Transport: Preparing command request"
        }

        VoiceRequestState.REJECTED -> {
            return "Transport: No command transmitted"
        }

        VoiceRequestState.FAILED -> {
            return "Transport: Command was not transmitted"
        }

        VoiceRequestState.SUBMITTED -> {
            // Continue to request-specific transport rendering below.
        }
    }

    if (request == null) {
        return "Transport: Waiting for request identity"
    }

    return when (transportStatus) {
        null -> {
            "Transport: Waiting for command status " +
                "(id=${request.requestId})"
        }

        is CarStatusEvent.CommandSent -> {
            "Transport: Request sent to BabyNodeCAN: " +
                transportStatus.command.command +
                " (id=${transportStatus.requestId})"
        }

        is CarStatusEvent.CommandResponse -> {
            if (
                transportStatus.status.equals(
                    "ok",
                    ignoreCase = true
                )
            ) {
                "BabyNodeCAN: Request accepted " +
                    "(id=${transportStatus.requestId})"
            } else {
                "BabyNodeCAN: Response " +
                    transportStatus.status +
                    " (id=${transportStatus.requestId})"
            }
        }

        is CarStatusEvent.Error -> {
            "Transport: Failed: " +
                transportStatus.message +
                " (id=${request.requestId})"
        }

        is CarStatusEvent.Connected,
        is CarStatusEvent.Disconnected -> {
            /*
             * Connection events are intentionally excluded from
             * request-correlated voice status.
             */
            "Transport: Waiting for command status " +
                "(id=${request.requestId})"
        }
    }
}