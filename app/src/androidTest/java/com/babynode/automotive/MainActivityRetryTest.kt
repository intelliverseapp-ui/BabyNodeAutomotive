package com.babynode.automotive

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityRetryTest {

    @Before
    fun setUp() {
        MainActivity.resetTransportFactoryForTest()
        MainActivity.resetRetryConfigurationForTest()

        MainActivity.maximumReconnectAttempts =
            2

        MainActivity.reconnectDelaysMillis =
            longArrayOf(
                0L,
                0L
            )

        MainActivity.reconnectObservationDelayMillis =
            0L
    }

    @After
    fun tearDown() {
        MainActivity.resetTransportFactoryForTest()
        MainActivity.resetRetryConfigurationForTest()
    }

    @Test
    fun retriesStopAtLimitAndManualRetryStartsFreshAttempt() {
        val transport =
            RetryRecordingTransport(
                connectOnAttempt =
                    4
            )

        MainActivity.installTransportFactoryForTest {
            transport
        }

        val scenario =
            ActivityScenario.launch(
                MainActivity::class.java
            )

        try {
            waitForCondition {
                var exhausted =
                    false

                scenario.onActivity { activity ->
                    exhausted =
                        activity.reconnectAttemptForTest() ==
                            2 &&
                            activity.connectionStateForTest() is
                            CarConnectionState.Failed
                }

                exhausted
            }

            assertEquals(
                3,
                transport.connectCount.get()
            )

            scenario.onActivity { activity ->
                activity.manualRetryForTest()
            }

            waitForCondition {
                var connected =
                    false

                scenario.onActivity { activity ->
                    connected =
                        activity.connectionStateForTest() is
                            CarConnectionState.Connected
                }

                connected
            }

            assertEquals(
                4,
                transport.connectCount.get()
            )

            scenario.onActivity { activity ->
                assertEquals(
                    0,
                    activity.reconnectAttemptForTest()
                )

                assertTrue(
                    activity.connectionStateForTest() is
                        CarConnectionState.Connected
                )
            }
        } finally {
            scenario.close()
        }
    }

    private fun waitForCondition(
        condition: () -> Boolean
    ) {
        val deadline =
            SystemClock.uptimeMillis() +
                5_000L

        while (
            SystemClock.uptimeMillis() <
            deadline
        ) {
            if (condition()) {
                return
            }

            SystemClock.sleep(
                20L
            )
        }

        assertTrue(
            "Timed out waiting for retry state",
            condition()
        )
    }

    private class RetryRecordingTransport(
        private val connectOnAttempt:
            Int
    ) : CarCanTransport {

        val connectCount =
            AtomicInteger(
                0
            )

        private val events =
            MutableSharedFlow<CarStatusEvent>(
                extraBufferCapacity =
                    8
            )

        override suspend fun connect() {
            val attempt =
                connectCount.incrementAndGet()

            if (
                attempt ==
                connectOnAttempt
            ) {
                events.emit(
                    CarStatusEvent.Connected(
                        transportName =
                            "Retry Test"
                    )
                )
            }
        }

        override suspend fun disconnect() {
        }

        override suspend fun sendCommand(
            command: CanonicalCommand
        ): CarCommandRequest {
            error(
                "Retry test does not send commands"
            )
        }

        override fun status():
            Flow<CarStatusEvent> {
            return events
        }

        override fun close():
            List<CarStatusEvent> {
            return emptyList()
        }
    }
}
