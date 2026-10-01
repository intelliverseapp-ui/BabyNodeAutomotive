package com.babynode.automotive.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babynode.automotive.CarStatusEvent

private const val TAG = "CanDebugSection"

@Composable
fun CanDebugSection(
    eventHistory: List<CarStatusEvent>
) {
    Log.i(TAG, "Render CanDebugSection(): eventHistorySize=${eventHistory.size}")

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
            for (event in eventHistory) {
                val line = event.toLogLine()
                Log.i(TAG, "Render log line: $line")

                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

private fun CarStatusEvent.toLogLine(): String = when (this) {
    is CarStatusEvent.Connected -> "CONNECTED: $transportName"
    is CarStatusEvent.Disconnected -> "DISCONNECTED: $transportName"
    is CarStatusEvent.Error -> "ERROR: $message"
    is CarStatusEvent.FrameSent ->
        "TX ID=${frame.id}  DATA=${frame.data.joinToString(" ")}"
    is CarStatusEvent.FrameReceived ->
        "RX ID=${frame.id}  DATA=${frame.data.joinToString(" ")}"
}
