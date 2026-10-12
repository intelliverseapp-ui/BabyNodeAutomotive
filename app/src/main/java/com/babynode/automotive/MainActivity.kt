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
        "Single-CAN Module (ESP32-S3 WROOM, development stub)",
        BabyNodePeerContract.SUPPORTED_MODULE
    )
}

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG =
            "BNA_MainActivity"

        internal var transportFactory:
            () -> CarCanTransport = {
                CarCanBusBluetooth()
            }

        internal fun resetTransportFactoryForTest() {
            transportFactory = {
                CarCanBusBluetooth()
            }
        }

        private const val TRANSPORT_NAME =
            "Bluetooth"

        private const val STATE_SELECTED_MODULE =
            "selected_module"

        private const val STATE_RESTORED_OUTCOME =
            "restored_terminal_outcome"

        private const val OUTCOME_TYPE = "type"
        private const val OUTCOME_TYPE_RESPONSE = "response"
        private const val OUTCOME_TYPE_ERROR = "error"
        private const val OUTCOME_GENERATION = "session_generation"
        private const val OUTCOME_REQUEST_ID = "request_id"
        private const val OUTCOME_COMMAND = "command"
        private const val OUTCOME_VALUE = "value"
        private const val OUTCOME_STATUS = "status"
        private const val OUTCOME_MESSAGE = "message"

        private const val DEFAULT_MAXIMUM_RECONNECT_ATTEMPTS =
            5

        private val DEFAULT_RECONNECT_DELAYS_MILLIS =
            longArrayOf(
                1_000L,
                2_000L,
                4_000L,
                8_000L,
                10_000L
            )

        internal var maximumReconnectAttempts:
            Int =
            DEFAULT_MAXIMUM_RECONNECT_ATTEMPTS

        internal var reconnectDelaysMillis:
            LongArray =
            DEFAULT_RECONNECT_DELAYS_MILLIS.copyOf()

        internal var reconnectObservationDelayMillis:
            Long =
            500L

        internal fun resetRetryConfigurationForTest() {
            maximumReconnectAttempts =
                DEFAULT_MAXIMUM_RECONNECT_ATTEMPTS

            reconnectDelaysMillis =
                DEFAULT_RECONNECT_DELAYS_MILLIS.copyOf()

            reconnectObservationDelayMillis =
                500L
        }
    }

    private lateinit var transport:
        CarCanTransport

    private lateinit var dispatcher:
        CarCommandDispatcher

    private val statusBus =
        CarStatusBus()

    private val connectionGeneration =
        ConnectionGeneration()

    private var transportEventsJob:
        Job? = null

    private var connectionJob:
        Job? = null

    private var reconnectJob:
        Job? = null

    private var restoredTerminalOutcome by
        mutableStateOf<CarStatusEvent?>(null)

    private var selectedModule:
        ModuleType =
        ModuleType.SINGLE_CAN

    private var bluetoothStartupRequested =
        false

    private var activityIsDestroying =
        false

    private val receivedTerminalOutcomes =
        LinkedHashMap<Pair<Long, Long>, CarStatusEvent>()

    private fun retainTerminalOutcome(
        event: CarStatusEvent
    ) {
        val key =
            when (event) {
                is CarStatusEvent.CommandResponse ->
                    event.sessionGeneration to
                        event.requestId

                is CarStatusEvent.Error -> {
                    val generation =
                        event.sessionGeneration
                    val requestId =
                        event.requestId

                    if (
                        !event.isRequestSpecific ||
                        generation == null ||
                        requestId == null
                    ) {
                        return
                    }

                    generation to requestId
                }

                else -> return
            }

        receivedTerminalOutcomes.putIfAbsent(
            key,
            event
        )
    }

    private var intentionalTransportReplacement =
        false

    private var reconnectAttempt =
        0

    private var deviceApprovalPending =
        false

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

        val restoredTerminalOutcomes =
            BabyNodeApp
                .instance
                .consumeTerminalRequestOutcomes()

        if (restoredTerminalOutcomes.isNotEmpty()) {
            statusBus.replaceTransportStatus(
                terminalEvents =
                    restoredTerminalOutcomes
            )

            Log.w(
                TAG,
                "Restored terminal outcomes from prior Activity: " +
                    "count=${restoredTerminalOutcomes.size}"
            )
        }

        val consumedRestoredOutcome: CarStatusEvent? =
            restoredTerminalOutcomes.lastOrNull { event ->
                when (event) {
                    is CarStatusEvent.CommandResponse -> true
                    is CarStatusEvent.Error ->
                        event.isRequestSpecific
                    else -> false
                }
            }

        restoredTerminalOutcome =
            restoreSavedTerminalOutcome(
                savedInstanceState
            )
                ?: consumedRestoredOutcome

        enableEdgeToEdge()

        val initialTransport =
            transportFactory()

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
                        restoredTerminalOutcome =
                            restoredTerminalOutcome,
                        onRestoredOutcomeDismissed = {
                            restoredTerminalOutcome =
                                null
                        },
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
                        },
                        onApproveDevice = { address ->
                            val stored =
                                BabyNodeApp
                                    .instance
                                    .approveBabyNodeCanAddress(
                                        address
                                    )

                            if (stored) {
                                deviceApprovalPending =
                                    false

                                statusBus
                                    .resetForTransportReplacement()

                                connectCurrentTransport()
                            } else {
                                statusBus.accept(
                                    CarStatusEvent.Error(
                                        message =
                                            "BabyNodeCAN identity " +
                                                "could not be stored"
                                    )
                                )
                            }
                        },
                        onForgetDevice = {
                            val identityRemoved =
                                BabyNodeApp
                                    .instance
                                    .clearApprovedBabyNodeCanAddress()

                            val decision =
                                ForgetDeviceDecision
                                    .fromPersistenceResult(
                                        identityRemoved =
                                            identityRemoved
                                    )

                            when {
                                decision.replaceTransport -> {
                                    deviceApprovalPending =
                                        false

                                    replaceTransport(
                                        module =
                                            currentModule,
                                        onDispatcherReplaced = {
                                            replacementDispatcher ->

                                            currentDispatcher =
                                                replacementDispatcher
                                        }
                                    )
                                }

                                decision.showPersistenceError -> {
                                    statusBus.accept(
                                        CarStatusEvent.Error(
                                            message =
                                                "BabyNodeCAN identity " +
                                                    "could not be forgotten"
                                        )
                                    )

                                    Log.e(
                                        TAG,
                                        "Approved BabyNodeCAN identity " +
                                            "removal failed"
                                    )
                                }
                            }
                        },
                        onRetryConnection = {
                            connectionGeneration.advance()

                            reconnectJob?.cancel()
                            reconnectJob =
                                null

                            connectionJob?.cancel()
                            connectionJob =
                                null

                            reconnectAttempt =
                                0

                            bluetoothStartupRequested =
                                false

                            statusBus
                                .resetForTransportReplacement()

                            ensureBluetoothPermissionsAndConnect()
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

        saveTerminalOutcome(
            restoredTerminalOutcome
        )?.let { saved ->
            outState.putBundle(
                STATE_RESTORED_OUTCOME,
                saved
            )
        }

        super.onSaveInstanceState(
            outState
        )
    }

    private fun saveTerminalOutcome(
        event: CarStatusEvent?
    ): Bundle? {
        return when (event) {
            is CarStatusEvent.CommandResponse -> Bundle().apply {
                putString(OUTCOME_TYPE, OUTCOME_TYPE_RESPONSE)
                putLong(OUTCOME_GENERATION, event.sessionGeneration)
                putLong(OUTCOME_REQUEST_ID, event.requestId)
                putString(OUTCOME_COMMAND, event.command.command)
                event.command.value?.let {
                    putString(OUTCOME_VALUE, it)
                }
                putString(OUTCOME_STATUS, event.status)
            }

            is CarStatusEvent.Error -> {
                val requestId = event.requestId
                val command = event.command

                if (requestId == null || command == null) {
                    null
                } else {
                    Bundle().apply {
                        putString(OUTCOME_TYPE, OUTCOME_TYPE_ERROR)
                        event.sessionGeneration?.let {
                            putLong(OUTCOME_GENERATION, it)
                        }
                        putLong(OUTCOME_REQUEST_ID, requestId)
                        putString(OUTCOME_COMMAND, command.command)
                        command.value?.let {
                            putString(OUTCOME_VALUE, it)
                        }
                        putString(OUTCOME_MESSAGE, event.message)
                    }
                }
            }

            else -> null
        }
    }

    // Any missing or malformed field yields null (fail closed).
    private fun restoreSavedTerminalOutcome(
        savedInstanceState: Bundle?
    ): CarStatusEvent? {
        return runCatching {
            val saved =
                savedInstanceState
                    ?.getBundle(STATE_RESTORED_OUTCOME)
                    ?: return@runCatching null

            if (!saved.containsKey(OUTCOME_REQUEST_ID)) {
                return@runCatching null
            }

            val requestId = saved.getLong(OUTCOME_REQUEST_ID)
            val commandName =
                saved.getString(OUTCOME_COMMAND)
                    ?.takeIf { it.isNotBlank() }
                    ?: return@runCatching null
            val command =
                CanonicalCommand(
                    commandName,
                    saved.getString(OUTCOME_VALUE)
                )

            when (saved.getString(OUTCOME_TYPE)) {
                OUTCOME_TYPE_RESPONSE -> {
                    if (!saved.containsKey(OUTCOME_GENERATION)) {
                        return@runCatching null
                    }

                    CarStatusEvent.CommandResponse(
                        sessionGeneration =
                            saved.getLong(OUTCOME_GENERATION),
                        requestId = requestId,
                        command = command,
                        status =
                            saved.getString(OUTCOME_STATUS)
                                ?.takeIf { it.isNotBlank() }
                                ?: return@runCatching null
                    )
                }

                OUTCOME_TYPE_ERROR -> {
                    CarStatusEvent.Error(
                        message =
                            saved.getString(OUTCOME_MESSAGE)
                                ?.takeIf { it.isNotBlank() }
                                ?: return@runCatching null,
                        sessionGeneration =
                            if (saved.containsKey(OUTCOME_GENERATION)) {
                                saved.getLong(OUTCOME_GENERATION)
                            } else {
                                null
                            },
                        requestId = requestId,
                        command = command
                    )
                }

                else -> null
            }
        }.getOrNull()
    }

    private fun restoreSelectedModule(
        savedInstanceState: Bundle?
    ): ModuleType {
        val savedModuleName =
            savedInstanceState
                ?.getString(
                    STATE_SELECTED_MODULE
                )

        val persistedModuleName =
            BabyNodeApp
                .instance
                .selectedModuleName()

        val moduleName =
            savedModuleName
                ?: persistedModuleName

        return runCatching {
            if (moduleName == null) {
                ModuleType.SINGLE_CAN
            } else {
                ModuleType.valueOf(
                    moduleName
                )
            }
        }.getOrDefault(
            ModuleType.SINGLE_CAN
        )
    }

    /**
     * Exercises the production transport-replacement path from
     * instrumented lifecycle tests.
     */
    internal fun replaceTransportForTest() {
        replaceTransport(
            module =
                selectedModule,
            onDispatcherReplaced = {
                // The lifecycle test does not retain Compose UI state.
            }
        )
    }

    private fun replaceTransport(
        module: ModuleType,
        onDispatcherReplaced:
            (CarCommandDispatcher) -> Unit
    ) {
        intentionalTransportReplacement =
            true

        connectionGeneration.advance()

        deviceApprovalPending =
            false

        reconnectJob?.cancel()
        reconnectJob =
            null

        reconnectAttempt =
            0

        connectionJob?.cancel()
        connectionJob =
            null

        val oldTransport =
            transport

        val oldDispatcher =
            dispatcher

        val terminalEvents =
            oldTransport.close()

        transportEventsJob?.cancel()
        transportEventsJob =
            null

        terminalEvents.forEach { event ->
            oldDispatcher.acceptTransportEvent(
                event
            )
        }

        statusBus.replaceTransportStatus(
            terminalEvents =
                terminalEvents
        )

        val newTransport =
            transportFactory()

        transport =
            newTransport

        dispatcher =
            CarCommandDispatcher(
                lifecycleScope,
                newTransport
            )

        selectedModule =
            module

        BabyNodeApp
            .instance
            .saveSelectedModuleName(
                module.name
            )

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

        val activeGeneration =
            connectionGeneration.current()

        connectionJob =
            lifecycleScope.launch {
                if (
                    !connectionGeneration.isCurrent(
                        activeGeneration
                    ) ||
                    activeTransport !== transport
                ) {
                    return@launch
                }

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

                    if (
                        connectionGeneration.isCurrent(
                            activeGeneration
                        ) &&
                        activeTransport === transport
                    ) {
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
                } finally {
                    if (
                        connectionGeneration.isCurrent(
                            activeGeneration
                        )
                    ) {
                        connectionJob =
                            null
                    }
                }

                if (
                    connectionGeneration.isCurrent(
                        activeGeneration
                    ) &&
                    !activityIsDestroying &&
                    !deviceApprovalPending &&
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

    internal fun scheduleReconnectForTest() {
        scheduleReconnect(
            reason =
                "Lifecycle retry test"
        )
    }

    internal fun manualRetryForTest() {
        connectionGeneration.advance()

        reconnectJob?.cancel()
        reconnectJob =
            null

        connectionJob?.cancel()
        connectionJob =
            null

        reconnectAttempt =
            0

        bluetoothStartupRequested =
            false

        statusBus
            .resetForTransportReplacement()

        connectCurrentTransport()
    }

    internal fun connectionStateForTest():
        CarConnectionState {
        return statusBus
            .connectionState
            .value
    }

    internal fun reconnectAttemptForTest():
        Int {
        return reconnectAttempt
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

        val reconnectGeneration =
            connectionGeneration.current()

        val reconnectTransport =
            transport

        reconnectJob =
            lifecycleScope.launch {
                while (
                    connectionGeneration.isCurrent(
                        reconnectGeneration
                    ) &&
                    reconnectTransport === transport &&
                    !activityIsDestroying &&
                    !intentionalTransportReplacement &&
                    reconnectAttempt <
                    maximumReconnectAttempts
                ) {
                    val delayIndex =
                        reconnectAttempt.coerceAtMost(
                            reconnectDelaysMillis.lastIndex
                        )

                    val reconnectDelay =
                        reconnectDelaysMillis[
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
                        !connectionGeneration.isCurrent(
                            reconnectGeneration
                        ) ||
                        reconnectTransport !== transport ||
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
                        reconnectTransport

                    if (
                        !connectionGeneration.isCurrent(
                            reconnectGeneration
                        ) ||
                        activeTransport !== transport
                    ) {
                        return@launch
                    }

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
                        reconnectObservationDelayMillis
                    )

                    if (
                        connectionGeneration.isCurrent(
                            reconnectGeneration
                        ) &&
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
                    connectionGeneration.isCurrent(
                        reconnectGeneration
                    ) &&
                    reconnectTransport === transport &&
                    !activityIsDestroying &&
                    statusBus.connectionState.value
                    !is CarConnectionState.Connected
                ) {
                    statusBus.accept(
                        CarStatusEvent.Error(
                            "BabyNodeCAN reconnection failed after " +
                                "$maximumReconnectAttempts attempts"
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

    /**
     * Exercises production external-intent inspection without
     * manually invoking an Android lifecycle callback.
     */
    internal fun inspectExternalIntentForTest(
        intent: Intent
    ) {
        inspectExternalIntent(
            intent
        )
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

        transport.close().forEach { event ->
            retainTerminalOutcome(
                event
            )
        }

        val terminalEvents =
            receivedTerminalOutcomes.values.toList()

        if (terminalEvents.isNotEmpty()) {
            val persisted =
                BabyNodeApp
                    .instance
                    .saveTerminalRequestOutcomes(
                        terminalEvents
                    )

            if (!persisted) {
                Log.e(
                    TAG,
                    "Terminal request outcomes could not be persisted"
                )
            }
        }

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

                        dispatcher.acceptTransportEvent(
                            event
                        )

                        statusBus.accept(
                            event
                        )

                        retainTerminalOutcome(
                            event
                        )

                        when (event) {
                            is CarStatusEvent.DeviceApprovalRequired -> {
                                deviceApprovalPending =
                                    true

                                reconnectJob?.cancel()
                                reconnectJob =
                                    null

                                Log.i(
                                    TAG,
                                    "BabyNodeCAN device approval required"
                                )
                            }

                            is CarStatusEvent.Connected -> {
                                deviceApprovalPending =
                                    false

                                reconnectAttempt =
                                    0

                                Log.i(
                                    TAG,
                                    "Bluetooth connection established"
                                )

                                dispatcher.sendProtocolHandshake(
                                    onResult = { result ->
                                        if (
                                            result is
                                            CarCommandDispatchResult.Submitted &&
                                            dispatcher
                                                .isProtocolHandshakeReady()
                                        ) {
                                            /*
                                             * The exact handshake response
                                             * arrived before sendCommand()
                                             * returned and was retained by
                                             * the dispatcher. Start module
                                             * configuration only after that
                                             * response has been consumed.
                                             */
                                            dispatcher.sendModuleConfig(
                                                moduleJsonId =
                                                    selectedModule.jsonId
                                            )
                                        }
                                    }
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
                                if (
                                    event.command.command ==
                                    "protocol.handshake" &&
                                    event.status.equals(
                                        "ok",
                                        ignoreCase = true
                                    )
                                ) {
                                    Log.i(
                                        TAG,
                                        "Protocol handshake acknowledged"
                                    )

                                    dispatcher.sendModuleConfig(
                                        moduleJsonId =
                                            selectedModule.jsonId,
                                        onResult = { result ->
                                            if (
                                                result is
                                                CarCommandDispatchResult.Submitted
                                            ) {
                                                statusBus
                                                    .latestEvent
                                                    .value
                                                    ?.let { latestEvent ->
                                                        dispatcher
                                                            .acceptTransportEvent(
                                                                latestEvent
                                                            )
                                                    }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
            }
    }
}