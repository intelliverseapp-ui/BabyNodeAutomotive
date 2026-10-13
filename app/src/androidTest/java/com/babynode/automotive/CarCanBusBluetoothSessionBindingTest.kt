package com.babynode.automotive

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarCanBusBluetoothSessionBindingTest {

    private val sessionA =
        1_111L

    private val sessionB =
        2_222L

    @Test
    fun handshakeIsSentWithoutSessionId() =
        withTransport { transport, peer ->
            val request =
                transport.sendCommand(
                    CanonicalCommand(
                        "protocol.handshake"
                    )
                )

            val packet =
                peer.nextPacket()

            assertEquals(
                "protocol.handshake",
                packet.getString("command")
            )

            assertEquals(
                request.requestId,
                packet.getLong("id")
            )

            assertFalse(
                packet.has("session_id")
            )
        }

    @Test
    fun successfulHandshakeRetainsSessionAndStampsFollowingCommands() =
        withTransport { transport, peer ->
            handshake(
                transport,
                peer,
                sessionA
            )

            transport.sendCommand(
                CanonicalCommand(
                    "config.module",
                    "single"
                )
            )

            val configPacket =
                peer.nextPacket()

            assertEquals(
                "config.module",
                configPacket.getString("command")
            )

            assertEquals(
                sessionA,
                configPacket.getLong("session_id")
            )

            transport.sendCommand(
                CanonicalCommand(
                    "LOCK_DOORS"
                )
            )

            val userPacket =
                peer.nextPacket()

            assertEquals(
                "LOCK_DOORS",
                userPacket.getString("command")
            )

            assertEquals(
                sessionA,
                userPacket.getLong("session_id")
            )
        }

    @Test
    fun postHandshakeSendIsRejectedWithoutNegotiatedSession() =
        withTransport { transport, peer ->
            val result =
                runCatching {
                    transport.sendCommand(
                        CanonicalCommand(
                            "LOCK_DOORS"
                        )
                    )
                }

            assertTrue(
                result.isFailure
            )

            assertNull(
                peer.pollPacket()
            )
        }

    @Test
    fun matchingSessionResponseResolvesRequestExactlyOnce() =
        withTransport { transport, peer ->
            handshake(
                transport,
                peer,
                sessionA
            )

            val responses =
                collectResponses(
                    transport
                )

            try {
                val request =
                    transport.sendCommand(
                        CanonicalCommand(
                            "LOCK_DOORS"
                        )
                    )

                peer.nextPacket()

                peer.respond(
                    ordinaryResponse(
                        request.requestId,
                        "LOCK_DOORS",
                        sessionA
                    )
                )

                peer.respond(
                    ordinaryResponse(
                        request.requestId,
                        "LOCK_DOORS",
                        sessionA
                    )
                )

                awaitResponseCount(
                    responses,
                    1
                )

                delay(200L)

                assertEquals(
                    1,
                    responses.size
                )
            } finally {
                responses.cancelAndJoin()
            }
        }

    @Test
    fun missingResponseSessionIdIsIgnored() =
        invalidSessionResponseIsIgnored(
            omitSessionId =
                true
        )

    @Test
    fun malformedResponseSessionIdIsIgnored() =
        invalidSessionResponseIsIgnored(
            sessionValue =
                sessionA.toString()
        )

    @Test
    fun fractionalResponseSessionIdIsIgnored() =
        invalidSessionResponseIsIgnored(
            sessionValue =
                1_111.5
        )

    @Test
    fun zeroResponseSessionIdIsIgnored() =
        invalidSessionResponseIsIgnored(
            sessionValue =
                0L
        )

    @Test
    fun outOfRangeResponseSessionIdIsIgnored() =
        invalidSessionResponseIsIgnored(
            sessionValue =
                BabyNodeProtocol.MAX_SESSION_ID + 1L
        )

    @Test
    fun mismatchedResponseSessionIdIsIgnored() =
        invalidSessionResponseIsIgnored(
            sessionValue =
                sessionA + 1L
        )

    @Test
    fun oldSessionResponseCannotResolveNewSessionRequest() =
        runBlocking {
            val transport =
                CarCanBusBluetooth()

            try {
                val oldPeer =
                    ScriptedPeerConnection()

                transport.installConnectionForTest(
                    oldPeer
                )

                handshake(
                    transport,
                    oldPeer,
                    sessionA
                )

                val newPeer =
                    ScriptedPeerConnection()

                transport.installConnectionForTest(
                    newPeer
                )

                handshake(
                    transport,
                    newPeer,
                    sessionB
                )

                val responses =
                    collectResponses(
                        transport
                    )

                try {
                    val request =
                        transport.sendCommand(
                            CanonicalCommand(
                                "LOCK_DOORS"
                            )
                        )

                    assertEquals(
                        sessionB,
                        newPeer.nextPacket()
                            .getLong("session_id")
                    )

                    newPeer.respond(
                        ordinaryResponse(
                            request.requestId,
                            "LOCK_DOORS",
                            sessionA
                        )
                    )

                    delay(300L)

                    assertTrue(
                        responses.isEmpty()
                    )

                    newPeer.respond(
                        ordinaryResponse(
                            request.requestId,
                            "LOCK_DOORS",
                            sessionB
                        )
                    )

                    awaitResponseCount(
                        responses,
                        1
                    )

                    delay(200L)

                    assertEquals(
                        1,
                        responses.size
                    )
                } finally {
                    responses.cancelAndJoin()
                }
            } finally {
                transport.close()
            }
        }

    @Test
    fun disconnectClearsPeerSession() =
        withTransport { transport, peer ->
            handshake(
                transport,
                peer,
                sessionA
            )

            transport.disconnect()

            val replacement =
                ScriptedPeerConnection()

            transport.installConnectionForTest(
                replacement
            )

            val result =
                runCatching {
                    transport.sendCommand(
                        CanonicalCommand(
                            "LOCK_DOORS"
                        )
                    )
                }

            assertTrue(
                result.isFailure
            )

            assertNull(
                replacement.pollPacket()
            )
        }

    @Test
    fun connectionReplacementClearsPeerSession() =
        withTransport { transport, peer ->
            handshake(
                transport,
                peer,
                sessionA
            )

            val replacement =
                ScriptedPeerConnection()

            transport.installConnectionForTest(
                replacement
            )

            val result =
                runCatching {
                    transport.sendCommand(
                        CanonicalCommand(
                            "LOCK_DOORS"
                        )
                    )
                }

            assertTrue(
                result.isFailure
            )

            assertNull(
                replacement.pollPacket()
            )
        }

    @Test
    fun unsuccessfulHandshakeDoesNotInstallSession() =
        withTransport { transport, peer ->
            val request =
                transport.sendCommand(
                    CanonicalCommand(
                        "protocol.handshake"
                    )
                )

            peer.nextPacket()

            peer.respond(
                handshakeResponse(
                    request.requestId,
                    sessionA
                ).put(
                    "status",
                    "error"
                )
            )

            delay(300L)

            assertSendRejected(
                transport,
                peer
            )
        }

    @Test
    fun invalidHandshakeDoesNotInstallSession() =
        withTransport { transport, peer ->
            val request =
                transport.sendCommand(
                    CanonicalCommand(
                        "protocol.handshake"
                    )
                )

            peer.nextPacket()

            peer.respond(
                handshakeResponse(
                    request.requestId,
                    0L
                )
            )

            delay(300L)

            assertSendRejected(
                transport,
                peer
            )
        }

    @Test
    fun handshakeWithInvalidCapabilitiesDoesNotInstallSession() =
        withTransport { transport, peer ->
            val request =
                transport.sendCommand(
                    CanonicalCommand(
                        "protocol.handshake"
                    )
                )

            peer.nextPacket()

            peer.respond(
                handshakeResponse(
                    request.requestId,
                    sessionA
                ).put(
                    "can_transmit",
                    true
                )
            )

            delay(300L)

            assertSendRejected(
                transport,
                peer
            )
        }

    private fun invalidSessionResponseIsIgnored(
        sessionValue: Any? =
            null,
        omitSessionId: Boolean =
            false
    ) =
        withTransport { transport, peer ->
            handshake(
                transport,
                peer,
                sessionA
            )

            val responses =
                collectResponses(
                    transport
                )

            try {
                val request =
                    transport.sendCommand(
                        CanonicalCommand(
                            "LOCK_DOORS"
                        )
                    )

                peer.nextPacket()

                val invalid =
                    ordinaryResponse(
                        request.requestId,
                        "LOCK_DOORS",
                        sessionA
                    )

                if (omitSessionId) {
                    invalid.remove(
                        "session_id"
                    )
                } else {
                    invalid.put(
                        "session_id",
                        sessionValue
                    )
                }

                peer.respond(
                    invalid
                )

                delay(300L)

                assertTrue(
                    responses.isEmpty()
                )

                peer.respond(
                    ordinaryResponse(
                        request.requestId,
                        "LOCK_DOORS",
                        sessionA
                    )
                )

                awaitResponseCount(
                    responses,
                    1
                )

                delay(200L)

                assertEquals(
                    1,
                    responses.size
                )
            } finally {
                responses.cancelAndJoin()
            }
        }

    private suspend fun assertSendRejected(
        transport: CarCanBusBluetooth,
        peer: ScriptedPeerConnection
    ) {
        val result =
            runCatching {
                transport.sendCommand(
                    CanonicalCommand(
                        "LOCK_DOORS"
                    )
                )
            }

        assertTrue(
            result.isFailure
        )

        assertNull(
            peer.pollPacket()
        )
    }

    private suspend fun handshake(
        transport: CarCanBusBluetooth,
        peer: ScriptedPeerConnection,
        sessionId: Long
    ) {
        val request =
            transport.sendCommand(
                CanonicalCommand(
                    "protocol.handshake"
                )
            )

        peer.nextPacket()

        peer.respond(
            handshakeResponse(
                request.requestId,
                sessionId
            )
        )

        // Allow the receive loop to process the handshake response.
        delay(300L)
    }

    private fun handshakeResponse(
        requestId: Long,
        sessionId: Long
    ): JSONObject =
        JSONObject()
            .put("id", requestId)
            .put("version", BabyNodeProtocol.VERSION)
            .put("type", "response")
            .put("command", "protocol.handshake")
            .put("status", "ok")
            .put("session_id", sessionId)
            .put("module", BabyNodePeerContract.SUPPORTED_MODULE)
            .put("can_transmit", BabyNodePeerContract.allowsVehicleActuation)
            .put(
                "vehicle_commands",
                BabyNodePeerContract.SUPPORTED_VEHICLE_COMMAND_CAPABILITY
            )

    private fun ordinaryResponse(
        requestId: Long,
        command: String,
        sessionId: Long
    ): JSONObject =
        JSONObject()
            .put("id", requestId)
            .put("version", BabyNodeProtocol.VERSION)
            .put("type", "response")
            .put("command", command)
            .put("status", "ok")
            .put("session_id", sessionId)

    private class ResponseCollector(
        val responses: MutableList<CarStatusEvent.CommandResponse>,
        val job: Job
    ) : List<CarStatusEvent.CommandResponse> by responses {

        suspend fun cancelAndJoin() {
            job.cancelAndJoin()
        }
    }

    private fun kotlinx.coroutines.CoroutineScope.collectResponses(
        transport: CarCanBusBluetooth
    ): ResponseCollector {
        val responses =
            java.util.Collections.synchronizedList(
                mutableListOf<CarStatusEvent.CommandResponse>()
            )

        val job =
            launch(
                Dispatchers.IO,
                start =
                    CoroutineStart.UNDISPATCHED
            ) {
                transport
                    .status()
                    .filterIsInstance<CarStatusEvent.CommandResponse>()
                    .collect {
                        responses += it
                    }
            }

        return ResponseCollector(
            responses =
                responses,
            job =
                job
        )
    }

    private suspend fun awaitResponseCount(
        responses: List<CarStatusEvent.CommandResponse>,
        count: Int
    ) {
        withTimeout(
            5_000L
        ) {
            while (responses.size < count) {
                delay(20L)
            }
        }
    }

    private fun withTransport(
        block: suspend kotlinx.coroutines.CoroutineScope.(
            CarCanBusBluetooth,
            ScriptedPeerConnection
        ) -> Unit
    ) =
        runBlocking {
            val transport =
                CarCanBusBluetooth()

            val peer =
                ScriptedPeerConnection()

            try {
                transport.installConnectionForTest(
                    peer
                )

                block(
                    transport,
                    peer
                )
            } finally {
                transport.close()
            }
        }

    private class ScriptedPeerConnection :
        RfcommConnection {

        override var isConnected =
            false
            private set

        private val peerWriter =
            PipedOutputStream()

        override val inputStream:
            InputStream =
            PipedInputStream(
                peerWriter
            )

        private val packets =
            LinkedBlockingQueue<JSONObject>()

        private val buffer =
            ByteArrayOutputStream()

        override val outputStream:
            OutputStream =
            object : OutputStream() {
                override fun write(
                    value: Int
                ) {
                    buffer.write(
                        value
                    )
                }

                override fun write(
                    bytes: ByteArray,
                    offset: Int,
                    length: Int
                ) {
                    buffer.write(
                        bytes,
                        offset,
                        length
                    )
                }

                override fun flush() {
                    val text =
                        buffer.toString(
                            StandardCharsets.UTF_8.name()
                        ).trim()

                    buffer.reset()

                    if (text.isNotEmpty()) {
                        packets.add(
                            JSONObject(
                                text
                            )
                        )
                    }
                }
            }

        fun nextPacket():
            JSONObject {
            val packet =
                packets.poll(
                    5,
                    TimeUnit.SECONDS
                )

            assertNotNull(
                packet
            )

            return packet!!
        }

        fun pollPacket():
            JSONObject? =
            packets.poll(
                200,
                TimeUnit.MILLISECONDS
            )

        fun respond(
            response: JSONObject
        ) {
            peerWriter.write(
                (response.toString() + "\n")
                    .toByteArray(
                        StandardCharsets.UTF_8
                    )
            )

            peerWriter.flush()
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
        }
    }
}
