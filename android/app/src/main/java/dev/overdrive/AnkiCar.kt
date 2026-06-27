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
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class CarState { Disconnected, Connecting, Connected }

/** Connection priority = Android's lever over the connection interval (≈ the game's 30 ms is BALANCED). */
enum class Prio(val v: Int, val label: String) {
    HIGH(BluetoothGatt.CONNECTION_PRIORITY_HIGH, "HIGH ~11ms"),
    BALANCED(BluetoothGatt.CONNECTION_PRIORITY_BALANCED, "BAL ~30ms"),
    LOW(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER, "LOW ~100ms+"),
}

data class FoundDevice(
    val name: String, val address: String, val rssi: Int, val device: BluetoothDevice,
    val modelId: Int = -1,
)

data class CarInfo(
    val address: String, val name: String, val state: CarState,
    val lastStatus: Int = -1, val uptimeS: Long = 0, val drops: Int = 0,
    val modelId: Int = -1,
)

/** Decodes the BluetoothGattCallback status (= the HCI disconnect reason surfaced to the app). */
fun statusName(s: Int): String = when (s) {
    0 -> "OK"
    8 -> "0x08 SUPERVISION-TIMEOUT (link lost)"
    19 -> "0x13 remote terminated"
    22 -> "0x16 local terminated"
    34 -> "0x22 LMP timeout"
    62 -> "0x3E fail-to-establish"
    133 -> "0x85 GATT_ERROR"
    else -> "status=$s"
}

