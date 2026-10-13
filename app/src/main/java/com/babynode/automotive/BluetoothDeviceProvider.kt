package com.babynode.automotive

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice

/**
 * Identity of a bonded Bluetooth device candidate.
 */
data class BluetoothDeviceIdentity(
    val name: String?,
    val address: String,
    val bluetoothDevice: BluetoothDevice? = null
)

/**
 * Provides access to the platform Bluetooth adapter state and bonded devices.
 */
interface BluetoothDeviceProvider {

    /**
     * Whether a Bluetooth adapter is available on this system.
     */
    val isSupported: Boolean

    /**
     * Whether the Bluetooth adapter is currently enabled.
     */
    val isEnabled: Boolean

    /**
     * Set of currently bonded Bluetooth device identities.
     */
    val bondedDevices: Set<BluetoothDeviceIdentity>

    /**
     * Cancels any ongoing Bluetooth discovery.
     */
    fun cancelDiscovery(): Boolean
}

/**
 * Production Bluetooth adapter and device provider backed by Android Bluetooth.
 */
object AndroidBluetoothDeviceProvider :
    BluetoothDeviceProvider {

    private val adapter: BluetoothAdapter?
        get() = BluetoothAdapter.getDefaultAdapter()

    override val isSupported: Boolean
        get() = adapter != null

    @get:SuppressLint("MissingPermission")
    override val isEnabled: Boolean
        get() = adapter?.isEnabled == true

    @get:SuppressLint("MissingPermission")
    override val bondedDevices: Set<BluetoothDeviceIdentity>
        get() {
            val devices =
                adapter?.bondedDevices ?: return emptySet()

            return devices.map { device ->
                BluetoothDeviceIdentity(
                    name =
                        device.name,
                    address =
                        device.address,
                    bluetoothDevice =
                        device
                )
            }.toSet()
        }

    @SuppressLint("MissingPermission")
    override fun cancelDiscovery(): Boolean {
        return adapter?.cancelDiscovery() ?: false
    }
}
