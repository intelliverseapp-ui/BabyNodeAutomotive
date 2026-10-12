package com.babynode.automotive

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import java.util.UUID

/**
 * Production RFCOMM connection factory.
 */
object AndroidRfcommConnectionFactory :
    RfcommConnectionFactory {

    @SuppressLint("MissingPermission")
    override fun create(
        device: BluetoothDevice,
        serviceUuid: UUID
    ): RfcommConnection {
        return AndroidRfcommConnection(
            device.createRfcommSocketToServiceRecord(
                serviceUuid
            )
        )
    }
}
