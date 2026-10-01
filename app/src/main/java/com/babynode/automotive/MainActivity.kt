package com.babynode.automotive

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainActivity : ComponentActivity() {

    private val TAG = "BNA_MainActivity"

    private val transportMutex = Mutex()
    private lateinit var transport: CarCanTransport
    private lateinit var dispatcher: CarCommandDispatcher
    private val statusBus = CarStatusBus()
    private var transportEventsJob: Job? = null
    private var selectedTransport = "Mock"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "onCreate(): Activity starting")

        enableEdgeToEdge()

        // Initialize default transport
        val initialTransport = CarCanBusMock()
        transport = initialTransport
        dispatcher = CarCommandDispatcher(lifecycleScope, initialTransport)
        observeTransport(initialTransport)

        Log.i(TAG, "onCreate(): Transport + dispatcher initialized (Mock)")

        lifecycleScope.launch {
            transportMutex.withLock {
                Log.i(TAG, "onCreate(): Connecting initial Mock transport")
                statusBus.markConnecting("Mock")
                initialTransport.connect()
            }
        }

        setContent {
            BabyNodeAutomotiveTheme {
                var currentDispatcher by remember { mutableStateOf(dispatcher) }
                var currentSelection by remember { mutableStateOf(selectedTransport) }
                val connectionState by statusBus.connectionState.collectAsState()
                val latestEvent by statusBus.latestEvent.collectAsState()
                val eventHistory by statusBus.events.collectAsState()

                Scaffold(
                    modifier = Modifier
                ) { innerPadding ->
                    AutomotiveScreen(
                        dispatcher = currentDispatcher,
                        selectedTransport = currentSelection,
                        connectionState = connectionState,
                        status = latestEvent,
                        eventHistory = eventHistory,
                        modifier = Modifier.padding(innerPadding),
                        onTransportSelected = { selected ->
                            Log.i(TAG, "UI: Transport selected → $selected")
                            if (selected != currentSelection) {
                                currentSelection = selected
                                lifecycleScope.launch {
                                    Log.i(TAG, "UI: Switching transport to $selected")
                                    currentDispatcher = selectTransport(selected)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy(): Activity destroying — cleaning up transport")
        transportEventsJob?.cancel()
        if (::transport.isInitialized) {
            Log.i(TAG, "onDestroy(): Closing transport")
            transport.close()
        }
        super.onDestroy()
    }

    private fun observeTransport(activeTransport: CarCanTransport) {
        Log.i(TAG, "observeTransport(): Observing status events for ${activeTransport::class.simpleName}")
        transportEventsJob = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            activeTransport.status().collect { event ->
                Log.i(TAG, "observeTransport(): Event received → $event")
                statusBus.accept(event)
            }
        }
    }

    private suspend fun selectTransport(name: String): CarCommandDispatcher = transportMutex.withLock {
        Log.i(TAG, "selectTransport(): Requested switch to $name")

        if (name == selectedTransport) {
            Log.i(TAG, "selectTransport(): Already using $name — no switch needed")
            return dispatcher
        }

        val previousTransport = transport
        Log.i(TAG, "selectTransport(): Disconnecting previous transport (${previousTransport::class.simpleName})")

        try {
            previousTransport.disconnect()
        } catch (e: CancellationException) {
            Log.e(TAG, "selectTransport(): Disconnect cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "selectTransport(): Disconnect failed: ${e.message}")
            statusBus.accept(CarStatusEvent.Error("Transport disconnect failed: ${e.message}", e))
        }

        transportEventsJob?.cancelAndJoin()
        Log.i(TAG, "selectTransport(): Previous transport event job cancelled")

        previousTransport.close()
        Log.i(TAG, "selectTransport(): Previous transport closed")

        val nextTransport = when (name) {
            "TCP" -> {
                Log.i(TAG, "selectTransport(): Creating TCP transport")
                CarCanBusTcp(scope = lifecycleScope)
            }
            else -> {
                Log.i(TAG, "selectTransport(): Creating Mock transport")
                CarCanBusMock()
            }
        }

        transport = nextTransport
        selectedTransport = name
        dispatcher = CarCommandDispatcher(lifecycleScope, nextTransport)

        Log.i(TAG, "selectTransport(): Marking connection state → Connecting($name)")
        statusBus.markConnecting(name)

        Log.i(TAG, "selectTransport(): Observing new transport events")
        observeTransport(nextTransport)

        try {
            Log.i(TAG, "selectTransport(): Connecting new transport ($name)")
            nextTransport.connect()
        } catch (e: CancellationException) {
            Log.e(TAG, "selectTransport(): Connect cancelled — closing transport")
            nextTransport.close()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "selectTransport(): Connect failed: ${e.message}")
            statusBus.accept(CarStatusEvent.Error("Transport connect failed: ${e.message}", e))
        }

        Log.i(TAG, "selectTransport(): Transport switched to $name")
        dispatcher
    }
}
