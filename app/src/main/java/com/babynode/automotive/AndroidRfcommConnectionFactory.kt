package com.babynode.automotive

import android.annotation.SuppressLint
import java.util.UUID

/**
 * Production RFCOMM connection factory.
 */
object AndroidRfcommConnectionFactory :
    RfcommConnectionFactory {

    @SuppressLint("MissingPermission")
    override fun create(
        device: BluetoothDeviceIdentity,
        serviceUuid: UUID
    ): RfcommConnection {
        val bluetoothDevice =
            device.bluetoothDevice
                ?: throw IllegalArgumentException(
                    "AndroidRfcommConnectionFactory requires an Android BluetoothDevice"
                )

        return AndroidRfcommConnection(
            bluetoothDevice.createRfcommSocketToServiceRecord(
                serviceUuid
            )
        )
    }
}
