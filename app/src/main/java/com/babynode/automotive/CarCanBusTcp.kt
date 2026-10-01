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

/**
 * TCP transport for DuoCAN ESP32‑C6 Wi‑Fi Access Point.
 *
 * Connects to:
 *   SSID: DuoCAN-C6
 *   IP:   192.168.4.1
 *   PORT: 1234
 *
 * This is the REAL transport used by BabyNodeAutomotive.
 */
class CarCanBusTcp(
    private val host: String = "192.168.4.1",
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

    override suspend fun connect() {
        Log.i(TAG, "connect(): Attempting TCP connection to $host:$port")

        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                if (socket?.isConnected == true && socket?.isClosed == false) {
                    Log.i(TAG, "connect(): Already connected — skipping")
                    return@withLock
                }

                val newSocket = Socket()
                try {
                    Log.i(TAG, "connect(): Connecting with timeout=$CONNECT_TIMEOUT_MILLIS ms")
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

                    Log.i(TAG, "connect(): TCP connected successfully")
                    statusFlow.emit(CarStatusEvent.Connected("TCP"))

                    receiveJob = scope.launch(Dispatchers.IO) {
                        Log.i(TAG, "connect(): Starting TCP readLoop()")
                        readLoop(newSocket, newReader)
                    }

                } catch (e: CancellationException) {
                    Log.e(TAG, "connect(): Cancelled — closing socket")
                    runCatching { newSocket.close() }
                    throw e

                } catch (e: Exception) {
                    Log.e(TAG, "connect(): TCP connect failed: ${e.message}")
                    runCatching { newSocket.close() }
                    statusFlow.emit(CarStatusEvent.Error("TCP connect failed: ${e.message}", e))
                }
            }
        }
    }

    override suspend fun disconnect() {
        Log.i(TAG, "disconnect(): TCP disconnect requested")

        val job = withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                val activeSocket = socket
                val activeJob = receiveJob

                socket = null
                writer = null
                receiveJob = null

                try {
                    Log.i(TAG, "disconnect(): Closing socket")
                    activeSocket?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "disconnect(): TCP disconnect failed: ${e.message}")
                    statusFlow.emit(CarStatusEvent.Error("TCP disconnect failed: ${e.message}", e))
                }

                Log.i(TAG, "disconnect(): Emitting Disconnected(TCP)")
                statusFlow.emit(CarStatusEvent.Disconnected("TCP"))

                activeJob
            }
        }

        job?.cancelAndJoin()
        Log.i(TAG, "disconnect(): Receive job cancelled")
    }

    override suspend fun sendFrame(frame: CarCanFrame) {
        Log.i(TAG, "sendFrame(): Preparing to send frame id=${frame.id}, bytes=${frame.data.size}")

        withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                val activeWriter = writer
                if (activeWriter == null) {
                    Log.e(TAG, "sendFrame(): No writer — not connected")
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

                    Log.i(TAG, "sendFrame(): Sending command → \"$cmd\"")
                    activeWriter.println(cmd)

                    if (activeWriter.checkError()) {
                        Log.e(TAG, "sendFrame(): Writer reported output error")
                        throw IOException("TCP writer reported an output error")
                    }

                    Log.i(TAG, "sendFrame(): FrameSent emitted")
                    statusFlow.emit(CarStatusEvent.FrameSent(frame))

                } catch (e: CancellationException) {
                    Log.e(TAG, "sendFrame(): Cancelled")
                    throw e

                } catch (e: Exception) {
                    Log.e(TAG, "sendFrame(): TCP send failed: ${e.message}")
                    statusFlow.emit(CarStatusEvent.Error("TCP send failed: ${e.message}", e))
                    closeConnectionLocked()
                    statusFlow.emit(CarStatusEvent.Disconnected("TCP"))
                }
            }
        }
    }

    override fun status(): Flow<CarStatusEvent> {
        Log.i(TAG, "status(): Returning TCP status flow")
        return statusFlow
    }

    override fun close() {
        Log.i(TAG, "close(): Closing TCP transport")

        val activeSocket = socket
        socket = null
        writer = null

        receiveJob?.cancel()
        receiveJob = null

        val closeFailure = runCatching { activeSocket?.close() }.exceptionOrNull()
        if (closeFailure != null) {
            Log.e(TAG, "close(): TCP close failed: ${closeFailure.message}")
            statusFlow.tryEmit(CarStatusEvent.Error("TCP close failed: ${closeFailure.message}", closeFailure))
        }

        Log.i(TAG, "close(): Emitting Disconnected(TCP)")
        statusFlow.tryEmit(CarStatusEvent.Disconnected("TCP"))
    }

    private suspend fun readLoop(activeSocket: Socket, reader: BufferedReader) {
        Log.i(TAG, "readLoop(): Starting TCP RX loop")
        var failure: Exception? = null

        try {
            while (currentCoroutineContext().isActive) {
                val line = reader.readLine() ?: break
                Log.i(TAG, "readLoop(): RX line → \"$line\"")

                if (!line.startsWith("CAN_RX")) {
                    Log.i(TAG, "readLoop(): Ignored non-CAN_RX line")
                    continue
                }

                try {
                    val frame = parseReceivedFrame(line)
                    Log.i(TAG, "readLoop(): Parsed CAN_RX frame id=${frame.id}, bytes=${frame.data.size}")
                    statusFlow.emit(CarStatusEvent.FrameReceived(frame))

                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "readLoop(): Invalid TCP CAN frame: ${e.message}")
                    statusFlow.emit(CarStatusEvent.Error("Invalid TCP CAN frame: ${e.message}", e))
                }
            }

        } catch (e: CancellationException) {
            Log.e(TAG, "readLoop(): Cancelled")
            throw e

        } catch (e: Exception) {
            Log.e(TAG, "readLoop(): RX failure: ${e.message}")
            failure = e

        } finally {
            Log.i(TAG, "readLoop(): Finalizing RX loop")

            connectionMutex.withLock {
                if (socket === activeSocket) {
                    Log.i(TAG, "readLoop(): Closing connection due to RX termination")
                    closeConnectionLocked(cancelReceive = false)

                    if (failure != null) {
                        Log.e(TAG, "readLoop(): Emitting RX failure error")
                        statusFlow.emit(CarStatusEvent.Error("TCP RX failed: ${failure.message}", failure))
                    }

                    Log.i(TAG, "readLoop(): Emitting Disconnected(TCP)")
                    statusFlow.emit(CarStatusEvent.Disconnected("TCP"))
                }
            }
        }
    }

    private fun parseReceivedFrame(line: String): CarCanFrame {
        Log.i(TAG, "parseReceivedFrame(): Parsing → \"$line\"")

        val parts = line.trim().split(WHITESPACE)
        require(parts.size >= 3) { "frame header is incomplete" }

        val id = parts[1].toIntOrNull() ?: throw IllegalArgumentException("invalid frame ID")
        val dlc = parts[2].toIntOrNull() ?: throw IllegalArgumentException("invalid DLC")

        require(id in 0..0x1FFFFFFF) { "frame ID is outside the CAN identifier range" }
        require(dlc in 0..64 && parts.size == dlc + 3) { "DLC does not match payload length" }

        val data = parts.drop(3).map { value ->
            val byte = value.toIntOrNull()
            require(byte != null && byte in 0..255) { "payload byte is outside 0..255" }
            byte.toByte()
        }.toByteArray()

        Log.i(TAG, "parseReceivedFrame(): Parsed frame id=$id dlc=$dlc bytes=${data.size}")
        return CarCanFrame(id, data)
    }

    private fun closeConnectionLocked(cancelReceive: Boolean = true) {
        Log.i(TAG, "closeConnectionLocked(): Closing TCP connection (cancelReceive=$cancelReceive)")

        val activeSocket = socket
        socket = null
        writer = null

        runCatching { activeSocket?.close() }
            .onFailure { Log.e(TAG, "closeConnectionLocked(): Socket close failed: ${it.message}") }

        if (cancelReceive) {
            receiveJob?.cancel()
            Log.i(TAG, "closeConnectionLocked(): Receive job cancelled")
        }

        receiveJob = null
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 3_000
        val WHITESPACE = Regex("\\s+")
    }
}
