package com.babynode.automotive

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.babynode.automotive.ui.AutomotiveScreen
import com.babynode.automotive.ui.theme.BabyNodeAutomotiveTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val TAG = "BNA_MainActivity"

    // Coroutine scope for transport + dispatcher
    private val scope = MainScope()

    // Transport abstraction (switchable)
    private lateinit var transport: CarCanTransport

    // Unified automotive status bus
    private lateinit var statusBus: CarStatusBus

    // Command dispatcher (natural language → canonical → CAN frame → transport)
    private lateinit var dispatcher: CarCommandDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // ⭐ Default transport (MOCK)
        transport = CarCanBusMock(scope)
        statusBus = CarStatusBus(scope)
        dispatcher = CarCommandDispatcher(scope, transport)

        Log.i(TAG, "Transport + dispatcher initialized (MOCK)")

        // ⭐ Connect default transport
        scope.launch {
            transport.connect()
        }

        setContent {
            BabyNodeAutomotiveTheme {

                // ⭐ Transport selection state
                var selectedTransport by remember { mutableStateOf("Mock") }

                // ⭐ Transport switching logic
                LaunchedEffect(selectedTransport) {
                    scope.launch {
                        transport.disconnect()
                    }

                    transport = when (selectedTransport) {
                        "TCP" -> CarCanBusTcp("192.168.4.1", 1234)
                        else -> CarCanBusMock(scope)
                    }

                    dispatcher = CarCommandDispatcher(scope, transport)

                    scope.launch {
                        transport.connect()
                    }

                    Log.i(TAG, "Transport switched to: $selectedTransport")
                }

                Scaffold(
                    modifier = Modifier
                ) { innerPadding ->

                    // ⭐ Mount the unified AutomotiveScreen
                    AutomotiveScreen(
                        dispatcher = dispatcher,
                        statusEvents = statusBus.eventsFromTransport(transport),
                        onTransportSelected = { selected ->
                            selectedTransport = selected
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // ⭐ Disconnect transport cleanly
        scope.launch {
            transport.disconnect()
        }
    }
}
