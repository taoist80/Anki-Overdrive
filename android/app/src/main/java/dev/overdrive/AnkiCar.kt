package dev.overdrive

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ConnState { Disconnected, Scanning, Connecting, Connected }

data class FoundDevice(val name: String, val address: String, val rssi: Int, val device: BluetoothDevice)

data class Telemetry(
    val log: String = "",
    val position: Protocol.Position? = null,
    val version: Int? = null,
    val battery: Int? = null,
)

/**
 * Minimal BLE controller for one Anki car: scan -> connect -> enable notifications ->
 * SDK mode -> drive. State is exposed as Compose state for the UI to observe.
 * Permissions (BLUETOOTH_SCAN/CONNECT) are requested by the Activity before use.
 */
@SuppressLint("MissingPermission")
class AnkiCar(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scanner get() = adapter?.bluetoothLeScanner

    var state by mutableStateOf(ConnState.Disconnected); private set
    var telemetry by mutableStateOf(Telemetry()); private set
    val devices = mutableStateListOf<FoundDevice>()

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val seen = HashSet<String>()

    private fun log(s: String) {
        android.util.Log.i("OverdriveX", s)
        main.post { telemetry = telemetry.copy(log = (s + "\n" + telemetry.log).take(4000)) }
    }

    fun startScan() {
        val s = scanner ?: run { log("No BLE scanner — is Bluetooth on?"); return }
        devices.clear(); seen.clear()
        state = ConnState.Scanning
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        s.startScan(null, settings, scanCallback)   // unfiltered — some cars omit the service UUID from the ADV packet
        log("Scanning for Anki cars…")
        main.postDelayed({ stopScan() }, 8000)
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
        if (state == ConnState.Scanning) state = ConnState.Disconnected
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device
            val rec = result.scanRecord
            val hasAnki = rec?.serviceUuids?.any { it.uuid == Protocol.SERVICE } == true
            val name = dev.name ?: rec?.deviceName
            main.post {
                if (seen.add(dev.address)) log("seen ${name ?: "?"} ${dev.address} ${result.rssi}dBm anki=$hasAnki")
                val looksAnki = hasAnki ||
                    name?.contains("Drive", true) == true || name?.contains("Anki", true) == true
                if (looksAnki && devices.none { it.address == dev.address }) {
                    devices.add(FoundDevice(name ?: "Anki car", dev.address, result.rssi, dev))
                    log("→ car: ${name ?: "Anki car"} ${dev.address} (${result.rssi} dBm)")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) { log("Scan failed: $errorCode") }
    }

    fun connect(d: FoundDevice) {
        stopScan()
        state = ConnState.Connecting
        log("Connecting to ${d.address}…")
        gatt = d.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        send(Protocol.disconnect())
        gatt?.disconnect()
    }

    fun setSpeed(mm: Int) = send(Protocol.setSpeed(mm))
    fun changeLane(offsetMm: Float) = send(Protocol.changeLane(offsetMm))
    fun stop() = send(Protocol.setSpeed(0))

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> { log("Connected; discovering services…"); g.discoverServices() }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("Disconnected (status=$status)")
                    g.close(); gatt = null; writeChar = null
                    main.post { state = ConnState.Disconnected }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(Protocol.SERVICE) ?: run { log("Anki GATT service not found"); return }
            writeChar = svc.getCharacteristic(Protocol.WRITE_CHAR)
            val notify = svc.getCharacteristic(Protocol.NOTIFY_CHAR)
            if (writeChar == null || notify == null) { log("Expected characteristics missing"); return }
            g.setCharacteristicNotification(notify, true)
            val cccd = notify.getDescriptor(Protocol.CCCD)
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION") run {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(cccd)
                }
            }
            log("Enabling notifications…")
        }

        override fun onDescriptorWrite(g: BluetoothGatt, desc: BluetoothGattDescriptor, status: Int) {
            main.post { state = ConnState.Connected }
            log("SDK mode ON — ready to drive")
            send(Protocol.sdkMode(true))
            send(Protocol.setOffset(0f))
            send(Protocol.version())
            send(Protocol.battery())
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) =
            handleNotify(value)

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") handleNotify(ch.value ?: return)
        }
    }

    private fun handleNotify(d: ByteArray) {
        when (Protocol.msgId(d)) {
            Protocol.MSG_LOCALIZATION_POSITION ->
                Protocol.parsePosition(d)?.let { p -> main.post { telemetry = telemetry.copy(position = p) } }
            Protocol.MSG_VERSION_RESPONSE ->
                main.post { telemetry = telemetry.copy(version = Protocol.u16le(d, 2)) }
            Protocol.MSG_BATTERY_RESPONSE ->
                main.post { telemetry = telemetry.copy(battery = Protocol.u16le(d, 2)) }
        }
    }

    private fun send(bytes: ByteArray) {
        val g = gatt ?: return
        val ch = writeChar ?: return
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION") run {
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                ch.value = bytes
                g.writeCharacteristic(ch)
            }
        }
    }
}
