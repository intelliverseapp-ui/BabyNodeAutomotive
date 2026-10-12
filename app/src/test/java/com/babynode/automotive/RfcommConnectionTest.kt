package com.babynode.automotive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RfcommConnectionTest {

    @Test
    fun deterministicConnectionExposesControllableStreams() {
        val input =
            ByteArrayInputStream(
                "response\n".toByteArray()
            )

        val output =
            ByteArrayOutputStream()

        val connection =
            FakeRfcommConnection(
                inputStream =
                    input,
                outputStream =
                    output
            )

        assertFalse(
            connection.isConnected
        )

        connection.connect()

        assertTrue(
            connection.isConnected
        )

        assertSame(
            input,
            connection.inputStream
        )

        assertSame(
            output,
            connection.outputStream
        )

        connection.close()

        assertFalse(
            connection.isConnected
        )
    }

    private class FakeRfcommConnection(
        override val inputStream:
            ByteArrayInputStream,
        override val outputStream:
            ByteArrayOutputStream
    ) : RfcommConnection {

        override var isConnected =
            false
            private set

        override fun connect() {
            isConnected =
                true
        }

        override fun close() {
            isConnected =
                false
        }
    }
}
