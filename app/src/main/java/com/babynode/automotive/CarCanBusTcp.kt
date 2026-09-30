package com.babynode.automotive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
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
    private val port: Int = 1234
) : CarCanTransport {

    private val statusFlow = MutableSharedFlow<CarStatusEvent>(extraBufferCapacity = 64)

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 3000)

                socket = s
                writer = PrintWriter(s.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(s.getInputStream()))

                statusFlow.emit(CarStatusEvent.Connected("TCP"))

                // Start RX loop
                startRxLoop()
            } catch (e: Exception) {
                statusFlow.emit(CarStatusEvent.Error("TCP connect failed: ${e.message}", e))
            }
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                socket?.close()
                writer = null
                reader = null
                socket = null
                statusFlow.emit(CarStatusEvent.Disconnected("TCP"))
            } catch (e: Exception) {
                statusFlow.emit(CarStatusEvent.Error("TCP disconnect failed: ${e.message}", e))
            }
        }
    }

    override suspend fun sendFrame(frame: CarCanFrame) {
        withContext(Dispatchers.IO) {
            try {
                val cmd = buildString {
                    append("SEND ")
                    append(frame.id)
                    append(" ")
                    append(frame.data.size)
                    append(" ")
                    frame.data.forEach { b ->
                        append(b.toUByte().toString())
                        append(" ")
                    }
                }.trim()

                writer?.println(cmd)
                statusFlow.emit(CarStatusEvent.FrameSent(frame))
            } catch (e: Exception) {
                statusFlow.emit(CarStatusEvent.Error("TCP send failed: ${e.message}", e))
            }
        }
    }

    override fun status(): Flow<CarStatusEvent> = statusFlow

    /**
     * Reads lines from DuoCAN and emits FrameReceived events.
     */
    private fun startRxLoop() {
        Thread {
            try {
                while (true) {
                    val line = reader?.readLine() ?: break

                    if (line.startsWith("CAN_RX")) {
                        val parts = line.split(" ")
                        if (parts.size >= 4) {
                            val id = parts[1].toInt()
                            val dlc = parts[2].toInt()
                            val bytes = parts.drop(3).map { it.toInt().toByte() }.toByteArray()

                            val frame = CarCanFrame(id, bytes)
                            statusFlow.tryEmit(CarStatusEvent.FrameReceived(frame))
                        }
                    }
                }
            } catch (e: Exception) {
                statusFlow.tryEmit(CarStatusEvent.Error("TCP RX failed: ${e.message}", e))
            }
        }.start()
    }
}
