package com.babynode.automotive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babynode.automotive.CarCommandDispatcher
import com.babynode.automotive.CarStatusEvent
import kotlinx.coroutines.flow.Flow

@Composable
fun AutomotiveScreen(
    dispatcher: CarCommandDispatcher,
    statusEvents: Flow<CarStatusEvent>,
    onTransportSelected: (String) -> Unit = {}
) {

    val statusState by statusEvents.collectAsState(initial = null)

    var selectedTransport by remember { mutableStateOf("Mock") }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "Transport",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        expanded = true
                    }
                    .padding(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Select Transport",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedTransport,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Mock") },
                    onClick = {
                        selectedTransport = "Mock"
                        expanded = false
                        onTransportSelected("Mock")
                    }
                )
                DropdownMenuItem(
                    text = { Text("TCP") },
                    onClick = {
                        selectedTransport = "TCP"
                        expanded = false
                        onTransportSelected("TCP")
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        VoiceInputSection(
            dispatcher = dispatcher,
            status = statusState
        )
        Spacer(modifier = Modifier.height(24.dp))

        CommandTestSection(
            dispatcher = dispatcher,
            status = statusState
        )
        Spacer(modifier = Modifier.height(24.dp))

        CanDebugSection(
            status = statusState
        )
    }
}
