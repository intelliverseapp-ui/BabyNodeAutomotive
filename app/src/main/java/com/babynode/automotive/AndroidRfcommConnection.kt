package com.babynode.automotive

import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream

/**
 * Production RFCOMM connection backed by Android BluetoothSocket.
 */
class AndroidRfcommConnection(
    private val socket: BluetoothSocket
) : RfcommConnection {

    override val isConnected:
        Boolean
        get() =
            socket.isConnected

    override val inputStream:
        InputStream
        get() =
            socket.inputStream

    override val outputStream:
        OutputStream
        get() =
            socket.outputStream

    override fun connect() {
        socket.connect()
    }

    override fun close() {
        socket.close()
    }
}
