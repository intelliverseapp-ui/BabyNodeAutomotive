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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
        private const val TAG =
            "BNA_MainActivity"

        private const val TRANSPORT_NAME =
            "Bluetooth"

        private const val STATE_SELECTED_MODULE =
            "selected_module"

        private const val MAXIMUM_RECONNECT_ATTEMPTS =
            5

        private val RECONNECT_DELAYS_MILLIS =
            longArrayOf(
                1_000L,
                2_000L,
                4_000L,
                8_000L,
                10_000L
            )
    }

    private lateinit var transport:
        CarCanTransport

    private lateinit var dispatcher:
        CarCommandDispatcher

    private val statusBus =
        CarStatusBus()

    private var transportEventsJob:
        Job? = null

    private var connectionJob:
        Job? = null

    private var reconnectJob:
        Job? = null

    private var selectedModule:
        ModuleType =
        ModuleType.SINGLE_CAN

    private var bluetoothStartupRequested =
        false

    private var activityIsDestroying =
        false

    private var intentionalTransportReplacement =
        false

    private var reconnectAttempt =
        0

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
                    "connect=$connectGranted, " +
                    "scan=$scanGranted"
            )

            bluetoothStartupRequested =
                false

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
        super.onCreate(
            savedInstanceState
        )

        Log.i(
            TAG,
            "onCreate(): Activity starting"
        )

        selectedModule =
            restoreSelectedModule(
                savedInstanceState
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

        inspectExternalIntent(
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
                    statusBus
                        .connectionState
                        .collectAsState()

                val latestEvent by
                    statusBus
                        .latestEvent
                        .collectAsState()

                val eventHistory by
                    statusBus
                        .events
                        .collectAsState()

                Scaffold(
                    modifier =
                        Modifier
                ) { innerPadding ->

                    AutomotiveScreen(
                        dispatcher =
                            currentDispatcher,
                        moduleType =
                            currentModule,
                        connectionState =
                            connectionState,
                        status =
                            latestEvent,
                        eventHistory =
                            eventHistory,
                        modifier =
                            Modifier.padding(
                                innerPadding
                            ),
                        onModuleSelected = { module ->

                            Log.i(
                                TAG,
                                "UI module selection changed"
                            )

                            if (
                                module != currentModule
                            ) {
                                currentModule =
                                    module

                                replaceTransport(
                                    module =
                                        module,
                                    onDispatcherReplaced = {
                                        replacementDispatcher ->

                                        currentDispatcher =
                                            replacementDispatcher
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }

        ensureBluetoothPermissionsAndConnect()
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        outState.putString(
            STATE_SELECTED_MODULE,
            selectedModule.name
        )

        super.onSaveInstanceState(
            outState
        )
    }

    private fun restoreSelectedModule(
        savedInstanceState: Bundle?
    ): ModuleType {
        val savedModuleName =
            savedInstanceState
                ?.getString(
                    STATE_SELECTED_MODULE
                )

        return runCatching {
            if (savedModuleName == null) {
                ModuleType.SINGLE_CAN
            } else {
                ModuleType.valueOf(
                    savedModuleName
                )
            }
        }.getOrDefault(
            ModuleType.SINGLE_CAN
        )
    }

    private fun replaceTransport(
        module: ModuleType,
        onDispatcherReplaced:
            (CarCommandDispatcher) -> Unit
    ) {
        intentionalTransportReplacement =
            true

        reconnectJob?.cancel()
        reconnectJob =
            null

        reconnectAttempt =
            0

        connectionJob?.cancel()
        connectionJob =
            null

        transportEventsJob?.cancel()
        transportEventsJob =
            null

        transport.close()

        val newTransport =
            CarCanBusBluetooth()

        transport =
            newTransport

        dispatcher =
            CarCommandDispatcher(
                lifecycleScope,
                newTransport
            )

        selectedModule =
            module

        onDispatcherReplaced(
            dispatcher
        )

        observeTransport(
            newTransport
        )

        bluetoothStartupRequested =
            false

        intentionalTransportReplacement =
            false

        ensureBluetoothPermissionsAndConnect()
    }

    private fun ensureBluetoothPermissionsAndConnect() {
        if (activityIsDestroying) {
            return
        }

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.S
        ) {
            Log.i(
                TAG,
                "Modern Bluetooth runtime permissions " +
                    "are not required on this Android version"
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
                "connect=$connectGranted, " +
                "scan=$scanGranted"
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

        bluetoothStartupRequested =
            true

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
        if (activityIsDestroying) {
            return
        }

        if (connectionJob?.isActive == true) {
            Log.w(
                TAG,
                "Bluetooth connection attempt is already active"
            )

            return
        }

        bluetoothStartupRequested =
            false

        val activeTransport =
            transport

        connectionJob =
            lifecycleScope.launch {
                statusBus.markConnecting(
                    TRANSPORT_NAME
                )

                try {
                    Log.i(
                        TAG,
                        "Connecting Bluetooth transport"
                    )

                    activeTransport.connect()

                    Log.i(
                        TAG,
                        "Bluetooth transport connect() returned"
                    )
                } catch (e: CancellationException) {
                    Log.i(
                        TAG,
                        "Bluetooth startup cancelled"
                    )

                    throw e
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
                } finally {
                    connectionJob =
                        null
                }

                if (
                    !activityIsDestroying &&
                    activeTransport === transport &&
                    statusBus.connectionState.value
                        !is CarConnectionState.Connected
                ) {
                    scheduleReconnect(
                        reason =
                            "Initial connection did not complete"
                    )
                }
            }
    }

    private fun scheduleReconnect(
        reason: String
    ) {
        if (
            activityIsDestroying ||
            intentionalTransportReplacement
        ) {
            return
        }

        if (reconnectJob?.isActive == true) {
            Log.i(
                TAG,
                "Reconnect job is already active"
            )

            return
        }

        reconnectJob =
            lifecycleScope.launch {
                while (
                    !activityIsDestroying &&
                    !intentionalTransportReplacement &&
                    reconnectAttempt <
                    MAXIMUM_RECONNECT_ATTEMPTS
                ) {
                    val delayIndex =
                        reconnectAttempt.coerceAtMost(
                            RECONNECT_DELAYS_MILLIS.lastIndex
                        )

                    val reconnectDelay =
                        RECONNECT_DELAYS_MILLIS[
                            delayIndex
                        ]

                    reconnectAttempt +=
                        1

                    Log.w(
                        TAG,
                        "Scheduling Bluetooth reconnect " +
                            "attempt=$reconnectAttempt, " +
                            "delayMs=$reconnectDelay, " +
                            "reason=$reason"
                    )

                    delay(
                        reconnectDelay
                    )

                    if (
                        activityIsDestroying ||
                        intentionalTransportReplacement
                    ) {
                        return@launch
                    }

                    if (
                        statusBus.connectionState.value
                        is CarConnectionState.Connected
                    ) {
                        reconnectAttempt =
                            0

                        return@launch
                    }

                    val activeTransport =
                        transport

                    statusBus.markConnecting(
                        TRANSPORT_NAME
                    )

                    try {
                        Log.i(
                            TAG,
                            "Automatic Bluetooth reconnect attempt " +
                                reconnectAttempt
                        )

                        activeTransport.connect()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "Automatic Bluetooth reconnect failed",
                            e
                        )
                    }

                    /*
                     * Allow the transport event collector to process
                     * a possible Connected event before deciding
                     * whether another attempt is required.
                     */
                    delay(
                        500L
                    )

                    if (
                        activeTransport === transport &&
                        statusBus.connectionState.value
                        is CarConnectionState.Connected
                    ) {
                        reconnectAttempt =
                            0

                        Log.i(
                            TAG,
                            "Automatic Bluetooth reconnection succeeded"
                        )

                        return@launch
                    }
                }

                if (
                    !activityIsDestroying &&
                    statusBus.connectionState.value
                    !is CarConnectionState.Connected
                ) {
                    statusBus.accept(
                        CarStatusEvent.Error(
                            "BabyNodeCAN reconnection failed after " +
                                "$MAXIMUM_RECONNECT_ATTEMPTS attempts"
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

    /**
     * External intents are inspected but never translated directly
     * into vehicle commands.
     *
     * Vehicle commands must originate from the visible in-app voice
     * or typed-command interface, where the user deliberately submits
     * the request.
     */
    private fun inspectExternalIntent(
        incomingIntent: Intent?
    ) {
        if (incomingIntent == null) {
            return
        }

        val data =
            incomingIntent.dataString

        if (
            data == "bn://unlock_doors" ||
            data == "bn://lock_doors"
        ) {
            Log.w(
                TAG,
                "External vehicle-command deep link rejected"
            )

            return
        }

        when (incomingIntent.action) {
            Intent.ACTION_ASSIST -> {
                Log.w(
                    TAG,
                    "External Assistant command execution rejected"
                )
            }

            Intent.ACTION_PROCESS_TEXT -> {
                Log.w(
                    TAG,
                    "External processed-text command execution rejected"
                )
            }
        }
    }

    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(
            intent
        )

        setIntent(
            intent
        )

        inspectExternalIntent(
            intent
        )
    }

    override fun onDestroy() {
        activityIsDestroying =
            true

        intentionalTransportReplacement =
            true

        reconnectJob?.cancel()
        reconnectJob =
            null

        connectionJob?.cancel()
        connectionJob =
            null

        transportEventsJob?.cancel()
        transportEventsJob =
            null

        transport.close()

        super.onDestroy()
    }

    private fun observeTransport(
        activeTransport: CarCanTransport
    ) {
        transportEventsJob?.cancel()

        transportEventsJob =
            lifecycleScope.launch(
                start =
                    CoroutineStart.UNDISPATCHED
            ) {
                activeTransport
                    .status()
                    .collect { event ->

                        if (
                            activeTransport !== transport
                        ) {
                            return@collect
                        }

                        statusBus.accept(
                            event
                        )

                        when (event) {
                            is CarStatusEvent.Connected -> {
                                reconnectAttempt =
                                    0

                                Log.i(
                                    TAG,
                                    "Bluetooth connection established"
                                )

                                dispatcher.sendModuleConfig(
                                    selectedModule.jsonId
                                )
                            }

                            is CarStatusEvent.Disconnected -> {
                                if (
                                    !activityIsDestroying &&
                                    !intentionalTransportReplacement
                                ) {
                                    scheduleReconnect(
                                        reason =
                                            "Unexpected RFCOMM disconnect"
                                    )
                                }
                            }

                            is CarStatusEvent.Error -> {
                                /*
                                 * Command-level errors do not always
                                 * mean the socket was lost. Reconnect
                                 * is started only after a real
                                 * Disconnected event or an initial
                                 * connection that did not complete.
                                 */
                            }

                            is CarStatusEvent.CommandSent -> {
                                // No connection-state action required.
                            }

                            is CarStatusEvent.CommandResponse -> {
                                // No connection-state action required.
                            }
                        }
                    }
            }
    }
}