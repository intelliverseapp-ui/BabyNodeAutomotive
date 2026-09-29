package com.babynode.automotive

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.driver.ProbeTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CarCanBusUsb(
    private val context: Context,
    private val scope: CoroutineScope
) : CarCanTransport {

    private val TAG = "CarCanBusUsb"
    private val ACTION_USB_PERMISSION = "com.babynode.automotive.USB_PERMISSION"

    private val statusFlow = MutableSharedFlow<CarStatusEvent>(extraBufferCapacity = 64)

    private var port: UsbSerialPort? = null
    private var usbManager: UsbManager? = null
    private var driver: UsbSerialDriver? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return

            val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

            if (device == null) {
                Log.e(TAG, "USB permission result: device null")
                return
            }

            if (granted) {
                Log.i(TAG, "USB permission granted for ${device.deviceName}")
                scope.launch { openSerialPort() }
            } else {
                Log.e(TAG, "USB permission denied for ${device.deviceName}")
                scope.launch {
                    statusFlow.emit(CarStatusEvent.Error("USB permission denied"))
                }
            }
        }
    }

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            try {
                usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

                // Enumerate devices
                val deviceList = usbManager!!.deviceList
                if (deviceList.isEmpty()) {
                    Log.e(TAG, "No USB devices detected by Android")
                } else {
                    Log.i(TAG, "USB devices detected:")
                    for ((_, dev) in deviceList) {
                        Log.i(TAG, "Device: ${dev.deviceName}")
                        Log.i(TAG, "  Vendor ID: ${dev.vendorId}")
                        Log.i(TAG, "  Product ID: ${dev.productId}")
                        Log.i(TAG, "  Interfaces: ${dev.interfaceCount}")
                        for (i in 0 until dev.interfaceCount) {
                            val intf = dev.getInterface(i)
                            Log.i(TAG, "    Interface $i: endpoints=${intf.endpointCount}")
                        }
                    }
                }

                // ⭐ CUSTOM PROBE TABLE FOR ESP32 CDC/ACM
                val customTable = ProbeTable().apply {
                    addProduct(0x303A, 0x1001, CdcAcmSerialDriver::class.java) // ESP32-S3
                    addProduct(0x303A, 0x0002, CdcAcmSerialDriver::class.java) // ESP32 generic CDC
                }

                val prober = UsbSerialProber(customTable)
                val availableDrivers: List<UsbSerialDriver> =
                    prober.findAllDrivers(usbManager)

                if (availableDrivers.isEmpty()) {
                    Log.e(TAG, "Custom probe table found NO serial drivers")
                    scope.launch {
                        statusFlow.emit(CarStatusEvent.Error("No USB serial drivers found"))
                    }
                    return@withContext
                }

                driver = availableDrivers[0]
                val device = driver!!.device

                val permissionIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_IMMUTABLE
                )

                val filter = IntentFilter(ACTION_USB_PERMISSION)
                context.registerReceiver(usbReceiver, filter)

                if (!usbManager!!.hasPermission(device)) {
                    Log.i(TAG, "Requesting USB permission for ${device.deviceName}")
                    usbManager!!.requestPermission(device, permissionIntent)
                } else {
                    Log.i(TAG, "Already have USB permission for ${device.deviceName}")
                    openSerialPort()
                }

            } catch (e: Exception) {
                Log.e(TAG, "USB connect failed: ${e.message}", e)
                scope.launch {
                    statusFlow.emit(CarStatusEvent.Error("USB connect failed: ${e.message}", e))
                }
            }
        }
    }

    private suspend fun openSerialPort() {
        withContext(Dispatchers.IO) {
            try {
                val d = driver ?: return@withContext
                val connection = usbManager!!.openDevice(d.device)

                if (connection == null) {
                    Log.e(TAG, "Unable to open USB device")
                    scope.launch {
                        statusFlow.emit(CarStatusEvent.Error("Unable to open USB device"))
                    }
                    return@withContext
                }

                port = d.ports[0]
                port!!.open(connection)
                port!!.setParameters(
                    115200,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )

                Log.i(TAG, "USB serial port initialized for DuoCAN")

                scope.launch {
                    statusFlow.emit(CarStatusEvent.Connected("USB"))
                }

                startReadLoop()

            } catch (e: Exception) {
                Log.e(TAG, "USB serial open failed: ${e.message}", e)
                scope.launch {
                    statusFlow.emit(CarStatusEvent.Error("USB serial open failed: ${e.message}", e))
                }
            }
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                port?.close()
                port = null

                try {
                    context.unregisterReceiver(usbReceiver)
                } catch (_: Exception) {}

                scope.launch {
                    statusFlow.emit(CarStatusEvent.Disconnected("USB"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "USB disconnect failed: ${e.message}", e)
                scope.launch {
                    statusFlow.emit(CarStatusEvent.Error("USB disconnect failed: ${e.message}", e))
                }
            }
        }
    }

    override suspend fun sendFrame(frame: CarCanFrame) {
        withContext(Dispatchers.IO) {
            try {
                val p = port
                if (p == null) {
                    Log.e(TAG, "USB port not initialized")
                    scope.launch {
                        statusFlow.emit(CarStatusEvent.Error("USB port not initialized"))
                    }
                    return@withContext
                }

                val packet = buildPacket(frame)
                p.write(packet, 1000)

                Log.i(TAG, "USB packet sent: ${packet.joinToString(" ") { "0x%02X".format(it) }}")

                scope.launch {
                    statusFlow.emit(CarStatusEvent.FrameSent(frame))
                }

            } catch (e: Exception) {
                Log.e(TAG, "USB send failed: ${e.message}", e)
                scope.launch {
                    statusFlow.emit(CarStatusEvent.Error("USB send failed: ${e.message}", e))
                }
            }
        }
    }

    override fun status(): Flow<CarStatusEvent> = statusFlow

    private fun buildPacket(frame: CarCanFrame): ByteArray {
        val idHigh = (frame.id shr 8) and 0xFF
        val idLow = frame.id and 0xFF
        val len = frame.data.size

        return byteArrayOf(
            0xAA.toByte(),
            idHigh.toByte(),
            idLow.toByte(),
            len.toByte()
        ) + frame.data
    }

    private fun startReadLoop() {
        scope.launch(Dispatchers.IO) {
            val p = port ?: return@launch

            val buffer = ByteArray(64)

            while (true) {
                try {
                    val len = p.read(buffer, 100)
                    if (len > 0) {
                        val bytes = buffer.copyOf(len)
                        Log.i(TAG, "USB RX: ${bytes.joinToString(" ") { "0x%02X".format(it) }}")

                        scope.launch {
                            statusFlow.emit(
                                CarStatusEvent.FrameReceived(
                                    CarCanFrame(0xFFFF, bytes)
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "USB read failed: ${e.message}", e)
                    scope.launch {
                        statusFlow.emit(CarStatusEvent.Error("USB read failed: ${e.message}", e))
                    }
                    break
                }
            }
        }
    }
}