@SuppressLint("MissingPermission")
class CarManager(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scanner get() = adapter?.bluetoothLeScanner
    val maxCars = 4

    var scanning by mutableStateOf(false); private set
    var priority by mutableStateOf(Prio.BALANCED); private set
    var stress by mutableStateOf(false); private set
    var log by mutableStateOf(""); private set
    val found = mutableStateListOf<FoundDevice>()
    val cars = mutableStateListOf<CarInfo>()

    private val conns = LinkedHashMap<String, CarConn>()
    private val seen = HashSet<String>()

    /** Telemetry hooks for the RaceEngine. Invoked on the main thread. */
    var onPosition: ((address: String, pos: Protocol.Position) -> Unit)? = null
    var onTransition: ((address: String) -> Unit)? = null
    var onDelocalized: ((address: String) -> Unit)? = null

    init {
        // Periodic tick: refresh per-car uptime AND run 2.6's desired-state reconnect loop. 2.6 keeps a
        // persistent "desired connection state" per car and an always-on advertisement scan, so a dropped
        // car re-links the instant it re-advertises (VehicleServices::SetDesiredConnectionState +
        // StartBroadcastingAdvertisementUpdates). We mirror that: while any car the user connected is down
        // and we aren't already scanning, keep hunting for it — the loop never gives up while it's wanted.
        main.post(object : Runnable {
            override fun run() {
                if (conns.isNotEmpty()) {
                    refresh()
                    if (!scanning && conns.values.any { it.info().state == CarState.Disconnected }) startScan()
                }
                main.postDelayed(this, 1000)
            }
        })
    }

    private fun log(s: String) {
        android.util.Log.i("OverdriveX", s)
        main.post { log = (s + "\n" + log).take(8000) }
    }

    private fun refresh() = main.post {
        cars.clear(); conns.values.forEach { cars.add(it.info()) }
    }

    fun startScan() {
        val s = scanner ?: run { log("No BLE scanner (Bluetooth off?)"); return }
        found.clear(); seen.clear(); scanning = true
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        s.startScan(null, settings, scanCb)
        log("Scanning…")
        main.postDelayed({ stopScan() }, 8000)
    }
    fun stopScan() { scanner?.stopScan(scanCb); scanning = false }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(t: Int, r: ScanResult) {
            val d = r.device; val rec = r.scanRecord
            val anki = rec?.serviceUuids?.any { it.uuid == Protocol.SERVICE } == true
            val nm = d.name ?: rec?.deviceName
            // Anki cars broadcast their model in the manufacturer data: [product_id, model_id, ...].
            var modelId = -1
            var rawMfg = ""
            val mfg = rec?.manufacturerSpecificData
            if (mfg != null) {
                for (i in 0 until mfg.size()) {
                    val bytes = mfg.valueAt(i) ?: continue
                    rawMfg += "[cid=${mfg.keyAt(i)} ${bytes.joinToString(" ") { "%02x".format(it) }}]"
                    if (modelId < 0) modelId = Protocol.modelIdFromMfg(bytes)
                }
            }
            main.post {
                if (seen.add(d.address)) log("seen ${nm ?: "?"} ${d.address} ${r.rssi}dBm anki=$anki model=$modelId mfg=$rawMfg")
                val looks = anki || nm?.contains("Drive", true) == true || nm?.contains("Anki", true) == true
                if (!looks) return@post
                val existing = conns[d.address]
                if (existing != null) {
                    // A car we already manage is advertising again. If it had dropped, it's now reachable —
                    // reconnect directly (a direct connect to a live advertiser is fast/reliable, unlike a
                    // stale autoConnect). This is 2.6's "reconnect on advertisement rediscovery".
                    if (existing.info().state == CarState.Disconnected) { log("[$nm] rediscovered — reconnecting"); existing.reconnectNow() }
                } else if (found.none { it.address == d.address }) {
                    found.add(FoundDevice(nm ?: "Anki car", d.address, r.rssi, d, modelId))
                }
            }
        }
        override fun onScanFailed(e: Int) = log("Scan failed: $e")
    }

    fun connect(d: FoundDevice) {
        if (conns.size >= maxCars) { log("Max $maxCars cars connected"); return }
        stopScan()
        found.removeAll { it.address == d.address }
        conns[d.address] = CarConn(d.device, d.name, d.modelId).also { it.connect() }
        refresh()
    }

    fun disconnectAll() { conns.values.forEach { it.shutdown() }; conns.clear(); refresh(); log("--- disconnect all ---") }
    fun changePriority(p: Prio) { priority = p; conns.values.forEach { it.applyPriority(p) }; log("set priority -> ${p.label}") }
    fun changeStress(on: Boolean) { stress = on; conns.values.forEach { it.setStress(on) }; log("drive-all (load) -> $on") }

    // ---- Race control (used by RaceEngine; no-ops for unknown/disconnected cars) ----
    /** Currently-connected cars, in connection order. */
    fun connectedCars(): List<CarInfo> = conns.values.map { it.info() }.filter { it.state == CarState.Connected }

    /** Cars the user connected that are currently down (not Connected) — drives the HUD reconnect prompt. */
    fun droppedCars(): List<CarInfo> = conns.values.map { it.info() }.filter { it.state != CarState.Connected }

    /**
     * Manual force-reconnect — the UI "Reconnect" button. 2.6 itself had no such button: its native
     * desired-state loop never stopped trying and the player's only job was physical — reseat the car on
     * the charger so it re-advertises (the "Get To the Charger" phase). On Android a manual kick still
     * helps after a wedged GATT, so this tears down + re-arms every dropped car and starts an active rescan.
     */
    fun reconnectDropped() {
        val down = conns.values.filter { it.info().state != CarState.Connected }
        if (down.isEmpty()) { log("reconnect: all cars already connected"); return }
        log("force-reconnect: ${down.size} dropped car(s) + active rescan")
        down.forEach { it.forceReconnect() }
        if (!scanning) startScan()
    }

    /** Set a car's target speed (mm/s). The car follows the track in firmware at this speed. */
    fun drive(address: String, speedMmPerSec: Int, accelMmPerSec2: Int = 1000) =
        conns[address]?.driveCmd(speedMmPerSec, accelMmPerSec2) ?: Unit

    /** Change lane to an absolute horizontal offset (mm from track centerline). */
    fun changeLane(address: String, offsetMm: Float, hSpeed: Int = 300, hAccel: Int = 1000) =
        conns[address]?.laneCmd(offsetMm, hSpeed, hAccel) ?: Unit

    /** Set the car's internal notion of its current offset (baseline before changeLane). */
    fun setLaneOffset(address: String, offsetMm: Float) =
        conns[address]?.offsetCmd(offsetMm) ?: Unit

    /** Issue a 180° u-turn to realign a car that's driving the wrong way (against race direction). */
    fun uTurn(address: String) = conns[address]?.uTurnCmd() ?: Unit

    @SuppressLint("MissingPermission")
    inner class CarConn(val device: BluetoothDevice, val name: String, val modelId: Int = -1) {
        private var gatt: BluetoothGatt? = null
        private var writeChar: BluetoothGattCharacteristic? = null
        private var state = CarState.Connecting
        private var lastStatus = -1
        private var drops = 0
        private var connectedAt = 0L
        private var driveOn = false
        private var spd = 0

        fun info(): CarInfo {
            val up = if (state == CarState.Connected && connectedAt > 0)
                (SystemClock.elapsedRealtime() - connectedAt) / 1000 else 0
            return CarInfo(device.address, name, state, lastStatus, up, drops, modelId)
        }

        fun connect(reconnect: Boolean = false) {
            state = CarState.Connecting
            log("[$name] ${if (reconnect) "reconnecting (autoConnect)…" else "connecting…"}")
            // autoConnect=false is fast for the first connect, but after a supervision-timeout drop the
            // device isn't immediately connectable and a direct connect fails repeatedly (status 147).
            // autoConnect=true lets the OS re-establish whenever the car re-advertises (picked up / placed
            // back on the track), which is what actually makes a dropped car re-engage.
            gatt = device.connectGatt(context, reconnect, cb, BluetoothDevice.TRANSPORT_LE)
            // Watchdog: a reconnect that never completes must not wedge the desired-state loop. If we're
            // still Connecting after 10s, fail it back to Disconnected so the tick/rediscovery can retry.
            main.postDelayed({
                if (state == CarState.Connecting && conns.containsKey(device.address)) {
                    log("[$name] connect timed out — will retry"); teardownGatt()
                    state = CarState.Disconnected; refresh()
                }
            }, 10000)
        }
        private fun teardownGatt() { try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}; gatt = null }
        /** Reconnect on advertisement rediscovery: the car is live right now, so a direct connect is fastest. */
        fun reconnectNow() { if (state == CarState.Disconnected) { teardownGatt(); connect(reconnect = false) } }
        /** Manual force-reconnect (UI button): tear down any wedged link and re-arm with autoConnect. */
        fun forceReconnect() { if (state != CarState.Connected) { teardownGatt(); connect(reconnect = true) } }
        fun shutdown() { driveOn = false; try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}; gatt = null; state = CarState.Disconnected }
        fun applyPriority(p: Prio) { gatt?.requestConnectionPriority(p.v) }
        fun setStress(on: Boolean) { driveOn = on; if (on && state == CarState.Connected) main.post(driveLoop) }

        // Race control. Disable the lab oscillator if it was on, so the RaceEngine owns the speed.
        fun driveCmd(speed: Int, accel: Int) { driveOn = false; spd = speed; if (state == CarState.Connected) send(Protocol.setSpeed(speed, accel)) }
        fun uTurnCmd() { if (state == CarState.Connected) send(Protocol.uTurn()) }
        fun laneCmd(offsetMm: Float, hSpeed: Int, hAccel: Int) { if (state == CarState.Connected) send(Protocol.changeLane(offsetMm, hSpeed, hAccel)) }
        fun offsetCmd(mm: Float) { if (state == CarState.Connected) send(Protocol.setOffset(mm)) }

        private val driveLoop = object : Runnable {
            override fun run() {
                if (!driveOn || state != CarState.Connected) return
                spd = if (spd == 0) 300 else 0          // oscillate to create realistic command traffic (no track needed)
                send(Protocol.setSpeed(spd, 1000))
                main.postDelayed(this, 100)
            }
        }

        private val cb = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    log("[$name] CONNECTED (status=$status); requesting ${priority.label}; discovering")
                    g.requestConnectionPriority(priority.v)
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    lastStatus = status
                    if (state == CarState.Connected) drops++
                    val up = if (connectedAt > 0) (SystemClock.elapsedRealtime() - connectedAt) / 1000 else 0
                    log("[$name] DISCONNECTED ${statusName(status)} after ${up}s (drops=$drops)")
                    state = CarState.Disconnected; driveOn = false
                    try { g.close() } catch (_: Exception) {}; gatt = null
                    // auto-reconnect (autoConnect=true) so a dropped car re-engages when it's back in range
                    main.postDelayed({ if (conns.containsKey(device.address)) connect(reconnect = true) }, 1500)
                }
                refresh()
            }
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val svc = g.getService(Protocol.SERVICE) ?: run { log("[$name] Anki service missing"); return }
                writeChar = svc.getCharacteristic(Protocol.WRITE_CHAR)
                val n = svc.getCharacteristic(Protocol.NOTIFY_CHAR) ?: return
                g.setCharacteristicNotification(n, true)
                val cccd = n.getDescriptor(Protocol.CCCD)
                if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                else { @Suppress("DEPRECATION") run { cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(cccd) } }
            }
            override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
                state = CarState.Connected; connectedAt = SystemClock.elapsedRealtime()
                send(Protocol.sdkMode(true)); send(Protocol.setOffset(0f))
                log("[$name] ready (SDK mode on)")
                if (stress) { driveOn = true; main.post(driveLoop) }
                refresh()
            }
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, v: ByteArray) = handleNotify(v)
            @Deprecated("pre-33") override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION") c.value?.let { handleNotify(it) }
            }
        }

        /** Parse vehicle->client notifications and surface position/transition to the RaceEngine. */
        private fun handleNotify(v: ByteArray) {
            when (Protocol.msgId(v)) {
                Protocol.MSG_LOCALIZATION_POSITION ->
                    Protocol.parsePosition(v)?.let { p -> main.post { onPosition?.invoke(device.address, p) } }
                Protocol.MSG_LOCALIZATION_TRANSITION ->
                    main.post { onTransition?.invoke(device.address) }
                Protocol.MSG_VEHICLE_DELOCALIZED ->
                    main.post { onDelocalized?.invoke(device.address) }
            }
        }

        private fun send(bytes: ByteArray) {
            val g = gatt ?: return; val ch = writeChar ?: return
            if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            else { @Suppress("DEPRECATION") run { ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; ch.value = bytes; g.writeCharacteristic(ch) } }
        }
    }
}
