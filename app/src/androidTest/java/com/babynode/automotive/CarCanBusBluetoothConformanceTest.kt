package com.babynode.automotive

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
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


    @Test
    fun duplicatePeerResponseProducesOneTerminalResult() =
        runBlocking {
            val connection =
                FlushTriggeredResponseConnection(
                    responseCopies =
                        2
                )

            val transport =
                CarCanBusBluetooth()

            val observedEvents =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect { event ->
                            observedEvents +=
                                event
                        }
                }

            try {
                transport.installConnectionForTest(
                    connection =
                        connection
                )

                val request =
                    transport.sendCommand(
                        CanonicalCommand(
                            command =
                                "LOCK_DOORS"
                        )
                    )

                withTimeout(
                    5_000L
                ) {
                    while (
                        observedEvents.none {
                            it is
                                CarStatusEvent.CommandSent
                        } ||
                        observedEvents.none {
                            it is
                                CarStatusEvent.CommandResponse
                        }
                    ) {
                        delay(
                            10L
                        )
                    }
                }

                delay(
                    250L
                )

                val matchingResponses =
                    observedEvents
                        .filterIsInstance<
                            CarStatusEvent.CommandResponse
                        >()
                        .filter { event ->
                            CarRequestEventMatcher.matches(
                                event =
                                    event,
                                request =
                                    request
                            )
                        }

                val matchingErrors =
                    observedEvents
                        .filterIsInstance<
                            CarStatusEvent.Error
                        >()
                        .filter { event ->
                            CarRequestEventMatcher.matches(
                                event =
                                    event,
                                request =
                                    request
                            )
                        }

                assertEquals(
                    1,
                    matchingResponses.size
                )

                assertTrue(
                    matchingErrors.isEmpty()
                )

                assertEquals(
                    matchingResponses.single(),
                    CarRequestEventMatcher.selectBest(
                        events =
                            observedEvents.toList(),
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
                collector.cancelAndJoin()
                transport.close()
            }
        }


    @Test
    fun closeReturnsOneTerminalOutcomeForPendingRequest() =
        runBlocking {
            val connection =
                FlushTriggeredResponseConnection(
                    responseCopies =
                        0
                )

            val transport =
                CarCanBusBluetooth()

            try {
                transport.installConnectionForTest(
                    connection =
                        connection
                )

                val sentEventDeferred =
                    async(
                        start =
                            CoroutineStart.UNDISPATCHED
                    ) {
                        withTimeout(
                            5_000L
                        ) {
                            transport
                                .status()
                                .filterIsInstance<
                                    CarStatusEvent.CommandSent
                                >()
                                .first()
                        }
                    }

                val request =
                    transport.sendCommand(
                        CanonicalCommand(
                            command =
                                "LOCK_DOORS"
                        )
                    )

                val sentEvent =
                    sentEventDeferred.await()

                assertTrue(
                    CarRequestEventMatcher.matches(
                        event =
                            sentEvent,
                        request =
                            request
                    )
                )

                val terminalEvents =
                    transport.close()

                val matchingTerminalEvents =
                    terminalEvents.filter { event ->
                        CarRequestEventMatcher.matches(
                            event =
                                event,
                            request =
                                request
                        )
                    }

                assertEquals(
                    1,
                    matchingTerminalEvents.size
                )

                val terminalError =
                    matchingTerminalEvents
                        .single() as
                        CarStatusEvent.Error

                assertTrue(
                    terminalError.message.contains(
                        "outcome unknown",
                        ignoreCase =
                            true
                    )
                )

                assertEquals(
                    request.sessionGeneration,
                    terminalError.sessionGeneration
                )

                assertEquals(
                    request.requestId,
                    terminalError.requestId
                )

                assertEquals(
                    request.command,
                    terminalError.command
                )

                assertTrue(
                    transport.close()
                        .isEmpty()
                )
            } finally {
                transport.close()
            }
        }


    @Test
    fun unsupportedResponseStatusFailsClosed() =
        runBlocking {
            val connection =
                FlushTriggeredResponseConnection(
                    responseStatus =
                        "maybe"
                )

            val transport =
                CarCanBusBluetooth()

            val observedEvents =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect { event ->
                            observedEvents +=
                                event
                        }
                }

            try {
                transport.installConnectionForTest(
                    connection =
                        connection
                )

                val request =
                    transport.sendCommand(
                        CanonicalCommand(
                            command =
                                "LOCK_DOORS"
                        )
                    )

                withTimeout(
                    5_000L
                ) {
                    while (
                        observedEvents.none {
                            it is
                                CarStatusEvent.CommandSent
                        }
                    ) {
                        delay(
                            10L
                        )
                    }
                }

                delay(
                    250L
                )

                assertFalse(
                    observedEvents.any { event ->
                        event is
                            CarStatusEvent.CommandResponse &&
                            CarRequestEventMatcher.matches(
                                event =
                                    event,
                                request =
                                    request
                            )
                    }
                )

                assertFalse(
                    observedEvents.any { event ->
                        event is
                            CarStatusEvent.Error &&
                            CarRequestEventMatcher.matches(
                                event =
                                    event,
                                request =
                                    request
                            )
                    }
                )

                val terminalEvents =
                    transport.close()

                val matchingTerminalEvents =
                    terminalEvents.filter { event ->
                        CarRequestEventMatcher.matches(
                            event =
                                event,
                            request =
                                request
                        )
                    }

                assertEquals(
                    1,
                    matchingTerminalEvents.size
                )

                val terminalError =
                    matchingTerminalEvents
                        .single() as
                        CarStatusEvent.Error

                assertTrue(
                    terminalError.message.contains(
                        "outcome unknown",
                        ignoreCase =
                            true
                    )
                )

                assertEquals(
                    request.sessionGeneration,
                    terminalError.sessionGeneration
                )

                assertEquals(
                    request.requestId,
                    terminalError.requestId
                )

                assertEquals(
                    request.command,
                    terminalError.command
                )

                assertTrue(
                    transport.close()
                        .isEmpty()
                )
            } finally {
                collector.cancelAndJoin()
                transport.close()
            }
        }


    @Test
    fun reverseOrderResponsesRemainCorrelatedToTheirRequests() =
        runBlocking {
            val connection =
                ReverseOrderResponseConnection()

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
                                    4
                                )
                                .toList()
                        }
                    }

                val lockRequest =
                    transport.sendCommand(
                        CanonicalCommand(
                            command =
                                "LOCK_DOORS"
                        )
                    )

                val headlightsRequest =
                    transport.sendCommand(
                        CanonicalCommand(
                            command =
                                "HEADLIGHTS_ON"
                        )
                    )

                val events =
                    eventsDeferred.await()

                val responses =
                    events.filterIsInstance<
                        CarStatusEvent.CommandResponse
                    >()

                assertEquals(
                    2,
                    responses.size
                )

                val firstResponse =
                    responses[
                        0
                    ]

                val secondResponse =
                    responses[
                        1
                    ]

                assertEquals(
                    headlightsRequest.requestId,
                    firstResponse.requestId
                )

                assertEquals(
                    headlightsRequest.command,
                    firstResponse.command
                )

                assertEquals(
                    lockRequest.requestId,
                    secondResponse.requestId
                )

                assertEquals(
                    lockRequest.command,
                    secondResponse.command
                )

                assertEquals(
                    firstResponse,
                    CarRequestEventMatcher.selectBest(
                        events =
                            events,
                        request =
                            headlightsRequest
                    )
                )

                assertEquals(
                    secondResponse,
                    CarRequestEventMatcher.selectBest(
                        events =
                            events,
                        request =
                            lockRequest
                    )
                )

                assertFalse(
                    events.any { event ->
                        event is
                            CarStatusEvent.Error &&
                            (
                                CarRequestEventMatcher.matches(
                                    event =
                                        event,
                                    request =
                                        lockRequest
                                ) ||
                                CarRequestEventMatcher.matches(
                                    event =
                                        event,
                                    request =
                                        headlightsRequest
                                )
                            )
                    }
                )

                val terminalEvents =
                    transport.close()

                assertFalse(
                    terminalEvents.any { event ->
                        CarRequestEventMatcher.matches(
                            event =
                                event,
                            request =
                                lockRequest
                        ) ||
                            CarRequestEventMatcher.matches(
                                event =
                                    event,
                                request =
                                    headlightsRequest
                            )
                    }
                )
            } finally {
                transport.close()
            }
        }


    private class ReverseOrderResponseConnection :
        RfcommConnection {

        override var isConnected =
            false
            private set

        private val peerWriter =
            PipedOutputStream()

        override val inputStream =
            PipedInputStream(
                peerWriter
            )

        private val currentPacket =
            ByteArrayOutputStream()

        private val requests =
            mutableListOf<
                Pair<Long, String>
            >()

        override val outputStream:
            OutputStream =
            object : OutputStream() {

                override fun write(
                    value: Int
                ) {
                    currentPacket.write(
                        value
                    )
                }

                override fun write(
                    bytes: ByteArray,
                    offset: Int,
                    length: Int
                ) {
                    currentPacket.write(
                        bytes,
                        offset,
                        length
                    )
                }

                override fun flush() {
                    val packet =
                        currentPacket
                            .toString(
                                StandardCharsets.UTF_8.name()
                            )
                            .trim()

                    currentPacket.reset()

                    val json =
                        JSONObject(
                            packet
                        )

                    requests +=
                        json.getLong(
                            "id"
                        ) to
                            json.getString(
                                "command"
                            )

                    if (
                        requests.size ==
                        2
                    ) {
                        requests
                            .asReversed()
                            .forEach {
                                (
                                    requestId,
                                    command
                                ) ->

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
                                            command
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
                currentPacket.close()
            }
        }
    }


    @Test
    fun disconnectResolvesPendingRequestExactlyOnce() =
        runBlocking {
            val connection =
                FlushTriggeredResponseConnection(
                    responseCopies =
                        0
                )

            val transport =
                CarCanBusBluetooth()

            val observedEvents =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect { event ->
                            observedEvents +=
                                event
                        }
                }

            try {
                transport.installConnectionForTest(
                    connection =
                        connection
                )

                val request =
                    transport.sendCommand(
                        CanonicalCommand(
                            command =
                                "LOCK_DOORS"
                        )
                    )

                withTimeout(
                    5_000L
                ) {
                    while (
                        observedEvents.none { event ->
                            event is
                                CarStatusEvent.CommandSent &&
                                CarRequestEventMatcher.matches(
                                    event =
                                        event,
                                    request =
                                        request
                                )
                        }
                    ) {
                        delay(
                            10L
                        )
                    }
                }

                transport.disconnect()

                withTimeout(
                    5_000L
                ) {
                    while (
                        observedEvents.none { event ->
                            event is
                                CarStatusEvent.Error &&
                                CarRequestEventMatcher.matches(
                                    event =
                                        event,
                                    request =
                                        request
                                )
                        } ||
                        observedEvents.none { event ->
                            event is
                                CarStatusEvent.Disconnected
                        }
                    ) {
                        delay(
                            10L
                        )
                    }
                }

                delay(
                    250L
                )

                val matchingErrors =
                    observedEvents
                        .filterIsInstance<
                            CarStatusEvent.Error
                        >()
                        .filter { event ->
                            CarRequestEventMatcher.matches(
                                event =
                                    event,
                                request =
                                    request
                            )
                        }

                assertEquals(
                    1,
                    matchingErrors.size
                )

                val terminalError =
                    matchingErrors.single()

                assertTrue(
                    terminalError.message.contains(
                        "outcome unknown",
                        ignoreCase =
                            true
                    )
                )

                assertEquals(
                    request.sessionGeneration,
                    terminalError.sessionGeneration
                )

                assertEquals(
                    request.requestId,
                    terminalError.requestId
                )

                assertEquals(
                    request.command,
                    terminalError.command
                )

                assertEquals(
                    1,
                    observedEvents.count { event ->
                        event is
                            CarStatusEvent.Disconnected
                    }
                )

                assertFalse(
                    connection.isConnected
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
                collector.cancelAndJoin()
                transport.close()
            }
        }


    @Test
    fun flushFailureAfterWriteReportsOutcomeUnknownExactlyOnce() =
        runBlocking {
            val connection =
                FlushFailureConnection()

            val transport =
                CarCanBusBluetooth()

            val observedEvents =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect { event ->
                            observedEvents +=
                                event
                        }
                }

            try {
                transport.installConnectionForTest(
                    connection =
                        connection
                )

                val exception =
                    runCatching {
                        transport.sendCommand(
                            CanonicalCommand(
                                command =
                                    "LOCK_DOORS"
                            )
                        )
                    }
                        .exceptionOrNull()

                assertTrue(
                    exception is
                        CarCommandOutcomeUnknownException
                )

                val outcomeUnknownException =
                    exception as
                        CarCommandOutcomeUnknownException

                val request =
                    outcomeUnknownException.request

                assertTrue(
                    outcomeUnknownException.message
                        .orEmpty()
                        .contains(
                            "outcome unknown",
                            ignoreCase =
                                true
                        )
                )

                withTimeout(
                    5_000L
                ) {
                    while (
                        observedEvents.none { event ->
                            event is
                                CarStatusEvent.Error &&
                                CarRequestEventMatcher.matches(
                                    event =
                                        event,
                                    request =
                                        request
                                )
                        } ||
                        observedEvents.none { event ->
                            event is
                                CarStatusEvent.Disconnected
                        }
                    ) {
                        delay(
                            10L
                        )
                    }
                }

                delay(
                    250L
                )

                val matchingErrors =
                    observedEvents
                        .filterIsInstance<
                            CarStatusEvent.Error
                        >()
                        .filter { event ->
                            CarRequestEventMatcher.matches(
                                event =
                                    event,
                                request =
                                    request
                            )
                        }

                assertEquals(
                    1,
                    matchingErrors.size
                )

                val terminalError =
                    matchingErrors.single()

                assertTrue(
                    terminalError.message.contains(
                        "outcome unknown",
                        ignoreCase =
                            true
                    )
                )

                assertEquals(
                    request.sessionGeneration,
                    terminalError.sessionGeneration
                )

                assertEquals(
                    request.requestId,
                    terminalError.requestId
                )

                assertEquals(
                    request.command,
                    terminalError.command
                )

                assertFalse(
                    observedEvents.any { event ->
                        event is
                            CarStatusEvent.CommandSent &&
                            CarRequestEventMatcher.matches(
                                event =
                                    event,
                                request =
                                    request
                            )
                    }
                )

                assertEquals(
                    1,
                    observedEvents.count { event ->
                        event is
                            CarStatusEvent.Disconnected
                    }
                )

                assertFalse(
                    connection.isConnected
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
                collector.cancelAndJoin()
                transport.close()
            }
        }


    private class FlushFailureConnection :
        RfcommConnection {

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
                    assertTrue(
                        writtenBytes.size() >
                            0
                    )

                    throw IOException(
                        "Simulated flush failure"
                    )
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


    @Test
    fun silentPeerProducesOneCorrelatedTimeoutOutcome() =
        runBlocking {
            val connection =
                FlushTriggeredResponseConnection(
                    responseCopies =
                        0
                )

            val transport =
                CarCanBusBluetooth(
                    responseTimeoutMillis =
                        100L
                )

            val observedEvents =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect { event ->
                            observedEvents +=
                                event
                        }
                }

            try {
                transport.installConnectionForTest(
                    connection =
                        connection
                )

                val request =
                    transport.sendCommand(
                        CanonicalCommand(
                            command =
                                "LOCK_DOORS"
                        )
                    )

                withTimeout(
                    5_000L
                ) {
                    while (
                        observedEvents.none { event ->
                            event is
                                CarStatusEvent.Error &&
                                CarRequestEventMatcher.matches(
                                    event =
                                        event,
                                    request =
                                        request
                                )
                        }
                    ) {
                        delay(
                            10L
                        )
                    }
                }

                delay(
                    250L
                )

                val matchingErrors =
                    observedEvents
                        .filterIsInstance<
                            CarStatusEvent.Error
                        >()
                        .filter { event ->
                            CarRequestEventMatcher.matches(
                                event =
                                    event,
                                request =
                                    request
                            )
                        }

                assertEquals(
                    1,
                    matchingErrors.size
                )

                val timeoutError =
                    matchingErrors.single()

                assertTrue(
                    timeoutError.message.contains(
                        "outcome unknown",
                        ignoreCase =
                            true
                    )
                )

                assertTrue(
                    timeoutError.message.contains(
                        "may have executed",
                        ignoreCase =
                            true
                    )
                )

                assertEquals(
                    request.sessionGeneration,
                    timeoutError.sessionGeneration
                )

                assertEquals(
                    request.requestId,
                    timeoutError.requestId
                )

                assertEquals(
                    request.command,
                    timeoutError.command
                )

                assertTrue(
                    observedEvents.any { event ->
                        event is
                            CarStatusEvent.CommandSent &&
                            CarRequestEventMatcher.matches(
                                event =
                                    event,
                                request =
                                    request
                            )
                    }
                )

                assertFalse(
                    observedEvents.any { event ->
                        event is
                            CarStatusEvent.CommandResponse &&
                            CarRequestEventMatcher.matches(
                                event =
                                    event,
                                request =
                                    request
                            )
                    }
                )

                assertEquals(
                    timeoutError,
                    CarRequestEventMatcher.selectBest(
                        events =
                            observedEvents.toList(),
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
                collector.cancelAndJoin()
                transport.close()
            }
        }

    private class FlushTriggeredResponseConnection(
        private val responseCommand:
            String =
            "LOCK_DOORS",
        private val responseStatus:
            String =
            "ok",
        private val responseCopies:
            Int =
            1
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
                                    responseStatus
                                )
                                .toString() +
                                "\n"

                        repeat(
                            responseCopies
                        ) {
                            peerWriter.write(
                                response.toByteArray(
                                    StandardCharsets.UTF_8
                                )
                            )

                            peerWriter.flush()
                        }
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
