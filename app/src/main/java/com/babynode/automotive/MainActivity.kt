package com.babynode.automotive

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.babynode.automotive.ui.AutomotiveScreen
import com.babynode.automotive.ui.theme.BabyNodeAutomotiveTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ModuleType(val uiLabel: String, val jsonId: String, val ip: String, val port: Int) {
    SINGLE_CAN("Single-CAN Module (ESP32-S3 WROOM)", "single", "10.84.212.50", 1234),
    DUAL_CAN("Dual-CAN Module (DuoCAN-C6)", "dual", "10.84.212.50", 1234)
}

class MainActivity : ComponentActivity() {

    private val TAG = "BNA_MainActivity"

    private val transportMutex = Mutex()
    private lateinit var transport: CarCanTransport
    private lateinit var dispatcher: CarCommandDispatcher
    private val statusBus = CarStatusBus()
    private var transportEventsJob: Job? = null

    private var selectedModule: ModuleType = ModuleType.SINGLE_CAN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "onCreate(): Activity starting")

        enableEdgeToEdge()

        // INITIAL TRANSPORT
        val initialTransport = CarCanBusTcp(
            host = selectedModule.ip,
            port = selectedModule.port,
            scope = lifecycleScope
        )

        transport = initialTransport
        dispatcher = CarCommandDispatcher(lifecycleScope, initialTransport)
        observeTransport(initialTransport)

        lifecycleScope.launch {
            transportMutex.withLock {
                statusBus.markConnecting("TCP")
                initialTransport.connect()
            }
        }

        setContent {
            BabyNodeAutomotiveTheme {
                var currentDispatcher by remember { mutableStateOf(dispatcher) }
                var currentModule by remember { mutableStateOf(selectedModule) }

                val connectionState by statusBus.connectionState.collectAsState()
                val latestEvent by statusBus.latestEvent.collectAsState()
                val eventHistory by statusBus.events.collectAsState()

                Scaffold(
                    modifier = Modifier
                ) { innerPadding ->
                    AutomotiveScreen(
                        dispatcher = currentDispatcher,
                        moduleType = currentModule,
                        connectionState = connectionState,
                        status = latestEvent,
                        eventHistory = eventHistory,
                        modifier = Modifier.padding(innerPadding),
                        onModuleSelected = { module ->
                            Log.i(TAG, "UI: Module selected → ${module.uiLabel}")

                            if (module != currentModule) {
                                currentModule = module
                                selectedModule = module

                                lifecycleScope.launch {
                                    transportMutex.withLock {

                                        // CLOSE OLD TRANSPORT
                                        Log.i(TAG, "UI: Closing old TCP transport")
                                        transport.close()
                                        transportEventsJob?.cancel()

                                        // CREATE NEW TRANSPORT
                                        Log.i(TAG, "UI: Creating new TCP transport for ${module.uiLabel}")
                                        val newTransport = CarCanBusTcp(
                                            host = module.ip,
                                            port = module.port,
                                            scope = lifecycleScope
                                        )

                                        transport = newTransport
                                        dispatcher = CarCommandDispatcher(lifecycleScope, newTransport)
                                        currentDispatcher = dispatcher

                                        observeTransport(newTransport)

                                        // CONNECT NEW TRANSPORT
                                        Log.i(TAG, "UI: Connecting new TCP transport")
                                        statusBus.markConnecting("TCP")
                                        newTransport.connect()

                                        // SEND MODULE CONFIG
                                        Log.i(TAG, "UI: Sending module config → ${module.jsonId}")
                                        dispatcher.sendModuleConfig(module.jsonId)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // ============================================================
    // GEMINI / ASSISTANT INGESTION
    // ============================================================
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        Log.i(TAG, "onNewIntent(): Received intent → ${intent.action}")

        // ⭐ Google Assistant deep‑link handling
        intent.dataString?.let { data ->
            Log.i(TAG, "Assistant deep link → $data")

            when (data) {
                "bn://unlock_doors" -> {
                    Log.i(TAG, "Assistant: unlock_doors")
                    dispatcher.handle("unlock the doors")
                }
                "bn://lock_doors" -> {
                    Log.i(TAG, "Assistant: lock_doors")
                    dispatcher.handle("lock the doors")
                }
            }
        }

        when (intent.action) {

            Intent.ACTION_ASSIST -> {
                Log.i(TAG, "ACTION_ASSIST received from Gemini")

                val query = intent.getStringExtra(Intent.EXTRA_ASSIST_CONTEXT)
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: ""

                if (query.isNotBlank()) {
                    Log.i(TAG, "Gemini text → \"$query\"")

                    if (CarCommandDetector.isAutomotive(query)) {
                        Log.i(TAG, "Forwarding automotive command to dispatcher")
                        dispatcher.handle(query)
                    } else {
                        Log.i(TAG, "Gemini text is NOT automotive → ignoring")
                    }
                }
            }

            Intent.ACTION_PROCESS_TEXT -> {
                Log.i(TAG, "ACTION_PROCESS_TEXT received from Gemini")

                val query = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""

                if (query.isNotBlank()) {
                    Log.i(TAG, "Gemini text → \"$query\"")

                    if (CarCommandDetector.isAutomotive(query)) {
                        Log.i(TAG, "Forwarding automotive command to dispatcher")
                        dispatcher.handle(query)
                    } else {
                        Log.i(TAG, "Gemini text is NOT automotive → ignoring")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        transportEventsJob?.cancel()
        if (::transport.isInitialized) {
            transport.close()
        }
        super.onDestroy()
    }

    private fun observeTransport(activeTransport: CarCanTransport) {
        transportEventsJob = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            activeTransport.status().collect { event ->
                statusBus.accept(event)
            }
        }
    }
}
