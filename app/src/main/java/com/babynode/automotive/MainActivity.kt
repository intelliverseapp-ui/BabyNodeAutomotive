package com.babynode.automotive

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.babynode.automotive.ui.theme.BabyNodeAutomotiveTheme

class MainActivity : ComponentActivity() {

    private val TAG = "BNA_MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // ⭐ Initialize CAN bus hardware (BNA only)
        CarCanBus.initialize(this)
        Log.i(TAG, "CarCanBus initialized")

        setContent {
            BabyNodeAutomotiveTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AutomotiveConsole(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AutomotiveConsole(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    val context = LocalContext.current

    Column(modifier = modifier.padding(16.dp)) {

        Text("BabyNode Automotive", modifier = Modifier.padding(bottom = 16.dp))

        TextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Enter automotive command") },
            modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)
        )

        Button(onClick = {
            if (CarCommandDetector.isAutomotive(text)) {
                CarCommandDispatcher.handle(context, text)
                status = "Executed: $text"
            } else {
                status = "Not an automotive command"
            }
        }) {
            Text("Send Command")
        }

        Text(
            text = status,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AutomotiveConsolePreview() {
    BabyNodeAutomotiveTheme {
        AutomotiveConsole()
    }
}
