package com.babynode.automotive

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

class CarCanBusTcp(
    private val host: String = "10.84.212.50",
    private val port: Int = 1234,
    private val scope: CoroutineScope
) : CarCanTransport {

    private val TAG = "CarCanBusTcp"

    private val statusFlow = MutableSharedFlow<CarStatusEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val connectionMutex = Mutex()

    @Volatile
    private var socket: Socket? = null
    @Volatile
    private var writer: PrintWriter? = null
    @Volatile
    private var receiveJob: Job? = null

    @Volatile
    private var reconnectAttempt: Int = 0

    override suspend fun connect() {
        Log.i(TAG, "connect(): Attempting TCP connection to $host:$port with retries")

        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                if (socket?.isConnected == true && socket?.isClosed == false) {
                    Log.i(TAG, "connect(): Already connected — skipping")
                    reconnectAttempt = 0
                    return@withLock
                }

                var lastError: Exception? = null

                for (attempt in 1..MAX_CONNECT_ATTEMPTS) {
                    val newSocket = Socket()
                    try {
                        Log.i(TAG, "connect(): Attempt $attempt/$MAX_CONNECT_ATTEMPTS")
                        newSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS)

                        val newReader = BufferedReader(
                            InputStreamReader(newSocket.getInputStream(), Charsets.UTF_8)
                        )
                        val newWriter = PrintWriter(
                            newSocket.getOutputStream().bufferedWriter(Charsets.UTF_8),
                            true
                        )

                        socket = newSocket
                        writer = newWriter
                        reconnectAttempt = 0

                        Log.i(TAG, "connect(): TCP connected successfully")
                        statusFlow.emit(CarStatusEvent.Connected("TCP"))

                        receiveJob = scope.launch(Dispatchers.IO) {
                            readLoop(newSocket, newReader)
                        }

                        return@withLock

                    } catch (e: CancellationException) {
                        runCatching { newSocket.close() }
                        throw e

                    } catch (e: Exception) {
                        lastError = e
                        Log.e(TAG, "connect(): TCP connect failed: ${e.message}")
                        runCatching { newSocket.close() }

                        if (attempt < MAX_CONNECT_ATTEMPTS) {
                            Thread.sleep(CONNECT_RETRY_BACKOFF_MILLIS)
                        }
                    }
                }

                statusFlow.emit(
                    CarStatusEvent.Error(
                        "TCP connect failed after $MAX_CONNECT_ATTEMPTS attempts: ${lastError?.message}",
                        lastError
                    )
                )
            }
        }
    }

    override suspend fun disconnect() {
        val job = withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                val activeSocket = socket
                val activeJob = receiveJob

                socket = null
                writer = null
                receiveJob = null
                reconnectAttempt = 0

                runCatching { activeSocket?.close() }

                statusFlow.emit(CarStatusEvent.Disconnected("TCP"))
                activeJob
            }
        }

        job?.cancelAndJoin()
    }

    override suspend fun sendFrame(frame: CarCanFrame) {
        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                val activeWriter = writer
                if (activeWriter == null) {
                    statusFlow.emit(CarStatusEvent.Error("TCP send failed: not connected"))
                    return@withLock
                }

                try {
                    val cmd = buildString {
                        append("SEND ")
                        append(frame.id)
                        append(" ")
                        append(frame.data.size)
                        append(" ")
                        frame.data.forEach { byte ->
                            append(byte.toUByte().toString())
                            append(" ")
                        }
                    }.trim()

                    activeWriter.println(cmd)

                    if (activeWriter.checkError()) {
                        throw IOException("TCP writer reported an output error")
                    }

                    statusFlow.emit(CarStatusEvent.FrameSent(frame))

                } catch (e: Exception) {
                    statusFlow.emit(
                        CarStatusEvent.Error(
                            "TCP send failed: ${e.message}",
                            e
                        )
                    )
                    closeConnectionLocked()
                    statusFlow.emit(CarStatusEvent.Disconnected("TCP"))
                }
            }
        }
    }

    override fun status(): Flow<CarStatusEvent> = statusFlow

    override fun close() {
        val activeSocket = socket
        socket = null
        writer = null

        receiveJob?.cancel()
        receiveJob = null
        reconnectAttempt = 0

        runCatching { activeSocket?.close() }
        statusFlow.tryEmit(CarStatusEvent.Disconnected("TCP"))
    }

    private suspend fun readLoop(activeSocket: Socket, reader: BufferedReader) {
        var failure: Exception? = null

        try {
            while (currentCoroutineContext().isActive) {
                val line = reader.readLine() ?: break

                if (!line.startsWith("CAN_RX")) continue

                try {
                    val frame = parseReceivedFrame(line)
                    statusFlow.emit(CarStatusEvent.FrameReceived(frame))

                } catch (e: IllegalArgumentException) {
                    statusFlow.emit(
                        CarStatusEvent.Error(
                            "Invalid TCP CAN frame: ${e.message}",
                            e
                        )
                    )
                }
            }

        } catch (e: Exception) {
            failure = e

        } finally {
            connectionMutex.withLock {
                if (socket === activeSocket) {
                    closeConnectionLocked(cancelReceive = false)

                    if (failure != null) {
                        statusFlow.emit(
                            CarStatusEvent.Error(
                                "TCP RX failed: ${failure.message}",
                                failure
                            )
                        )
                    }

                    statusFlow.emit(CarStatusEvent.Disconnected("TCP"))
                }
            }

            scope.launch(Dispatchers.IO) {
                reconnectAttempt += 1
                val delayMs = computeBackoffDelay(reconnectAttempt)
                Log.i(TAG, "autoReconnect(): attempt=$reconnectAttempt backoff=${delayMs}ms")

                Thread.sleep(delayMs)

                connectionMutex.withLock {
                    if (socket == null) {
                        Log.i(TAG, "autoReconnect(): Attempting reconnect to $host:$port")
                        try {
                            connect()
                        } catch (e: Exception) {
                            Log.e(TAG, "autoReconnect(): Reconnect failed: ${e.message}")
                        }
                    } else {
                        Log.i(TAG, "autoReconnect(): Skipping — socket already reconnected")
                    }
                }
            }
        }
    }

    private fun computeBackoffDelay(attempt: Int): Long {
        val sequence = listOf(500L, 1000L, 2000L, 3000L, 5000L, 8000L)
        return if (attempt <= sequence.size) {
            sequence[attempt - 1]
        } else {
            10_000L
        }
    }

    private fun parseReceivedFrame(line: String): CarCanFrame {
        val parts = line.trim().split(WHITESPACE)
        require(parts.size >= 3)

        val id = parts[1].toIntOrNull() ?: throw IllegalArgumentException("invalid frame ID")
        val dlc = parts[2].toIntOrNull() ?: throw IllegalArgumentException("invalid DLC")

        require(id in 0..0x1FFFFFFF)
        require(dlc in 0..64 && parts.size == dlc + 3)

        val data = parts.drop(3).map { value ->
            val byte = value.toIntOrNull()
            require(byte != null && byte in 0..255)
            byte.toByte()
        }.toByteArray()

        return CarCanFrame(id, data)
    }

    private fun closeConnectionLocked(cancelReceive: Boolean = true) {
        val activeSocket = socket
        socket = null
        writer = null

        runCatching { activeSocket?.close() }

        if (cancelReceive) {
            receiveJob?.cancel()
        }

        receiveJob = null
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 3_000
        const val MAX_CONNECT_ATTEMPTS = 5
        const val CONNECT_RETRY_BACKOFF_MILLIS = 1_000L
        val WHITESPACE = Regex("\\s+")
    }
}