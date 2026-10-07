package com.babynode.automotive.ui

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babynode.automotive.CarStatusEvent

private const val TAG = "CanDebugSection"

@Composable
fun CanDebugSection(
    eventHistory: List<CarStatusEvent>
) {
    Log.i(
        TAG,
        "Render CanDebugSection(): eventHistorySize=${eventHistory.size}"
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        Text(
            text = "CAN Debug",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(8.dp)
        ) {

            for (event in eventHistory) {

                val line = event.toLogLine()

                Log.i(
                    TAG,
                    "Render log line: $line"
                )

                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )
            }
        }
    }
}

private fun CarStatusEvent.toLogLine(): String =
    when (this) {

        is CarStatusEvent.Connected ->
            "CONNECTED: $transportName"

        is CarStatusEvent.Disconnected ->
            "DISCONNECTED: $transportName"

        is CarStatusEvent.Error ->
            "ERROR: $message"

        is CarStatusEvent.CommandSent ->
            "COMMAND SENT: ${command.command}"

        is CarStatusEvent.CommandResponse ->
            "COMMAND RESPONSE: id=$commandId status=$status"
    }