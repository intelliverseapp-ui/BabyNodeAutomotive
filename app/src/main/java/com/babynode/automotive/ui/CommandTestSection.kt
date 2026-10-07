package com.babynode.automotive.ui

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
    "CommandTestSection"

private enum class TypedRequestState {
    READY,
    EDITING,
    REJECTED,
    SUBMITTED
}

@Composable
fun CommandTestSection(
    dispatcher: CarCommandDispatcher,
    status: CarStatusEvent?
) {
    var commandText by remember {
        mutableStateOf("")
    }

    var requestState by remember {
        mutableStateOf(
            TypedRequestState.READY
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
     * Associate transport events with this typed request only after
     * the typed request has been submitted.
     *
     * This prevents a voice-command acknowledgment from appearing
     * as the result of the typed-command section.
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
                     * current typed request.
                     */
                }
            }
        }
    }

    Column(
        modifier =
            Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Command Tester",
            style =
                MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        TextField(
            value =
                commandText,
            onValueChange = { updatedText ->
                commandText =
                    updatedText

                requestState =
                    if (updatedText.isBlank()) {
                        TypedRequestState.READY
                    } else {
                        TypedRequestState.EDITING
                    }

                /*
                 * Editing begins a new local request attempt.
                 * Clear any result associated with the preceding
                 * typed request.
                 */
                waitingForFreshTransportEvent =
                    false

                transportStatusForCurrentRequest =
                    null
            },
            label = {
                Text(
                    text = "Enter automotive command"
                )
            },
            singleLine = true,
            modifier =
                Modifier.fillMaxWidth()
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Button(
            enabled =
                commandText.isNotBlank(),
            onClick = {
                val submittedText =
                    commandText.trim()

                transportStatusForCurrentRequest =
                    null

                waitingForFreshTransportEvent =
                    false

                if (submittedText.isBlank()) {
                    requestState =
                        TypedRequestState.READY

                    return@Button
                }

                if (
                    !CarCommandDetector.isAutomotive(
                        submittedText
                    )
                ) {
                    Log.i(
                        TAG,
                        "Typed request rejected by Phase 1 detector"
                    )

                    requestState =
                        TypedRequestState.REJECTED

                    return@Button
                }

                Log.i(
                    TAG,
                    "Supported typed request submitted"
                )

                requestState =
                    TypedRequestState.SUBMITTED

                waitingForFreshTransportEvent =
                    true

                dispatcher.handle(
                    submittedText
                )
            }
        ) {
            Text(
                text = "Send Command"
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
                if (commandText.isBlank()) {
                    "Entered: Nothing yet"
                } else {
                    "Entered: $commandText"
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
                typedRequestStatusText(
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
                typedTransportStatusText(
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

private fun typedRequestStatusText(
    requestState: TypedRequestState
): String {
    return when (requestState) {
        TypedRequestState.READY -> {
            "Ready for typed command"
        }

        TypedRequestState.EDITING -> {
            "Ready to submit"
        }

        TypedRequestState.REJECTED -> {
            "Request rejected: not a supported Phase 1 command"
        }

        TypedRequestState.SUBMITTED -> {
            "Request submitted"
        }
    }
}

private fun typedTransportStatusText(
    requestState: TypedRequestState,
    transportStatus: CarStatusEvent?
): String {
    if (
        requestState == TypedRequestState.REJECTED
    ) {
        return "Transport: No command transmitted"
    }

    if (
        requestState == TypedRequestState.READY ||
        requestState == TypedRequestState.EDITING
    ) {
        return "Transport: No typed request submitted"
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