package com.babynode.automotive

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityExternalIntentTest {

    @Before
    fun setUp() {
        MainActivity.resetTransportFactoryForTest()
        MainActivity.resetRetryConfigurationForTest()
    }

    @After
    fun tearDown() {
        MainActivity.resetTransportFactoryForTest()
        MainActivity.resetRetryConfigurationForTest()
    }

    @Test
    fun externalCommandIntentsNeverDispatchVehicleCommands() {
        val transport =
            RecordingIntentTransport()

        MainActivity.installTransportFactoryForTest {
            transport
        }

        val scenario =
            ActivityScenario.launch(
                MainActivity::class.java
            )

        try {
            scenario.onActivity { activity ->
                activity.inspectExternalIntentForTest(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "bn://unlock_doors"
                        )
                    )
                )

                activity.inspectExternalIntentForTest(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "bn://lock_doors"
                        )
                    )
                )

                activity.inspectExternalIntentForTest(
                    Intent(
                        Intent.ACTION_ASSIST
                    ).putExtra(
                        "command",
                        "unlock the doors"
                    )
                )

                activity.inspectExternalIntentForTest(
                    Intent(
                        Intent.ACTION_PROCESS_TEXT
                    ).putExtra(
                        Intent.EXTRA_PROCESS_TEXT,
                        "lock the doors"
                    )
                )
            }

            assertTrue(
                transport.sentCommands.isEmpty()
            )
        } finally {
            scenario.close()
        }

        assertTrue(
            transport.sentCommands.isEmpty()
        )
    }

    private class RecordingIntentTransport :
        CarCanTransport {

        val sentCommands =
            CopyOnWriteArrayList<CanonicalCommand>()

        override suspend fun connect() {
        }

        override suspend fun disconnect() {
        }

        override suspend fun sendCommand(
            command: CanonicalCommand
        ): CarCommandRequest {
            sentCommands +=
                command

            return CarCommandRequest(
                requestId =
                    sentCommands.size.toLong(),
                command =
                    command
            )
        }

        override fun status():
            Flow<CarStatusEvent> {
            return emptyFlow()
        }

        override fun close():
            List<CarStatusEvent> {
            return emptyList()
        }
    }
}
