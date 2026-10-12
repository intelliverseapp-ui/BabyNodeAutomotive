package com.babynode.automotive

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarCanBusBluetoothConformanceTest {

    @Test
    fun immediateResponseRemainsTerminalAfterCommandSent() =
        runBlocking {
            val connection =
                FlushTriggeredResponseConnection()

            val transport =
                CarCanBusBluetooth()

            try {
                transport.installConnectionForTest(
                    connection =
                        connection
                )

                val eventsDeferred =
                    async(
                        start =
                            CoroutineStart.UNDISPATCHED
                    ) {
                        withTimeout(
                            5_000L
                        ) {
                            transport
                                .status()
                                .filter { event ->
                                    event is
                                        CarStatusEvent.CommandSent ||
                                        event is
                                        CarStatusEvent.CommandResponse
                                }
                                .take(
                                    2
                                )
                                .toList()
                        }
                    }

                val request =
                    transport.sendCommand(
                        CanonicalCommand(
                            command =
                                "LOCK_DOORS"
                        )
                    )

                val events =
                    eventsDeferred.await()

                val response =
                    events.filterIsInstance<
                        CarStatusEvent.CommandResponse
                    >()
                        .single()

                assertEquals(
                    request.sessionGeneration,
                    response.sessionGeneration
                )

                assertEquals(
                    request.requestId,
                    response.requestId
                )

                assertEquals(
                    request.command,
                    response.command
                )

                assertEquals(
                    "ok",
                    response.status
                )

                assertTrue(
                    events.any {
                        it is CarStatusEvent.CommandSent
                    }
                )

                assertEquals(
                    response,
                    CarRequestEventMatcher.selectBest(
                        events =
                            events,
                        request =
                            request
                    )
                )

                assertFalse(
                    transport
                        .close()
                        .any { event ->
                            CarRequestEventMatcher.matches(
                                event =
                                    event,
                                request =
                                    request
                            )
                        }
                )
            } finally {
                transport.close()
            }
        }


    @Test
    fun mismatchedResponseCommandFailsClosed() =
        runBlocking {
            val connection =
                FlushTriggeredResponseConnection(
                    responseCommand =
                        "UNLOCK_DOORS"
                )

            val transport =
                CarCanBusBluetooth()

            try {
                transport.installConnectionForTest(
                    connection =
                        connection
                )

                val eventsDeferred =
                    async(
                        start =
                            CoroutineStart.UNDISPATCHED
                    ) {
                        withTimeout(
                            5_000L
                        ) {
                            transport
                                .status()
                                .filter { event ->
                                    event is
                                        CarStatusEvent.CommandSent ||
                                        (
                                            event is
                                                CarStatusEvent.Error &&
                                                event.isRequestSpecific
                                        ) ||
                                        event is
                                            CarStatusEvent.CommandResponse
                                }
                                .take(
                                    2
                                )
                                .toList()
                        }
                    }

                val request =
                    transport.sendCommand(
                        CanonicalCommand(
                            command =
                                "LOCK_DOORS"
                        )
                    )

                val events =
                    eventsDeferred.await()

                val error =
                    events.filterIsInstance<
                        CarStatusEvent.Error
                    >()
                        .single()

                assertEquals(
                    request.sessionGeneration,
                    error.sessionGeneration
                )

                assertEquals(
                    request.requestId,
                    error.requestId
                )

                assertEquals(
                    request.command,
                    error.command
                )

                assertTrue(
                    error.message.contains(
                        "response mismatch",
                        ignoreCase =
                            true
                    )
                )

                assertTrue(
                    error.message.contains(
                        "expected LOCK_DOORS"
                    )
                )

                assertTrue(
                    error.message.contains(
                        "received UNLOCK_DOORS"
                    )
                )

                assertFalse(
                    events.any {
                        it is
                            CarStatusEvent.CommandResponse
                    }
                )

                assertTrue(
                    events.any {
                        it is
                            CarStatusEvent.CommandSent
                    }
                )

                assertEquals(
                    error,
                    CarRequestEventMatcher.selectBest(
                        events =
                            events,
                        request =
                            request
                    )
                )

                assertFalse(
                    transport
                        .close()
                        .any { event ->
                            CarRequestEventMatcher.matches(
                                event =
                                    event,
                                request =
                                    request
                            )
                        }
                )
            } finally {
                transport.close()
            }
        }

    private class FlushTriggeredResponseConnection(
        private val responseCommand:
            String =
            "LOCK_DOORS"
    ) : RfcommConnection {

        override var isConnected =
            false
            private set

        private val peerWriter =
            PipedOutputStream()

        override val inputStream =
            PipedInputStream(
                peerWriter
            )

        private val writtenBytes =
            ByteArrayOutputStream()

        private val responseReleased =
            AtomicBoolean(
                false
            )

        override val outputStream:
            OutputStream =
            object : OutputStream() {

                override fun write(
                    value: Int
                ) {
                    writtenBytes.write(
                        value
                    )
                }

                override fun write(
                    bytes: ByteArray,
                    offset: Int,
                    length: Int
                ) {
                    writtenBytes.write(
                        bytes,
                        offset,
                        length
                    )
                }

                override fun flush() {
                    val packet =
                        writtenBytes
                            .toString(
                                StandardCharsets.UTF_8.name()
                            )
                            .trim()

                    val json =
                        JSONObject(
                            packet
                        )

                    val requestId =
                        json.getLong(
                            "id"
                        )

                    val command =
                        json.getString(
                            "command"
                        )

                    assertEquals(
                        1L,
                        requestId
                    )

                    assertEquals(
                        "LOCK_DOORS",
                        command
                    )

                    if (
                        responseReleased.compareAndSet(
                            false,
                            true
                        )
                    ) {
                        val response =
                            JSONObject()
                                .put(
                                    "id",
                                    requestId
                                )
                                .put(
                                    "version",
                                    BabyNodeProtocol.VERSION
                                )
                                .put(
                                    "type",
                                    "response"
                                )
                                .put(
                                    "command",
                                    responseCommand
                                )
                                .put(
                                    "status",
                                    "ok"
                                )
                                .toString() +
                                "\n"

                        peerWriter.write(
                            response.toByteArray(
                                StandardCharsets.UTF_8
                            )
                        )

                        peerWriter.flush()
                    }
                }
            }

        override fun connect() {
            isConnected =
                true
        }

        override fun close() {
            isConnected =
                false

            runCatching {
                peerWriter.close()
            }

            runCatching {
                inputStream.close()
            }

            runCatching {
                writtenBytes.close()
            }
        }
    }
}
