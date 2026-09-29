package com.babynode.automotive.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babynode.automotive.CarCommandDetector
import com.babynode.automotive.CarCommandDispatcher
import com.babynode.automotive.CarStatusEvent

@Composable
fun CommandTestSection(
    dispatcher: CarCommandDispatcher,
    status: CarStatusEvent?
) {

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
            onValueChange = { commandText = it },
            label = { Text("Enter automotive command") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ⭐ Send Command Button
        Button(
            onClick = {
                if (CarCommandDetector.isAutomotive(commandText)) {
                    dispatcher.handle(commandText)
                    localStatus = "Executed: $commandText"
                } else {
                    localStatus = "Not an automotive command"
                }
            }
        ) {
            Text("Send Command")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ⭐ Status output (local + transport status)
        Text(
            text = buildString {
                append(localStatus)
                if (status != null) {
                    append("\nTransport: ")
                    append(
                        when (status) {
                            is CarStatusEvent.Connected -> "Connected (${status.transportName})"
                            is CarStatusEvent.Disconnected -> "Disconnected (${status.transportName})"
                            is CarStatusEvent.Error -> "Error: ${status.message}"
                            is CarStatusEvent.FrameReceived -> "Frame received: ID=${status.frame.id}"
                            is CarStatusEvent.FrameSent -> "Frame sent: ID=${status.frame.id}"
                        }
                    )
                }
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
