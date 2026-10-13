package com.babynode.automotive

import java.util.UUID

/**
 * Creates one RFCOMM connection for an approved Bluetooth device.
 */
fun interface RfcommConnectionFactory {

    fun create(
        device: BluetoothDeviceIdentity,
        serviceUuid: UUID
    ): RfcommConnection
}
