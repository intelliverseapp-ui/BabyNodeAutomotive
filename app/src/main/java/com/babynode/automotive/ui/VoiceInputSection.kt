package com.babynode.automotive.ui

import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.babynode.automotive.CarCommandDetector
import com.babynode.automotive.CarCommandDispatcher
import com.babynode.automotive.CarStatusEvent

@Composable
fun VoiceInputSection(
    dispatcher: CarCommandDispatcher,
    status: CarStatusEvent?
) {

    var recognizedText by remember { mutableStateOf("") }
    var localStatus by remember { mutableStateOf("Ready for voice command") }

    // ⭐ Android voice recognition launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->

        // ⭐ CRITICAL: confirm callback is firing
        Log.i("VoiceInputSection", "VOICE CALLBACK FIRED")

        val data = result.data
        val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val spoken = matches?.firstOrNull() ?: ""
        recognizedText = spoken

        if (spoken.isNotEmpty()) {
            if (CarCommandDetector.isAutomotive(spoken)) {
                dispatcher.handle(spoken)
                localStatus = "Executed: $spoken"
            } else {
                localStatus = "Not an automotive command"
            }
        } else {
            localStatus = "No speech detected"
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        Text(
            text = "Voice Input",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ⭐ Speak Command Button
        Button(
            onClick = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak automotive command")
                }
                voiceLauncher.launch(intent)
            }
        ) {
            Text("Speak Command")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ⭐ Show recognized text
        Text(
            text = "Heard: $recognizedText",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

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
