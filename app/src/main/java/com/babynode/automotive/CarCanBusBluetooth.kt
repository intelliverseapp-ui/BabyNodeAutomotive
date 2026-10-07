package com.babynode.automotive

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class CarCanBusBluetooth : CarCanTransport {

    companion object {
        private const val TAG =
            "CarCanBusBluetooth"

        private const val DEVICE_NAME =
            "BabyNodeCAN"

        private const val RESPONSE_TIMEOUT_MILLIS =
            5_000L

        private const val MAXIMUM_FRAME_LENGTH =
            4_096

        private val VALID_RESPONSE_STATUSES =
            setOf(
                "ok",
                "error",
                "failed",
                "unsupported"
            )

        private val SPP_UUID: UUID =
            UUID.fromString(
                "00001101-0000-1000-8000-00805F9B34FB"
            )
    }

    private data class PendingCommand(
        val command: CanonicalCommand,
        val responseReceived: CompletableDeferred<Unit>
    )

    private val transportScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    private val connectMutex =
        Mutex()

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
    private var bluetoothSocket:
        BluetoothSocket? = null

    @Volatile
    private var connectingSocket:
        BluetoothSocket? = null

    @Volatile
    private var inputStream:
        InputStream? = null

    @Volatile
    private var outputStream:
        OutputStream? = null

    @Volatile
    private var receiveJob:
        Job? = null

    @Volatile
    private var intentionalDisconnect =
        false

    @Volatile
    private var permanentlyClosed =
        false

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        connectMutex.withLock {
            if (permanentlyClosed) {
                Log.e(
                    TAG,
                    "Connection rejected because transport is closed"
                )

                statusFlow.emit(
                    CarStatusEvent.Error(
                        "Bluetooth transport is permanently closed"
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

            if (connectingSocket != null) {
                Log.w(
                    TAG,
                    "RFCOMM connection attempt is already active"
                )

                return
            }

            intentionalDisconnect =
                false

            Log.i(
                TAG,
                "Starting Bluetooth connection"
            )

            var newSocket:
                BluetoothSocket? = null

            try {
                val adapter =
                    BluetoothAdapter.getDefaultAdapter()

                if (adapter == null) {
                    statusFlow.emit(
                        CarStatusEvent.Error(
                            "Bluetooth is not supported"
                        )
                    )

                    return
                }

                if (!adapter.isEnabled) {
                    statusFlow.emit(
                        CarStatusEvent.Error(
                            "Bluetooth is disabled"
                        )
                    )

                    return
                }

                val device =
                    findApprovedBabyNodeCanDevice(
                        adapter
                    ) ?: return

                adapter.cancelDiscovery()

                newSocket =
                    device.createRfcommSocketToServiceRecord(
                        SPP_UUID
                    )

                connectingSocket =
                    newSocket

                Log.i(
                    TAG,
                    "Opening RFCOMM socket"
                )

                withContext(Dispatchers.IO) {
                    newSocket.connect()
                }

                if (
                    permanentlyClosed ||
                    intentionalDisconnect
                ) {
                    runCatching {
                        newSocket.close()
                    }

                    return
                }

                connectionMutex.withLock {
                    bluetoothSocket =
                        newSocket

                    inputStream =
                        newSocket.inputStream

                    outputStream =
                        newSocket.outputStream
                }

                connectingSocket =
                    null

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
            } catch (e: CancellationException) {
                Log.i(
                    TAG,
                    "Bluetooth connection attempt cancelled"
                )

                runCatching {
                    newSocket?.close()
                }

                connectingSocket =
                    null

                throw e
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "RFCOMM connection failed",
                    e
                )

                runCatching {
                    newSocket?.close()
                }

                connectingSocket =
                    null

                cancelAllPendingCommands(
                    "Connection failed"
                )

                closeSocketResources()

                statusFlow.emit(
                    CarStatusEvent.Error(
                        "RFCOMM connection failed: " +
                            (
                                e.message
                                    ?: e.javaClass.simpleName
                            ),
                        e
                    )
                )
            } finally {
                if (
                    connectingSocket === newSocket
                ) {
                    connectingSocket =
                        null
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun findApprovedBabyNodeCanDevice(
        adapter: BluetoothAdapter
    ): BluetoothDevice? {
        val bondedDevices =
            adapter.bondedDevices

        val approvedAddress =
            BabyNodeApp
                .instance
                .approvedBabyNodeCanAddress()

        if (approvedAddress != null) {
            val approvedDevice =
                bondedDevices.firstOrNull {
                    it.address.equals(
                        approvedAddress,
                        ignoreCase = true
                    )
                }

            if (approvedDevice == null) {
                statusFlow.emit(
                    CarStatusEvent.Error(
                        "The approved BabyNodeCAN device " +
                            "is no longer paired"
                    )
                )

                return null
            }

            if (
                approvedDevice.name !=
                DEVICE_NAME
            ) {
                statusFlow.emit(
                    CarStatusEvent.Error(
                        "The approved Bluetooth device " +
                            "does not identify as BabyNodeCAN"
                    )
                )

                return null
            }

            Log.i(
                TAG,
                "Approved BabyNodeCAN identity verified"
            )

            return approvedDevice
        }

        val matchingDevices =
            bondedDevices.filter {
                it.name == DEVICE_NAME
            }

        if (matchingDevices.isEmpty()) {
            statusFlow.emit(
                CarStatusEvent.Error(
                    "BabyNodeCAN is not paired"
                )
            )

            return null
        }

        if (matchingDevices.size > 1) {
            statusFlow.emit(
                CarStatusEvent.Error(
                    "More than one paired device is named " +
                        DEVICE_NAME
                )
            )

            return null
        }

        val uniquelyMatchedDevice =
            matchingDevices.single()

        val identityStored =
            BabyNodeApp
                .instance
                .approveBabyNodeCanAddress(
                    uniquelyMatchedDevice.address
                )

        if (!identityStored) {
            statusFlow.emit(
                CarStatusEvent.Error(
                    "BabyNodeCAN Bluetooth identity " +
                        "could not be stored"
                )
            )

            return null
        }

        Log.i(
            TAG,
            "Unique BabyNodeCAN identity approved and stored"
        )

        return uniquelyMatchedDevice
    }

    override suspend fun disconnect() {
        Log.i(
            TAG,
            "Disconnecting Bluetooth transport"
        )

        intentionalDisconnect =
            true

        runCatching {
            connectingSocket?.close()
        }

        connectingSocket =
            null

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
        if (permanentlyClosed) {
            statusFlow.emit(
                CarStatusEvent.Error(
                    "Send failed: Bluetooth transport is closed"
                )
            )

            return
        }

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

        val pendingCommand =
            PendingCommand(
                command =
                    command,
                responseReceived =
                    CompletableDeferred()
            )

        var writeCompleted =
            false

        try {
            writeMutex.withLock {
                if (
                    permanentlyClosed ||
                    intentionalDisconnect
                ) {
                    statusFlow.emit(
                        CarStatusEvent.Error(
                            "Send failed: Bluetooth transport " +
                                "is not available"
                        )
                    )

                    return
                }

                val activeSocket =
                    bluetoothSocket

                val activeOutputStream =
                    outputStream

                if (
                    activeSocket == null ||
                    !activeSocket.isConnected ||
                    activeOutputStream == null
                ) {
                    statusFlow.emit(
                        CarStatusEvent.Error(
                            "Send failed: BabyNodeCAN is not connected"
                        )
                    )

                    return
                }

                pendingCommands[
                    messageId
                ] = pendingCommand

                Log.i(
                    TAG,
                    "Sending command request id=$messageId"
                )

                withContext(Dispatchers.IO) {
                    activeOutputStream.write(
                        (packet + "\n").toByteArray(
                            StandardCharsets.UTF_8
                        )
                    )

                    activeOutputStream.flush()
                }

                writeCompleted =
                    true
            }

            if (!writeCompleted) {
                pendingCommands.remove(
                    messageId,
                    pendingCommand
                )

                return
            }

            statusFlow.emit(
                CarStatusEvent.CommandSent(
                    command
                )
            )

            Log.i(
                TAG,
                "Command request written: " +
                    "id=$messageId, " +
                    "command=${command.command}"
            )

            startResponseTimeout(
                messageId =
                    messageId,
                pendingCommand =
                    pendingCommand
            )
        } catch (e: CancellationException) {
            pendingCommands.remove(
                messageId,
                pendingCommand
            )

            pendingCommand
                .responseReceived
                .cancel()

            Log.i(
                TAG,
                "Command send cancelled: id=$messageId"
            )

            throw e
        } catch (e: Exception) {
            pendingCommands.remove(
                messageId,
                pendingCommand
            )

            pendingCommand
                .responseReceived
                .cancel()

            Log.e(
                TAG,
                "Command send failed",
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

    override fun status():
        Flow<CarStatusEvent> {
        return statusFlow
    }

    override fun close() {
        if (permanentlyClosed) {
            return
        }

        Log.i(
            TAG,
            "Closing Bluetooth transport permanently"
        )

        permanentlyClosed =
            true

        intentionalDisconnect =
            true

        runCatching {
            connectingSocket?.close()
        }

        connectingSocket =
            null

        cancelAllPendingCommandsImmediately()

        receiveJob?.cancel()
        receiveJob = null

        closeSocketResourcesImmediately()

        transportScope.cancel()
    }

    private fun startResponseTimeout(
        messageId: Long,
        pendingCommand: PendingCommand
    ) {
        transportScope.launch {
            try {
                withTimeout(
                    RESPONSE_TIMEOUT_MILLIS
                ) {
                    pendingCommand
                        .responseReceived
                        .await()
                }
            } catch (
                e: TimeoutCancellationException
            ) {
                val removed =
                    pendingCommands.remove(
                        messageId,
                        pendingCommand
                    )

                if (removed) {
                    Log.e(
                        TAG,
                        "BabyNodeCAN response timeout: " +
                            "id=$messageId, " +
                            "command=" +
                            pendingCommand.command.command
                    )

                    statusFlow.emit(
                        CarStatusEvent.Error(
                            "BabyNodeCAN response timeout: " +
                                "id=$messageId " +
                                "command=" +
                                pendingCommand.command.command
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
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
                        connectedSocket.isConnected &&
                        !permanentlyClosed
                    ) {
                        val frame =
                            readBoundedFrame(
                                reader
                            )
                                ?: break

                        val packet =
                            frame.trim()

                        if (packet.isEmpty()) {
                            continue
                        }

                        processIncomingPacket(
                            packet
                        )
                    }

                    if (
                        !intentionalDisconnect &&
                        !permanentlyClosed
                    ) {
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
                    if (
                        !intentionalDisconnect &&
                        !permanentlyClosed
                    ) {
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

    private fun readBoundedFrame(
        reader: BufferedReader
    ): String? {
        val frame =
            StringBuilder()

        while (true) {
            val character =
                reader.read()

            if (character == -1) {
                return if (frame.isEmpty()) {
                    null
                } else {
                    throw IOException(
                        "Bluetooth frame ended before newline"
                    )
                }
            }

            if (
                character.toChar() == '\n'
            ) {
                return frame.toString()
            }

            if (
                character.toChar() != '\r'
            ) {
                frame.append(
                    character.toChar()
                )
            }

            if (
                frame.length >
                MAXIMUM_FRAME_LENGTH
            ) {
                throw IOException(
                    "Bluetooth frame exceeds " +
                        "$MAXIMUM_FRAME_LENGTH characters"
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
                    "type",
                    ""
                )

            if (
                !type.equals(
                    "response",
                    ignoreCase = true
                )
            ) {
                Log.w(
                    TAG,
                    "Unsupported incoming packet type"
                )

                return
            }

            if (!json.has("id")) {
                Log.w(
                    TAG,
                    "Response is missing id"
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
                    "Response contains invalid id"
                )

                return
            }

            val responseCommand =
                json.optString(
                    "command",
                    ""
                ).trim()

            if (responseCommand.isBlank()) {
                Log.w(
                    TAG,
                    "Response is missing command"
                )

                return
            }

            val responseStatus =
                json.optString(
                    "status",
                    ""
                )
                    .trim()
                    .lowercase()

            if (
                responseStatus !in
                VALID_RESPONSE_STATUSES
            ) {
                Log.w(
                    TAG,
                    "Response contains unsupported status"
                )

                return
            }

            val reason =
                json.optString(
                    "reason",
                    ""
                ).trim()

            val pendingCommand =
                pendingCommands[
                    commandId
                ]

            if (pendingCommand == null) {
                Log.w(
                    TAG,
                    "Ignoring unexpected, duplicate, or late " +
                        "response id=$commandId"
                )

                return
            }

            val expectedCommand =
                pendingCommand.command.command

            if (
                !responseCommand.equals(
                    expectedCommand,
                    ignoreCase = true
                )
            ) {
                Log.e(
                    TAG,
                    "Response command mismatch: " +
                        "id=$commandId"
                )

                val removed =
                    pendingCommands.remove(
                        commandId,
                        pendingCommand
                    )

                if (removed) {
                    pendingCommand
                        .responseReceived
                        .complete(Unit)

                    statusFlow.emit(
                        CarStatusEvent.Error(
                            "BabyNodeCAN response mismatch: " +
                                "expected $expectedCommand, " +
                                "received $responseCommand"
                        )
                    )
                }

                return
            }

            val removed =
                pendingCommands.remove(
                    commandId,
                    pendingCommand
                )

            if (!removed) {
                Log.w(
                    TAG,
                    "Response was already handled: id=$commandId"
                )

                return
            }

            pendingCommand
                .responseReceived
                .complete(Unit)

            val displayStatus =
                if (
                    reason.isNotBlank() &&
                    responseStatus != "ok"
                ) {
                    "$responseStatus: $reason"
                } else {
                    responseStatus
                }

            Log.i(
                TAG,
                "Matched BabyNodeCAN response: " +
                    "id=$commandId, " +
                    "command=$expectedCommand, " +
                    "status=$responseStatus"
            )

            statusFlow.emit(
                CarStatusEvent.CommandResponse(
                    commandId =
                        commandId,
                    status =
                        displayStatus
                )
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Invalid response packet",
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

    private suspend fun cancelAllPendingCommands(
        reason: String
    ) {
        val pendingEntries =
            pendingCommands
                .entries
                .toList()

        pendingEntries.forEach { entry ->
            val removed =
                pendingCommands.remove(
                    entry.key,
                    entry.value
                )

            if (removed) {
                entry.value
                    .responseReceived
                    .cancel()

                Log.w(
                    TAG,
                    "Pending command cancelled: " +
                        "id=${entry.key}, " +
                        "reason=$reason"
                )
            }
        }
    }

    private fun cancelAllPendingCommandsImmediately() {
        val pendingEntries =
            pendingCommands
                .entries
                .toList()

        pendingEntries.forEach { entry ->
            val removed =
                pendingCommands.remove(
                    entry.key,
                    entry.value
                )

            if (removed) {
                entry.value
                    .responseReceived
                    .cancel()
            }
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

        inputStream =
            null

        outputStream =
            null

        bluetoothSocket =
            null
    }

    private fun stopReceiveLoop() {
        receiveJob?.cancel()

        receiveJob =
            null
    }
}