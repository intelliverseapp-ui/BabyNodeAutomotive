package com.babynode.automotive.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.babynode.automotive.CarCommandDetector
import com.babynode.automotive.CarCommandDispatcher

@Composable
fun CommandTestSection() {

    var commandText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    val context = LocalContext.current

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
                    CarCommandDispatcher.handle(context, commandText)
                    status = "Executed: $commandText"
                } else {
                    status = "Not an automotive command"
                }
            }
        ) {
            Text("Send Command")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ⭐ Status output
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
