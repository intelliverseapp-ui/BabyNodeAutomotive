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
import com.babynode.automotive.CarCommandDispatcher
import com.babynode.automotive.CarStatusEvent

private const val TAG =
    "VoiceInputSection"

private enum class VoiceRequestState {
    READY,
    LISTENING,
    CANCELLED,
    NO_SPEECH,
    REJECTED,
    SUBMITTED
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

    var transportStatusForCurrentRequest by remember {
        mutableStateOf<CarStatusEvent?>(
            null
        )
    }

    var waitingForFreshTransportEvent by remember {
        mutableStateOf(
            false
        )
    }

    /*
     * A transport event is associated with the current voice request
     * only after that request has been submitted. This prevents an
     * acknowledgment from an earlier request from appearing beneath
     * a newly rejected or cancelled voice request.
     */
    LaunchedEffect(
        status
    ) {
        if (
            waitingForFreshTransportEvent &&
            status != null
        ) {
            when (status) {
                is CarStatusEvent.CommandSent,
                is CarStatusEvent.CommandResponse,
                is CarStatusEvent.Error,
                is CarStatusEvent.Disconnected -> {
                    transportStatusForCurrentRequest =
                        status
                }

                is CarStatusEvent.Connected -> {
                    /*
                     * A connection event is not the result of the
                     * submitted voice request, so it is not shown as
                     * that request's transport outcome.
                     */
                }
            }
        }
    }

    val voiceLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.StartActivityForResult()
        ) { result ->

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

                waitingForFreshTransportEvent =
                    false

                transportStatusForCurrentRequest =
                    null

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

            transportStatusForCurrentRequest =
                null

            waitingForFreshTransportEvent =
                false

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

                requestState =
                    VoiceRequestState.REJECTED

                return@rememberLauncherForActivityResult
            }

            Log.i(
                TAG,
                "Supported voice request submitted"
            )

            requestState =
                VoiceRequestState.SUBMITTED

            waitingForFreshTransportEvent =
                true

            dispatcher.handle(
                spoken
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

                waitingForFreshTransportEvent =
                    false

                transportStatusForCurrentRequest =
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
                    requestState
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
                    transportStatus =
                        transportStatusForCurrentRequest
                ),
            style =
                MaterialTheme.typography.bodyMedium
        )
    }
}

private fun voiceRequestStatusText(
    requestState: VoiceRequestState
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

        VoiceRequestState.REJECTED -> {
            "Request rejected: not a supported Phase 1 command"
        }

        VoiceRequestState.SUBMITTED -> {
            "Request submitted"
        }
    }
}

private fun voiceTransportStatusText(
    requestState: VoiceRequestState,
    transportStatus: CarStatusEvent?
): String {
    if (
        requestState == VoiceRequestState.REJECTED
    ) {
        return "Transport: No command transmitted"
    }

    if (
        requestState == VoiceRequestState.CANCELLED ||
        requestState == VoiceRequestState.NO_SPEECH
    ) {
        return "Transport: No command submitted"
    }

    if (
        requestState == VoiceRequestState.LISTENING
    ) {
        return "Transport: Waiting for voice request"
    }

    if (
        requestState == VoiceRequestState.READY
    ) {
        return "Transport: No voice request submitted"
    }

    return when (transportStatus) {
        null -> {
            "Transport: Waiting for command status"
        }

        is CarStatusEvent.Connected -> {
            "Transport: Connected to " +
                transportStatus.transportName
        }

        is CarStatusEvent.Disconnected -> {
            "Transport: Disconnected from " +
                transportStatus.transportName
        }

        is CarStatusEvent.Error -> {
            "Transport: Failed: " +
                transportStatus.message
        }

        is CarStatusEvent.CommandSent -> {
            "Transport: Request sent to BabyNodeCAN: " +
                transportStatus.command.command
        }

        is CarStatusEvent.CommandResponse -> {
            if (
                transportStatus.status.equals(
                    "ok",
                    ignoreCase = true
                )
            ) {
                "BabyNodeCAN: Request accepted " +
                    "(id=${transportStatus.commandId})"
            } else {
                "BabyNodeCAN: Response " +
                    transportStatus.status +
                    " (id=${transportStatus.commandId})"
            }
        }
    }
}