package com.babynode.automotive

import android.bluetooth.BluetoothDevice
import java.util.UUID

/**
 * Creates one RFCOMM connection for an approved Bluetooth device.
 */
fun interface RfcommConnectionFactory {

    fun create(
        device: BluetoothDevice,
        serviceUuid: UUID
    ): RfcommConnection
}
