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
import com.babynode.automotive.CarCommandDispatchResult
import com.babynode.automotive.CarCommandDispatcher
import com.babynode.automotive.CarCommandRequest
import com.babynode.automotive.CarStatusEvent

private const val TAG =
    "CommandTestSection"

private enum class TypedRequestState {
    READY,
    EDITING,
    DISPATCHING,
    REJECTED,
    SUBMITTED,
    FAILED
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
     * Accept only transport events whose request ID and canonical
     * command match the exact typed request returned by the
     * dispatcher.
     *
     * Voice requests, automatic config.module requests, previous
     * typed requests, late responses, and unrelated transport events
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
            eventMatchesTypedRequest(
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
                 * Editing starts a new local request attempt.
                 * Clear all identity and status belonging to the
                 * preceding typed request.
                 */
                activeRequest =
                    null

                transportStatusForCurrentRequest =
                    null

                rejectionReason =
                    null

                failureMessage =
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
                commandText.isNotBlank() &&
                    requestState !=
                    TypedRequestState.DISPATCHING,
            onClick = {
                val submittedText =
                    commandText.trim()

                activeRequest =
                    null

                transportStatusForCurrentRequest =
                    null

                rejectionReason =
                    null

                failureMessage =
                    null

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

                    rejectionReason =
                        "not a supported Phase 1 command"

                    requestState =
                        TypedRequestState.REJECTED

                    return@Button
                }

                Log.i(
                    TAG,
                    "Supported typed request awaiting dispatch result"
                )

                requestState =
                    TypedRequestState.DISPATCHING

                dispatcher.handle(
                    text =
                        submittedText,
                    onResult = { dispatchResult ->
                        when (dispatchResult) {
                            is CarCommandDispatchResult.Submitted -> {
                                val request =
                                    dispatchResult.request

                                activeRequest =
                                    request

                                requestState =
                                    TypedRequestState.SUBMITTED

                                /*
                                 * A fast response may already be the
                                 * latest global event when the request
                                 * identity reaches this callback.
                                 */
                                if (
                                    status != null &&
                                    eventMatchesTypedRequest(
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
                                    "Typed request correlated: " +
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
                                    typedDispatchRejectionText(
                                        dispatchResult.reason
                                    )

                                requestState =
                                    TypedRequestState.REJECTED

                                Log.i(
                                    TAG,
                                    "Typed request rejected by dispatcher"
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
                                    TypedRequestState.FAILED

                                Log.e(
                                    TAG,
                                    "Typed request dispatch failed"
                                )
                            }
                        }
                    }
                )
            }
        ) {
            Text(
                text =
                    if (
                        requestState ==
                        TypedRequestState.DISPATCHING
                    ) {
                        "Sending..."
                    } else {
                        "Send Command"
                    }
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
                typedTransportStatusText(
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

private fun eventMatchesTypedRequest(
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

private fun typedDispatchRejectionText(
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

private fun typedRequestStatusText(
    requestState: TypedRequestState,
    rejectionReason: String?,
    failureMessage: String?
): String {
    return when (requestState) {
        TypedRequestState.READY -> {
            "Ready for typed command"
        }

        TypedRequestState.EDITING -> {
            "Ready to submit"
        }

        TypedRequestState.DISPATCHING -> {
            "Validating and transmitting request"
        }

        TypedRequestState.REJECTED -> {
            "Request rejected: " +
                (
                    rejectionReason
                        ?: "request was not approved"
                )
        }

        TypedRequestState.SUBMITTED -> {
            "Request submitted"
        }

        TypedRequestState.FAILED -> {
            "Request failed: " +
                (
                    failureMessage
                        ?: "transport failure"
                )
        }
    }
}

private fun typedTransportStatusText(
    requestState: TypedRequestState,
    request: CarCommandRequest?,
    transportStatus: CarStatusEvent?
): String {
    when (requestState) {
        TypedRequestState.READY,
        TypedRequestState.EDITING -> {
            return "Transport: No typed request submitted"
        }

        TypedRequestState.DISPATCHING -> {
            return "Transport: Preparing command request"
        }

        TypedRequestState.REJECTED -> {
            return "Transport: No command transmitted"
        }

        TypedRequestState.FAILED -> {
            return "Transport: Command was not transmitted"
        }

        TypedRequestState.SUBMITTED -> {
            /*
             * Continue to the request-correlated transport status.
             */
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
             * General connection events do not belong to this typed
             * request and are not rendered as its outcome.
             */
            "Transport: Waiting for command status " +
                "(id=${request.requestId})"
        }
    }
}