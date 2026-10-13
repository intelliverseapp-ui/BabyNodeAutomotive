package com.babynode.automotive

import java.io.InputStream
import java.io.OutputStream

/**
 * Narrow connected-channel seam used by the Bluetooth transport.
 *
 * Production wraps Android BluetoothSocket. Tests can provide
 * deterministic streams without Android Bluetooth hardware.
 */
interface RfcommConnection {

    val isConnected:
        Boolean

    val inputStream:
        InputStream

    val outputStream:
        OutputStream

    fun connect()

    fun close()
}
