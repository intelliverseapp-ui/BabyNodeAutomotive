package com.babynode.automotive.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.babynode.automotive.CarCommandDetector
import com.babynode.automotive.CarCommandDispatcher

@Composable
fun VoiceInputSection() {

    var recognizedText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready for voice command") }
    val context = LocalContext.current

    // ⭐ Android voice recognition launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val spoken = matches?.firstOrNull() ?: ""
        recognizedText = spoken

        if (spoken.isNotEmpty()) {
            // ⭐ Voice recognized — now wire into automotive pipeline
            if (CarCommandDetector.isAutomotive(spoken)) {
                CarCommandDispatcher.handle(context, spoken)
                status = "Executed: $spoken"
            } else {
                status = "Not an automotive command"
            }
        } else {
            status = "No speech detected"
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

        // ⭐ Status output
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
