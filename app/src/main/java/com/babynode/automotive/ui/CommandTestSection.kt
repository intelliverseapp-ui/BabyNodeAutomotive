package com.babynode.automotive.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babynode.automotive.CarCommandDetector
import com.babynode.automotive.CarCommandDispatcher
import com.babynode.automotive.CarStatusEvent

private const val TAG = "CommandTestSection"

@Composable
fun CommandTestSection(
    dispatcher: CarCommandDispatcher,
    status: CarStatusEvent?
) {
    Log.i(TAG, "Render CommandTestSection(): latestEvent=$status")

    var commandText by remember { mutableStateOf("") }
    var localStatus by remember { mutableStateOf("Ready") }

    Column(modifier = Modifier.fillMaxWidth()) {

        Text(
            text = "Command Tester",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ⭐ Input field for manual automotive command
        TextField(
            value = commandText,
            onValueChange = {
                commandText = it
                Log.i(TAG, "User typed command: \"$commandText\"")
            },
            label = { Text("Enter automotive command") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ⭐ Send Command Button
        Button(
            onClick = {
                Log.i(TAG, "Send Command clicked: \"$commandText\"")

                if (CarCommandDetector.isAutomotive(commandText)) {
                    Log.i(TAG, "Command recognized as automotive → dispatching")
                    dispatcher.handle(commandText)
                    localStatus = "Executed: $commandText"
                } else {
                    Log.i(TAG, "Command NOT automotive → rejected")
                    localStatus = "Not an automotive command"
                }
            }
        ) {
            Text("Send Command")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ⭐ Status output (local + transport status)
        val statusText = buildString {
            append(localStatus)
            if (status != null) {
                append("\nTransport: ")
                append(
                    when (status) {
                        is CarStatusEvent.Connected ->
                            "Connected (${status.transportName})"
                        is CarStatusEvent.Disconnected ->
                            "Disconnected (${status.transportName})"
                        is CarStatusEvent.Error ->
                            "Error: ${status.message}"
                        is CarStatusEvent.FrameReceived ->
                            "Frame received: ID=${status.frame.id}"
                        is CarStatusEvent.FrameSent ->
                            "Frame sent: ID=${status.frame.id}"
                    }
                )
            }
        }

        Log.i(TAG, "Status text rendered: $statusText")

        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
