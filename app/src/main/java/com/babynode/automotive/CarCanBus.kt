package com.babynode.automotive

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * CarCanBus
 *
 * ONE RESPONSIBILITY:
 * Send CAN frames to the ESP32-S3 CAN controller over USB serial.
 *
 * Protocol (simple, extensible):
 *   [0xAA] [FRAME_ID_HIGH] [FRAME_ID_LOW] [DATA_LEN] [DATA...]
 *
 * ESP32-S3 receives this over USB serial and transmits the CAN frame.
 */
object CarCanBus {

    private const val TAG = "CarCanBus"

    private var port: UsbSerialPort? = null

    // ⭐ Listener for CAN debug UI
    private var listener: ((CANFrame) -> Unit)? = null

    // ⭐ Simple CAN frame model
    data class CANFrame(
        val id: Int,
        val data: ByteArray
    )

    fun setListener(callback: (CANFrame) -> Unit) {
        listener = callback
    }

    fun initialize(context: Context) {
        try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

            // Probe for USB serial devices
            val availableDrivers: List<UsbSerialDriver> =
                UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            if (availableDrivers.isEmpty()) {
                Log.e(TAG, "No USB serial drivers found — simulation mode")
                return
            }

            val driver = availableDrivers[0]
            val connection = usbManager.openDevice(driver.device)

            if (connection == null) {
                Log.e(TAG, "Permission denied or unable to open USB device")
                return
            }

            port = driver.ports[0]
            port?.open(connection)
            port?.setParameters(
                115200,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )

            Log.i(TAG, "USB serial port initialized for CAN bus")

        } catch (e: Exception) {
            Log.e(TAG, "CAN initialization failed: ${e.message}", e)
        }
    }

    fun send(frameId: Int, data: ByteArray) {
        val p = port

        // ⭐ Notify debug UI even in simulation mode
        listener?.invoke(CANFrame(frameId, data))

        if (p == null) {
            Log.w(TAG, "CAN bus not initialized — simulation only")
            return
        }

        try {
            val packet = buildPacket(frameId, data)
            p.write(packet, 1000)
            Log.i(TAG, "CAN packet sent: ${packet.joinToString(" ") { "0x%02X".format(it) }}")
        } catch (e: Exception) {
            Log.e(TAG, "CAN send failed: ${e.message}", e)
        }
    }

    private fun buildPacket(frameId: Int, data: ByteArray): ByteArray {
        val header = byteArrayOf(
            0xAA.toByte(),
            ((frameId shr 8) and 0xFF).toByte(),
            (frameId and 0xFF).toByte(),
            data.size.toByte()
        )
        return header + data
    }
}
