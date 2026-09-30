package com.babynode.automotive.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babynode.automotive.CarStatusEvent

@Composable
fun CanDebugSection(
    status: CarStatusEvent?
) {

    // ⭐ Unified CAN + Transport log buffer
    val log = remember { mutableStateListOf<String>() }

    // ⭐ Update log whenever a new status event arrives
    LaunchedEffect(status) {
        when (status) {

            is CarStatusEvent.Connected -> {
                log.add("CONNECTED: ${status.transportName}")
            }

            is CarStatusEvent.Disconnected -> {
                log.add("DISCONNECTED: ${status.transportName}")
            }

            is CarStatusEvent.Error -> {
                val msg = status.message
                log.add("ERROR: $msg")
            }

            is CarStatusEvent.FrameSent -> {
                val frame = status.frame
                val formatted = "TX ID=${frame.id}  DATA=${frame.data.joinToString(" ")}"
                log.add(formatted)
            }

            is CarStatusEvent.FrameReceived -> {
                val frame = status.frame
                val formatted = "RX ID=${frame.id}  DATA=${frame.data.joinToString(" ")}"
                log.add(formatted)
            }

            null -> {}
        }

        // ⭐ Keep log from growing forever
        if (log.size > 200) {
            log.removeFirst()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        Text(
            text = "CAN Debug",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ⭐ Scrolling CAN + Transport log window
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            for (line in log) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
