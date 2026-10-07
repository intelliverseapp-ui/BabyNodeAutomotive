package com.babynode.automotive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.babynode.automotive.ui.AutomotiveScreen
import com.babynode.automotive.ui.theme.BabyNodeAutomotiveTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class ModuleType(
    val uiLabel: String,
    val jsonId: String
) {
    SINGLE_CAN(
        "Single-CAN Module (ESP32-S3 WROOM)",
        "single"
    ),

    DUAL_CAN(
        "Dual-CAN Module (DuoCAN-C6)",
        "dual"
    )
}

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "BNA_MainActivity"
        private const val TRANSPORT_NAME = "Bluetooth"
    }

    private lateinit var transport: CarCanTransport
    private lateinit var dispatcher: CarCommandDispatcher

    private val statusBus = CarStatusBus()

    private var transportEventsJob: Job? = null

    private var selectedModule: ModuleType =
        ModuleType.SINGLE_CAN

    private var bluetoothStartupRequested = false

    private val bluetoothPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissionResults ->

            val connectGranted =
                permissionResults[
                    Manifest.permission.BLUETOOTH_CONNECT
                ] == true ||
                    hasPermission(
                        Manifest.permission.BLUETOOTH_CONNECT
                    )

            val scanGranted =
                permissionResults[
                    Manifest.permission.BLUETOOTH_SCAN
                ] == true ||
                    hasPermission(
                        Manifest.permission.BLUETOOTH_SCAN
                    )

            Log.i(
                TAG,
                "Bluetooth permission result: " +
                    "connect=$connectGranted, scan=$scanGranted"
            )

            if (
                connectGranted &&
                scanGranted
            ) {
                Log.i(
                    TAG,
                    "Bluetooth permissions granted"
                )

                connectCurrentTransport()
            } else {
                Log.e(
                    TAG,
                    "Bluetooth permissions denied"
                )

                statusBus.markConnecting(
                    TRANSPORT_NAME
                )

                statusBus.accept(
                    CarStatusEvent.Error(
                        "Nearby Devices permission is required " +
                            "to connect to BabyNodeCAN"
                    )
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        Log.i(
            TAG,
            "onCreate(): Activity starting"
        )

        enableEdgeToEdge()

        val initialTransport =
            CarCanBusBluetooth()

        transport =
            initialTransport

        dispatcher =
            CarCommandDispatcher(
                lifecycleScope,
                initialTransport
            )

        observeTransport(
            initialTransport
        )

        handleIntent(
            intent
        )

        setContent {
            BabyNodeAutomotiveTheme {
                var currentDispatcher by remember {
                    mutableStateOf(
                        dispatcher
                    )
                }

                var currentModule by remember {
                    mutableStateOf(
                        selectedModule
                    )
                }

                val connectionState by
                    statusBus.connectionState.collectAsState()

                val latestEvent by
                    statusBus.latestEvent.collectAsState()

                val eventHistory by
                    statusBus.events.collectAsState()

                Scaffold(
                    modifier = Modifier
                ) { innerPadding ->

                    AutomotiveScreen(
                        dispatcher = currentDispatcher,
                        moduleType = currentModule,
                        connectionState = connectionState,
                        status = latestEvent,
                        eventHistory = eventHistory,
                        modifier = Modifier.padding(
                            innerPadding
                        ),
                        onModuleSelected = { module ->

                            Log.i(
                                TAG,
                                "UI: Module selected -> ${module.uiLabel}"
                            )

                            if (module != currentModule) {
                                currentModule = module
                                selectedModule = module

                                lifecycleScope.launch {
                                    transport.close()

                                    transportEventsJob?.cancel()

                                    val newTransport =
                                        CarCanBusBluetooth()

                                    transport =
                                        newTransport

                                    dispatcher =
                                        CarCommandDispatcher(
                                            lifecycleScope,
                                            newTransport
                                        )

                                    currentDispatcher =
                                        dispatcher

                                    observeTransport(
                                        newTransport
                                    )

                                    bluetoothStartupRequested =
                                        false

                                    ensureBluetoothPermissionsAndConnect()
                                }
                            }
                        }
                    )
                }
            }
        }

        ensureBluetoothPermissionsAndConnect()
    }

    private fun ensureBluetoothPermissionsAndConnect() {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.S
        ) {
            Log.i(
                TAG,
                "Android version does not require modern " +
                    "Bluetooth runtime permissions"
            )

            connectCurrentTransport()
            return
        }

        val connectGranted =
            hasPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            )

        val scanGranted =
            hasPermission(
                Manifest.permission.BLUETOOTH_SCAN
            )

        Log.i(
            TAG,
            "Bluetooth permission check: " +
                "connect=$connectGranted, scan=$scanGranted"
        )

        if (
            connectGranted &&
            scanGranted
        ) {
            connectCurrentTransport()
            return
        }

        if (bluetoothStartupRequested) {
            Log.w(
                TAG,
                "Bluetooth permission request is already active"
            )

            return
        }

        bluetoothStartupRequested = true

        Log.i(
            TAG,
            "Requesting Nearby Devices permissions"
        )

        bluetoothPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        )
    }

    private fun connectCurrentTransport() {
        bluetoothStartupRequested = false

        lifecycleScope.launch {
            statusBus.markConnecting(
                TRANSPORT_NAME
            )

            try {
                Log.i(
                    TAG,
                    "Connecting Bluetooth transport"
                )

                transport.connect()

                Log.i(
                    TAG,
                    "Bluetooth transport connect() completed"
                )

                dispatcher.sendModuleConfig(
                    selectedModule.jsonId
                )
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Bluetooth startup failed",
                    e
                )

                statusBus.accept(
                    CarStatusEvent.Error(
                        "Bluetooth startup failed: " +
                            (
                                e.message
                                    ?: e.javaClass.simpleName
                            ),
                        e
                    )
                )
            }
        }
    }

    private fun hasPermission(
        permission: String
    ): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun handleIntent(
        intent: Intent?
    ) {
        if (intent == null) {
            return
        }

        intent.dataString?.let { data ->
            when (data) {
                "bn://unlock_doors" -> {
                    dispatcher.handle(
                        "unlock the doors"
                    )
                }

                "bn://lock_doors" -> {
                    dispatcher.handle(
                        "lock the doors"
                    )
                }
            }
        }

        when (intent.action) {
            Intent.ACTION_ASSIST -> {
                val query =
                    intent.getStringExtra(
                        Intent.EXTRA_TEXT
                    ) ?: ""

                if (
                    query.isNotBlank() &&
                    CarCommandDetector.isAutomotive(
                        query
                    )
                ) {
                    dispatcher.handle(
                        query
                    )
                }
            }

            Intent.ACTION_PROCESS_TEXT -> {
                val query =
                    intent.getCharSequenceExtra(
                        Intent.EXTRA_PROCESS_TEXT
                    )?.toString() ?: ""

                if (
                    query.isNotBlank() &&
                    CarCommandDetector.isAutomotive(
                        query
                    )
                ) {
                    dispatcher.handle(
                        query
                    )
                }
            }
        }
    }

    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(
            intent
        )

        handleIntent(
            intent
        )
    }

    override fun onDestroy() {
        transportEventsJob?.cancel()

        transport.close()

        super.onDestroy()
    }

    private fun observeTransport(
        activeTransport: CarCanTransport
    ) {
        transportEventsJob =
            lifecycleScope.launch(
                start = CoroutineStart.UNDISPATCHED
            ) {
                activeTransport
                    .status()
                    .collect { event ->
                        statusBus.accept(
                            event
                        )
                    }
            }
    }
}