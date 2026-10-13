package com.babynode.automotive

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val VALID_ADDRESS_A =
    "02:00:00:00:00:01"

private const val VALID_ADDRESS_B =
    "02:00:00:00:00:02"

@RunWith(AndroidJUnit4::class)
class CarCanBusBluetoothApprovalTest {

    @Before
    fun setUp() {
        BabyNodeApp
            .instance
            .clearApprovedBabyNodeCanAddress()
    }

    @After
    fun tearDown() {
        BabyNodeApp
            .instance
            .clearApprovedBabyNodeCanAddress()
    }

    @Test
    fun unsupportedBluetoothFailsClosed() =
        runBlocking {
            val provider =
                FakeBluetoothDeviceProvider(
                    isSupported =
                        false
                )

            val factory =
                RecordingRfcommConnectionFactory()

            val transport =
                CarCanBusBluetooth(
                    deviceProvider =
                        provider,
                    connectionFactory =
                        factory
                )

            val events =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    Dispatchers.IO,
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect {
                            events.add(
                                it
                            )
                        }
                }

            try {
                transport.connect()

                val error =
                    awaitEvent(
                        events
                    ) {
                        it is CarStatusEvent.Error
                    } as CarStatusEvent.Error

                assertEquals(
                    "Bluetooth is not supported",
                    error.message
                )

                assertTrue(
                    factory
                        .createdDevices
                        .isEmpty()
                )

                assertEquals(
                    0,
                    provider.cancelDiscoveryCallCount
                )
            } finally {
                collector.cancelAndJoin()
                transport.close()
            }
        }

    @Test
    fun disabledBluetoothFailsClosed() =
        runBlocking {
            val provider =
                FakeBluetoothDeviceProvider(
                    isSupported =
                        true,
                    isEnabled =
                        false
                )

            val factory =
                RecordingRfcommConnectionFactory()

            val transport =
                CarCanBusBluetooth(
                    deviceProvider =
                        provider,
                    connectionFactory =
                        factory
                )

            val events =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    Dispatchers.IO,
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect {
                            events.add(
                                it
                            )
                        }
                }

            try {
                transport.connect()

                val error =
                    awaitEvent(
                        events
                    ) {
                        it is CarStatusEvent.Error
                    } as CarStatusEvent.Error

                assertEquals(
                    "Bluetooth is disabled",
                    error.message
                )

                assertTrue(
                    factory
                        .createdDevices
                        .isEmpty()
                )

                assertEquals(
                    0,
                    provider.cancelDiscoveryCallCount
                )
            } finally {
                collector.cancelAndJoin()
                transport.close()
            }
        }

    @Test
    fun zeroMatchingCandidatesFailsClosed() =
        runBlocking {
            val provider =
                FakeBluetoothDeviceProvider(
                    bondedDevices =
                        setOf(
                            BluetoothDeviceIdentity(
                                name =
                                    "OtherDevice",
                                address =
                                    VALID_ADDRESS_A
                            )
                        )
                )

            val factory =
                RecordingRfcommConnectionFactory()

            val transport =
                CarCanBusBluetooth(
                    deviceProvider =
                        provider,
                    connectionFactory =
                        factory
                )

            val events =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    Dispatchers.IO,
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect {
                            events.add(
                                it
                            )
                        }
                }

            try {
                transport.connect()

                val error =
                    awaitEvent(
                        events
                    ) {
                        it is CarStatusEvent.Error
                    } as CarStatusEvent.Error

                assertEquals(
                    "BabyNodeCAN is not paired",
                    error.message
                )

                assertTrue(
                    factory
                        .createdDevices
                        .isEmpty()
                )

                assertEquals(
                    0,
                    provider.cancelDiscoveryCallCount
                )
            } finally {
                collector.cancelAndJoin()
                transport.close()
            }
        }

    @Test
    fun multipleMatchingCandidatesAreRejected() =
        runBlocking {
            val provider =
                FakeBluetoothDeviceProvider(
                    bondedDevices =
                        setOf(
                            BluetoothDeviceIdentity(
                                name =
                                    "BabyNodeCAN",
                                address =
                                    VALID_ADDRESS_A
                            ),
                            BluetoothDeviceIdentity(
                                name =
                                    "BabyNodeCAN",
                                address =
                                    VALID_ADDRESS_B
                            )
                        )
                )

            val factory =
                RecordingRfcommConnectionFactory()

            val transport =
                CarCanBusBluetooth(
                    deviceProvider =
                        provider,
                    connectionFactory =
                        factory
                )

            val events =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    Dispatchers.IO,
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect {
                            events.add(
                                it
                            )
                        }
                }

            try {
                transport.connect()

                val error =
                    awaitEvent(
                        events
                    ) {
                        it is CarStatusEvent.Error
                    } as CarStatusEvent.Error

                assertTrue(
                    error.message.contains(
                        "More than one paired device is named BabyNodeCAN"
                    )
                )

                assertTrue(
                    factory
                        .createdDevices
                        .isEmpty()
                )

                assertEquals(
                    0,
                    provider.cancelDiscoveryCallCount
                )
            } finally {
                collector.cancelAndJoin()
                transport.close()
            }
        }

    @Test
    fun uniqueUnapprovedCandidateEmitsDeviceApprovalRequiredAndDoesNotCallConnectionFactory() =
        runBlocking {
            val candidate =
                BluetoothDeviceIdentity(
                    name =
                        "BabyNodeCAN",
                    address =
                        VALID_ADDRESS_A
                )

            val provider =
                FakeBluetoothDeviceProvider(
                    bondedDevices =
                        setOf(
                            candidate
                        )
                )

            val factory =
                RecordingRfcommConnectionFactory()

            val transport =
                CarCanBusBluetooth(
                    deviceProvider =
                        provider,
                    connectionFactory =
                        factory
                )

            val events =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    Dispatchers.IO,
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect {
                            events.add(
                                it
                            )
                        }
                }

            try {
                transport.connect()

                val approval =
                    awaitEvent(
                        events
                    ) {
                        it is CarStatusEvent.DeviceApprovalRequired
                    } as CarStatusEvent.DeviceApprovalRequired

                assertEquals(
                    "BabyNodeCAN",
                    approval.deviceName
                )

                assertEquals(
                    VALID_ADDRESS_A,
                    approval.deviceAddress
                )

                assertTrue(
                    factory
                        .createdDevices
                        .isEmpty()
                )

                assertEquals(
                    0,
                    provider.cancelDiscoveryCallCount
                )
            } finally {
                collector.cancelAndJoin()
                transport.close()
            }
        }

    @Test
    fun afterApprovalReconnectSelectsPinnedDeviceAndFactoryReceivesOnlyPinnedIdentity() =
        runBlocking {
            val candidate =
                BluetoothDeviceIdentity(
                    name =
                        "BabyNodeCAN",
                    address =
                        VALID_ADDRESS_A
                )

            val provider =
                FakeBluetoothDeviceProvider(
                    bondedDevices =
                        setOf(
                            candidate
                        )
                )

            val factory =
                RecordingRfcommConnectionFactory()

            val transport =
                CarCanBusBluetooth(
                    deviceProvider =
                        provider,
                    connectionFactory =
                        factory
                )

            val events =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    Dispatchers.IO,
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect {
                            events.add(
                                it
                            )
                        }
                }

            try {
                // First connect attempt requires approval
                transport.connect()

                val approval =
                    awaitEvent(
                        events
                    ) {
                        it is CarStatusEvent.DeviceApprovalRequired
                    } as CarStatusEvent.DeviceApprovalRequired

                assertEquals(
                    VALID_ADDRESS_A,
                    approval.deviceAddress
                )

                assertTrue(
                    factory
                        .createdDevices
                        .isEmpty()
                )

                // Explicitly approve through BabyNodeApp
                assertTrue(
                    BabyNodeApp
                        .instance
                        .approveBabyNodeCanAddress(
                            VALID_ADDRESS_A
                        )
                )

                // Reconnect selects the pinned device
                transport.connect()

                val connected =
                    awaitEvent(
                        events
                    ) {
                        it is CarStatusEvent.Connected
                    } as CarStatusEvent.Connected

                assertEquals(
                    "BabyNodeCAN",
                    connected.transportName
                )

                assertEquals(
                    1,
                    factory.createdDevices.size
                )

                assertEquals(
                    VALID_ADDRESS_A,
                    factory.createdDevices.single().address
                )

                assertEquals(
                    "BabyNodeCAN",
                    factory.createdDevices.single().name
                )

                assertEquals(
                    1,
                    provider.cancelDiscoveryCallCount
                )
            } finally {
                collector.cancelAndJoin()
                transport.close()
            }
        }

    @Test
    fun disconnectFollowedByReconnectContinuesUsingPinnedIdentity() =
        runBlocking {
            val candidate =
                BluetoothDeviceIdentity(
                    name =
                        "BabyNodeCAN",
                    address =
                        VALID_ADDRESS_A
                )

            val provider =
                FakeBluetoothDeviceProvider(
                    bondedDevices =
                        setOf(
                            candidate
                        )
                )

            val factory =
                RecordingRfcommConnectionFactory()

            val transport =
                CarCanBusBluetooth(
                    deviceProvider =
                        provider,
                    connectionFactory =
                        factory
                )

            val events =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    Dispatchers.IO,
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect {
                            events.add(
                                it
                            )
                        }
                }

            try {
                assertTrue(
                    BabyNodeApp
                        .instance
                        .approveBabyNodeCanAddress(
                            VALID_ADDRESS_A
                        )
                )

                // First connect
                transport.connect()

                awaitEvent(
                    events
                ) {
                    it is CarStatusEvent.Connected
                }

                assertEquals(
                    1,
                    factory.createdDevices.size
                )

                assertEquals(
                    VALID_ADDRESS_A,
                    factory.createdDevices[0].address
                )

                // Disconnect
                transport.disconnect()

                awaitEvent(
                    events
                ) {
                    it is CarStatusEvent.Disconnected
                }

                // Reconnect
                transport.connect()

                awaitEvent(
                    events
                ) { event ->
                    event is CarStatusEvent.Connected &&
                        events.filterIsInstance<CarStatusEvent.Connected>().size == 2
                }

                assertEquals(
                    2,
                    factory.createdDevices.size
                )

                assertEquals(
                    VALID_ADDRESS_A,
                    factory.createdDevices[1].address
                )
            } finally {
                collector.cancelAndJoin()
                transport.close()
            }
        }

    @Test
    fun missingPinnedIdentityFailsClosed() =
        runBlocking {
            assertTrue(
                BabyNodeApp
                    .instance
                    .approveBabyNodeCanAddress(
                        VALID_ADDRESS_A
                    )
            )

            val provider =
                FakeBluetoothDeviceProvider(
                    bondedDevices =
                        setOf(
                            BluetoothDeviceIdentity(
                                name =
                                    "BabyNodeCAN",
                                address =
                                    VALID_ADDRESS_B
                            )
                        )
                )

            val factory =
                RecordingRfcommConnectionFactory()

            val transport =
                CarCanBusBluetooth(
                    deviceProvider =
                        provider,
                    connectionFactory =
                        factory
                )

            val events =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    Dispatchers.IO,
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect {
                            events.add(
                                it
                            )
                        }
                }

            try {
                transport.connect()

                val error =
                    awaitEvent(
                        events
                    ) {
                        it is CarStatusEvent.Error
                    } as CarStatusEvent.Error

                assertEquals(
                    "The approved BabyNodeCAN device is no longer paired",
                    error.message
                )

                assertTrue(
                    factory
                        .createdDevices
                        .isEmpty()
                )

                assertEquals(
                    0,
                    provider.cancelDiscoveryCallCount
                )
            } finally {
                collector.cancelAndJoin()
                transport.close()
            }
        }

    @Test
    fun changedNameAtPinnedAddressFailsClosed() =
        runBlocking {
            assertTrue(
                BabyNodeApp
                    .instance
                    .approveBabyNodeCanAddress(
                        VALID_ADDRESS_A
                    )
            )

            val provider =
                FakeBluetoothDeviceProvider(
                    bondedDevices =
                        setOf(
                            BluetoothDeviceIdentity(
                                name =
                                    "NotBabyNodeCAN",
                                address =
                                    VALID_ADDRESS_A
                            )
                        )
                )

            val factory =
                RecordingRfcommConnectionFactory()

            val transport =
                CarCanBusBluetooth(
                    deviceProvider =
                        provider,
                    connectionFactory =
                        factory
                )

            val events =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    Dispatchers.IO,
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect {
                            events.add(
                                it
                            )
                        }
                }

            try {
                transport.connect()

                val error =
                    awaitEvent(
                        events
                    ) {
                        it is CarStatusEvent.Error
                    } as CarStatusEvent.Error

                assertEquals(
                    "The approved Bluetooth device does not identify as BabyNodeCAN",
                    error.message
                )

                assertTrue(
                    factory
                        .createdDevices
                        .isEmpty()
                )

                assertEquals(
                    0,
                    provider.cancelDiscoveryCallCount
                )
            } finally {
                collector.cancelAndJoin()
                transport.close()
            }
        }

    @Test
    fun sameNameDeviceAtDifferentAddressCannotReplaceApprovedDevice() =
        runBlocking {
            assertTrue(
                BabyNodeApp
                    .instance
                    .approveBabyNodeCanAddress(
                        VALID_ADDRESS_A
                    )
            )

            val provider =
                FakeBluetoothDeviceProvider(
                    bondedDevices =
                        setOf(
                            BluetoothDeviceIdentity(
                                name =
                                    "BabyNodeCAN",
                                address =
                                    VALID_ADDRESS_B
                            )
                        )
                )

            val factory =
                RecordingRfcommConnectionFactory()

            val transport =
                CarCanBusBluetooth(
                    deviceProvider =
                        provider,
                    connectionFactory =
                        factory
                )

            val events =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    Dispatchers.IO,
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect {
                            events.add(
                                it
                            )
                        }
                }

            try {
                transport.connect()

                val error =
                    awaitEvent(
                        events
                    ) {
                        it is CarStatusEvent.Error
                    } as CarStatusEvent.Error

                assertEquals(
                    "The approved BabyNodeCAN device is no longer paired",
                    error.message
                )

                assertFalse(
                    events.any {
                        it is CarStatusEvent.DeviceApprovalRequired
                    }
                )

                assertFalse(
                    events.any {
                        it is CarStatusEvent.Connected
                    }
                )

                assertTrue(
                    factory
                        .createdDevices
                        .isEmpty()
                )

                assertEquals(
                    0,
                    provider.cancelDiscoveryCallCount
                )
            } finally {
                collector.cancelAndJoin()
                transport.close()
            }
        }

    @Test
    fun pinnedAddressSelectedEvenWhenOtherCandidatesExist() =
        runBlocking {
            assertTrue(
                BabyNodeApp
                    .instance
                    .approveBabyNodeCanAddress(
                        VALID_ADDRESS_A
                    )
            )

            val provider =
                FakeBluetoothDeviceProvider(
                    bondedDevices =
                        setOf(
                            BluetoothDeviceIdentity(
                                name =
                                    "BabyNodeCAN",
                                address =
                                    VALID_ADDRESS_A
                            ),
                            BluetoothDeviceIdentity(
                                name =
                                    "BabyNodeCAN",
                                address =
                                    VALID_ADDRESS_B
                            )
                        )
                )

            val factory =
                RecordingRfcommConnectionFactory()

            val transport =
                CarCanBusBluetooth(
                    deviceProvider =
                        provider,
                    connectionFactory =
                        factory
                )

            val events =
                Collections.synchronizedList(
                    mutableListOf<CarStatusEvent>()
                )

            val collector =
                launch(
                    Dispatchers.IO,
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    transport
                        .status()
                        .collect {
                            events.add(
                                it
                            )
                        }
                }

            try {
                transport.connect()

                val connected =
                    awaitEvent(
                        events
                    ) {
                        it is CarStatusEvent.Connected
                    } as CarStatusEvent.Connected

                assertEquals(
                    "BabyNodeCAN",
                    connected.transportName
                )

                assertEquals(
                    1,
                    factory.createdDevices.size
                )

                assertEquals(
                    VALID_ADDRESS_A,
                    factory.createdDevices.single().address
                )

                assertEquals(
                    1,
                    provider.cancelDiscoveryCallCount
                )
            } finally {
                collector.cancelAndJoin()
                transport.close()
            }
        }

    private suspend fun awaitEvent(
        events: List<CarStatusEvent>,
        predicate: (CarStatusEvent) -> Boolean
    ): CarStatusEvent {
        withTimeout(
            5_000L
        ) {
            while (events.none(predicate)) {
                delay(
                    10L
                )
            }
        }

        return events.first(
            predicate
        )
    }

    private class FakeBluetoothDeviceProvider(
        override var isSupported: Boolean = true,
        override var isEnabled: Boolean = true,
        override var bondedDevices: Set<BluetoothDeviceIdentity> = emptySet()
    ) : BluetoothDeviceProvider {

        var cancelDiscoveryCallCount = 0

        override fun cancelDiscovery(): Boolean {
            cancelDiscoveryCallCount++
            return true
        }
    }

    private class FakeRfcommConnection :
        RfcommConnection {

        override var isConnected: Boolean = false
            private set

        private val peerWriter =
            PipedOutputStream()

        override val inputStream: InputStream =
            PipedInputStream(peerWriter)

        override val outputStream: OutputStream =
            ByteArrayOutputStream()

        override fun connect() {
            isConnected = true
        }

        override fun close() {
            isConnected = false
            runCatching { peerWriter.close() }
            runCatching { inputStream.close() }
            runCatching { outputStream.close() }
        }
    }

    private class RecordingRfcommConnectionFactory(
        private val connectionSupplier: () -> RfcommConnection = { FakeRfcommConnection() }
    ) : RfcommConnectionFactory {

        val createdDevices =
            Collections.synchronizedList(
                mutableListOf<BluetoothDeviceIdentity>()
            )

        val createdUuids =
            Collections.synchronizedList(
                mutableListOf<UUID>()
            )

        val createdSockets =
            Collections.synchronizedList(
                mutableListOf<RfcommConnection>()
            )

        override fun create(
            device: BluetoothDeviceIdentity,
            serviceUuid: UUID
        ): RfcommConnection {
            createdDevices.add(device)
            createdUuids.add(serviceUuid)
            val socket = connectionSupplier()
            createdSockets.add(socket)
            return socket
        }
    }
}
