package com.babynode.automotive

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class CarCanBusBluetooth : CarCanTransport {

    companion object {
        private const val TAG = "CarCanBusBluetooth"
        private const val DEVICE_NAME = "BabyNodeCAN"

        private const val RESPONSE_TIMEOUT_MILLIS =
            5_000L

        private val SPP_UUID: UUID =
            UUID.fromString(
                "00001101-0000-1000-8000-00805F9B34FB"
            )
    }

    private data class PendingCommand(
        val command: CanonicalCommand,
        val timeoutJob: Job
    )

    private val transportScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private val connectionMutex =
        Mutex()

    private val writeMutex =
        Mutex()

    private val statusFlow =
        MutableSharedFlow<CarStatusEvent>(
            extraBufferCapacity = 64
        )

    private val nextMessageId =
        AtomicLong(1L)

    private val pendingCommands =
        ConcurrentHashMap<Long, PendingCommand>()

    @Volatile
    private var bluetoothSocket: BluetoothSocket? =
        null

    @Volatile
    private var inputStream: InputStream? =
        null

    @Volatile
    private var outputStream: OutputStream? =
        null

    @Volatile
    private var receiveJob: Job? =
        null

    @Volatile
    private var intentionalDisconnect =
        false

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        Log.i(
            TAG,
            "Bluetooth connect()"
        )

        intentionalDisconnect = false

        try {
            val adapter =
                BluetoothAdapter.getDefaultAdapter()

            if (adapter == null) {
                statusFlow.emit(
                    CarStatusEvent.Error(
                        "Bluetooth not supported"
                    )
                )

                return
            }

            if (!adapter.isEnabled) {
                statusFlow.emit(
                    CarStatusEvent.Error(
                        "Bluetooth disabled"
                    )
                )

                return
            }

            val existingSocket =
                bluetoothSocket

            if (
                existingSocket != null &&
                existingSocket.isConnected
            ) {
                Log.i(
                    TAG,
                    "RFCOMM socket is already connected"
                )

                return
            }

            val device =
                adapter.bondedDevices.firstOrNull {
                    it.name == DEVICE_NAME
                }

            if (device == null) {
                Log.e(
                    TAG,
                    "BabyNodeCAN not paired"
                )

                statusFlow.emit(
                    CarStatusEvent.Error(
                        "BabyNodeCAN not paired"
                    )
                )

                return
            }

            Log.i(
                TAG,
                "Found bonded device: ${device.name}"
            )

            adapter.cancelDiscovery()

            val newSocket =
                device.createRfcommSocketToServiceRecord(
                    SPP_UUID
                )

            Log.i(
                TAG,
                "Opening RFCOMM socket..."
            )

            withContext(Dispatchers.IO) {
                newSocket.connect()
            }

            connectionMutex.withLock {
                bluetoothSocket =
                    newSocket

                inputStream =
                    newSocket.inputStream

                outputStream =
                    newSocket.outputStream
            }

            Log.i(
                TAG,
                "RFCOMM socket connected"
            )

            startReceiveLoop(
                newSocket
            )

            statusFlow.emit(
                CarStatusEvent.Connected(
                    device.name ?: DEVICE_NAME
                )
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "RFCOMM connect failed",
                e
            )

            cancelAllPendingCommands(
                "Connection failed"
            )

            closeSocketResources()

            statusFlow.emit(
                CarStatusEvent.Error(
                    "RFCOMM connect failed: " +
                        (
                            e.message
                                ?: e.javaClass.simpleName
                        ),
                    e
                )
            )

            statusFlow.emit(
                CarStatusEvent.Disconnected(
                    "Bluetooth"
                )
            )
        }
    }

    override suspend fun disconnect() {
        Log.i(
            TAG,
            "Bluetooth disconnect()"
        )

        intentionalDisconnect = true

        cancelAllPendingCommands(
            "Transport disconnected"
        )

        stopReceiveLoop()

        closeSocketResources()

        statusFlow.emit(
            CarStatusEvent.Disconnected(
                "Bluetooth"
            )
        )
    }

    override suspend fun sendCommand(
        command: CanonicalCommand
    ) {
        val messageId =
            nextMessageId.getAndIncrement()

        val packet =
            JSONObject().apply {
                put(
                    "id",
                    messageId
                )

                put(
                    "type",
                    "command"
                )

                put(
                    "command",
                    command.command
                )

                if (command.value != null) {
                    put(
                        "value",
                        command.value
                    )
                }
            }.toString()

        Log.i(
            TAG,
            "TX: $packet"
        )

        val timeoutJob =
            transportScope.launch(
                start = CoroutineStart.LAZY
            ) {
                delay(
                    RESPONSE_TIMEOUT_MILLIS
                )

                val timedOutCommand =
                    pendingCommands.remove(
                        messageId
                    )

                if (timedOutCommand != null) {
                    Log.e(
                        TAG,
                        "Response timeout: " +
                            "id=$messageId, " +
                            "command=${command.command}"
                    )

                    statusFlow.emit(
                        CarStatusEvent.Error(
                            "BabyNodeCAN response timeout: " +
                                "id=$messageId " +
                                "command=${command.command}"
                        )
                    )
                }
            }

        val pendingCommand =
            PendingCommand(
                command = command,
                timeoutJob = timeoutJob
            )

        pendingCommands[messageId] =
            pendingCommand

        timeoutJob.start()

        try {
            writeMutex.withLock {
                val activeSocket =
                    bluetoothSocket

                val activeOutputStream =
                    outputStream

                if (
                    activeSocket == null ||
                    !activeSocket.isConnected ||
                    activeOutputStream == null
                ) {
                    removePendingCommand(
                        messageId
                    )

                    statusFlow.emit(
                        CarStatusEvent.Error(
                            "Send failed: " +
                                "BabyNodeCAN is not connected"
                        )
                    )

                    return
                }

                withContext(Dispatchers.IO) {
                    activeOutputStream.write(
                        (packet + "\n").toByteArray(
                            StandardCharsets.UTF_8
                        )
                    )

                    activeOutputStream.flush()
                }
            }

            statusFlow.emit(
                CarStatusEvent.CommandSent(
                    command
                )
            )

            Log.i(
                TAG,
                "CommandSent emitted: " +
                    "id=$messageId, " +
                    "command=${command.command}"
            )
        } catch (e: Exception) {
            removePendingCommand(
                messageId
            )

            Log.e(
                TAG,
                "Packet send failed",
                e
            )

            statusFlow.emit(
                CarStatusEvent.Error(
                    "Send failed: " +
                        (
                            e.message
                                ?: e.javaClass.simpleName
                        ),
                    e
                )
            )

            handleConnectionLoss(
                "Send failed"
            )
        }
    }

    override fun status(): Flow<CarStatusEvent> {
        return statusFlow
    }

    override fun close() {
        Log.i(
            TAG,
            "Bluetooth close()"
        )

        intentionalDisconnect = true

        cancelAllPendingCommandsImmediately()

        receiveJob?.cancel()
        receiveJob = null

        closeSocketResourcesImmediately()

        transportScope.cancel()
    }

    private fun startReceiveLoop(
        connectedSocket: BluetoothSocket
    ) {
        receiveJob?.cancel()

        receiveJob =
            transportScope.launch {
                val reader =
                    BufferedReader(
                        InputStreamReader(
                            connectedSocket.inputStream,
                            StandardCharsets.UTF_8
                        )
                    )

                Log.i(
                    TAG,
                    "Bluetooth receive loop started"
                )

                try {
                    while (
                        connectedSocket.isConnected
                    ) {
                        val line =
                            reader.readLine()
                                ?: break

                        val packet =
                            line.trim()

                        if (packet.isEmpty()) {
                            continue
                        }

                        Log.i(
                            TAG,
                            "RX: $packet"
                        )

                        processIncomingPacket(
                            packet
                        )
                    }

                    if (!intentionalDisconnect) {
                        Log.w(
                            TAG,
                            "Bluetooth receive loop reached EOF"
                        )

                        handleConnectionLoss(
                            "Remote device disconnected"
                        )
                    }
                } catch (e: CancellationException) {
                    Log.i(
                        TAG,
                        "Bluetooth receive loop cancelled"
                    )

                    throw e
                } catch (e: Exception) {
                    if (!intentionalDisconnect) {
                        Log.e(
                            TAG,
                            "Bluetooth receive failed",
                            e
                        )

                        statusFlow.emit(
                            CarStatusEvent.Error(
                                "Receive failed: " +
                                    (
                                        e.message
                                            ?: e.javaClass.simpleName
                                    ),
                                e
                            )
                        )

                        handleConnectionLoss(
                            "Receive failed"
                        )
                    }
                } finally {
                    runCatching {
                        reader.close()
                    }

                    Log.i(
                        TAG,
                        "Bluetooth receive loop ended"
                    )
                }
            }
    }

    private suspend fun processIncomingPacket(
        packet: String
    ) {
        try {
            val json =
                JSONObject(
                    packet
                )

            val type =
                json.optString(
                    "type"
                )

            if (
                !type.equals(
                    "response",
                    ignoreCase = true
                )
            ) {
                Log.w(
                    TAG,
                    "Ignoring unsupported packet type: $type"
                )

                return
            }

            if (!json.has("id")) {
                Log.w(
                    TAG,
                    "Response packet is missing id"
                )

                return
            }

            val commandId =
                json.optLong(
                    "id",
                    -1L
                )

            if (commandId < 0L) {
                Log.w(
                    TAG,
                    "Response packet contains an invalid id"
                )

                return
            }

            val responseStatus =
                json.optString(
                    "status",
                    "unknown"
                )

            val responseCommand =
                json.optString(
                    "command",
                    ""
                )

            val reason =
                json.optString(
                    "reason",
                    ""
                )

            Log.i(
                TAG,
                "Parsed response: " +
                    "id=$commandId, " +
                    "status=$responseStatus, " +
                    "command=$responseCommand, " +
                    "reason=$reason"
            )

            val pendingCommand =
                pendingCommands.remove(
                    commandId
                )

            if (pendingCommand == null) {
                Log.w(
                    TAG,
                    "Ignoring unexpected or duplicate response: " +
                        "id=$commandId"
                )

                return
            }

            pendingCommand
                .timeoutJob
                .cancel()

            val expectedCommand =
                pendingCommand.command.command

            if (
                responseCommand.isNotBlank() &&
                !responseCommand.equals(
                    expectedCommand,
                    ignoreCase = true
                )
            ) {
                Log.e(
                    TAG,
                    "Response command mismatch: " +
                        "id=$commandId, " +
                        "expected=$expectedCommand, " +
                        "received=$responseCommand"
                )

                statusFlow.emit(
                    CarStatusEvent.Error(
                        "BabyNodeCAN response mismatch: " +
                            "expected $expectedCommand, " +
                            "received $responseCommand"
                    )
                )

                return
            }

            val displayStatus =
                if (
                    reason.isNotBlank() &&
                    !responseStatus.equals(
                        "ok",
                        ignoreCase = true
                    )
                ) {
                    "$responseStatus: $reason"
                } else {
                    responseStatus
                }

            Log.i(
                TAG,
                "Matched response: " +
                    "id=$commandId, " +
                    "command=$expectedCommand, " +
                    "status=$displayStatus"
            )

            statusFlow.emit(
                CarStatusEvent.CommandResponse(
                    commandId = commandId,
                    status = displayStatus
                )
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Invalid response packet: $packet",
                e
            )

            statusFlow.emit(
                CarStatusEvent.Error(
                    "Invalid response from BabyNodeCAN: " +
                        (
                            e.message
                                ?: e.javaClass.simpleName
                        ),
                    e
                )
            )
        }
    }

    private fun removePendingCommand(
        messageId: Long
    ) {
        val pendingCommand =
            pendingCommands.remove(
                messageId
            )

        pendingCommand
            ?.timeoutJob
            ?.cancel()
    }

    private suspend fun cancelAllPendingCommands(
        reason: String
    ) {
        val pendingEntries =
            pendingCommands
                .entries
                .toList()

        pendingCommands.clear()

        pendingEntries.forEach { entry ->
            entry.value
                .timeoutJob
                .cancel()

            Log.w(
                TAG,
                "Cancelling pending command: " +
                    "id=${entry.key}, " +
                    "command=${entry.value.command.command}, " +
                    "reason=$reason"
            )
        }
    }

    private fun cancelAllPendingCommandsImmediately() {
        val pendingEntries =
            pendingCommands
                .entries
                .toList()

        pendingCommands.clear()

        pendingEntries.forEach { entry ->
            entry.value
                .timeoutJob
                .cancel()

            Log.w(
                TAG,
                "Closing pending command: " +
                    "id=${entry.key}, " +
                    "command=${entry.value.command.command}"
            )
        }
    }

    private suspend fun handleConnectionLoss(
        reason: String
    ) {
        cancelAllPendingCommands(
            reason
        )

        connectionMutex.withLock {
            val hadConnection =
                bluetoothSocket != null ||
                    inputStream != null ||
                    outputStream != null

            closeSocketResourcesImmediately()

            if (hadConnection) {
                Log.w(
                    TAG,
                    "$reason; Bluetooth connection closed"
                )

                statusFlow.emit(
                    CarStatusEvent.Disconnected(
                        "Bluetooth"
                    )
                )
            }
        }
    }

    private suspend fun closeSocketResources() {
        connectionMutex.withLock {
            closeSocketResourcesImmediately()
        }
    }

    private fun closeSocketResourcesImmediately() {
        runCatching {
            inputStream?.close()
        }

        runCatching {
            outputStream?.close()
        }

        runCatching {
            bluetoothSocket?.close()
        }

        inputStream = null
        outputStream = null
        bluetoothSocket = null
    }

    private fun stopReceiveLoop() {
        receiveJob?.cancel()
        receiveJob = null
    }
}