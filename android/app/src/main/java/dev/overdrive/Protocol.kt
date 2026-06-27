package dev.overdrive

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Anki Overdrive / Drive BLE protocol.
 * GATT UUIDs verified from the official app binary (libDriveEngine/LeService);
 * message IDs + byte layouts from Anki's open drive-sdk.
 *
 * Wire format per message: [size][msg_id][payload…], where size = bytes following the size byte.
 * Multi-byte fields are little-endian.
 */
object Protocol {
    val SERVICE: UUID = UUID.fromString("be15beef-6186-407e-8381-0bd89c4d8df4")
    val WRITE_CHAR: UUID = UUID.fromString("be15bee1-6186-407e-8381-0bd89c4d8df4")
    val NOTIFY_CHAR: UUID = UUID.fromString("be15bee0-6186-407e-8381-0bd89c4d8df4")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // client -> vehicle
    private const val MSG_SDK_MODE = 0x90
    private const val MSG_SET_SPEED = 0x24
    private const val MSG_SET_OFFSET = 0x2c
    private const val MSG_CHANGE_LANE = 0x25
    private const val MSG_TURN = 0x32           // SET_VEHICLE_UTURN (anki drive-sdk)
    private const val MSG_PING = 0x16
    private const val MSG_VERSION = 0x18
    private const val MSG_BATTERY = 0x1a
    private const val MSG_DISCONNECT = 0x0d

    // vehicle -> client (notifications)
    const val MSG_VERSION_RESPONSE = 0x19
    const val MSG_BATTERY_RESPONSE = 0x1b
    const val MSG_LOCALIZATION_POSITION = 0x27
    const val MSG_LOCALIZATION_TRANSITION = 0x29
    const val MSG_LOCALIZATION_INTERSECTION = 0x2a
    const val MSG_VEHICLE_DELOCALIZED = 0x2b   // car has left the track (off-track)

    private const val SDK_OPTION_OVERRIDE_LOCALIZATION = 0x01

    private fun msg(id: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val out = ByteArray(payload.size + 2)
        out[0] = (payload.size + 1).toByte()   // size = msg_id + payload
        out[1] = id.toByte()
        payload.copyInto(out, 2)
        return out
    }

    private fun le(capacity: Int, fill: ByteBuffer.() -> Unit): ByteArray =
        ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN).apply(fill).array()

    /**
     * SDK mode. We **must** override localization: our app is the basestation/localization authority and
     * the car drives *raw* at the speed we command. Without override, the car tries to self-localize an
     * unmapped track (impossible on a fresh scan) and won't move — and `respectRoadPieceSpeedLimit` with
     * no road network caps it to 0. (Letting the firmware drive would need 2.6's full on-phone planner,
     * which we don't run.) So curve safety is handled manually in [RaceEngine], not by the firmware.
     */
    fun sdkMode(enable: Boolean = true, overrideLocalization: Boolean = true): ByteArray =
        msg(MSG_SDK_MODE, byteArrayOf(
            (if (enable) 1 else 0).toByte(),
            (if (overrideLocalization) SDK_OPTION_OVERRIDE_LOCALIZATION else 0).toByte(),
        ))

    /**
     * SET_SPEED. The 3rd byte is `respectRoadPieceSpeedLimit`. It only works when the firmware owns the
     * track (its own localization + road network) — under our override-localization model the car has no
     * network, so `1` caps it to 0 and it won't move. So we send `0` (raw speed) and handle curve safety
     * manually in [RaceEngine] (4.0.4/2.6 send 1 only because their on-phone planner feeds the network).
     */
    fun setSpeed(mmPerSec: Int, accelMmPerSec2: Int = 1000, respectLimits: Boolean = false): ByteArray =
        msg(MSG_SET_SPEED, le(5) { putShort(mmPerSec.toShort()); putShort(accelMmPerSec2.toShort()); put(if (respectLimits) 1 else 0) })

    fun setOffset(offsetMm: Float): ByteArray =
        msg(MSG_SET_OFFSET, le(4) { putFloat(offsetMm) })

    fun changeLane(offsetMm: Float, hSpeed: Int = 300, hAccel: Int = 1000): ByteArray =
        msg(MSG_CHANGE_LANE, le(10) {
            putShort(hSpeed.toShort()); putShort(hAccel.toShort()); putFloat(offsetMm); put(0); put(0)
        })

    // Turn types / triggers (anki drive-sdk). A 180° u-turn realigns a wrong-way car with race direction.
    private const val TURN_UTURN = 3
    private const val TURN_TRIGGER_IMMEDIATE = 0
    fun uTurn(): ByteArray = msg(MSG_TURN, byteArrayOf(TURN_UTURN.toByte(), TURN_TRIGGER_IMMEDIATE.toByte()))

    /** Localization parse-flag bits: bit 0x40 = the vehicle is driving against the piece's forward dir. */
    const val PARSEFLAGS_REVERSE_DRIVING = 0x40
    const val PARSEFLAGS_REVERSE_PARSING = 0x20

    fun ping(): ByteArray = msg(MSG_PING)
    fun version(): ByteArray = msg(MSG_VERSION)
    fun battery(): ByteArray = msg(MSG_BATTERY)
    fun disconnect(): ByteArray = msg(MSG_DISCONNECT)

    data class Position(
        val locationId: Int,
        val roadPieceId: Int,
        val offsetMm: Float,
        val speedMmPerSec: Int,
        val parsingFlags: Int,
    )

    fun msgId(d: ByteArray): Int = if (d.size >= 2) d[1].toInt() and 0xff else -1

    fun parsePosition(d: ByteArray): Position? {
        if (msgId(d) != MSG_LOCALIZATION_POSITION || d.size < 11) return null
        val bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
        return Position(
            locationId = d[2].toInt() and 0xff,
            roadPieceId = d[3].toInt() and 0xff,
            offsetMm = bb.getFloat(4),
            speedMmPerSec = bb.getShort(8).toInt() and 0xffff,
            parsingFlags = d[10].toInt() and 0xff,
        )
    }

    fun u16le(d: ByteArray, at: Int): Int =
        if (d.size > at + 1) ((d[at + 1].toInt() and 0xff) shl 8) or (d[at].toInt() and 0xff) else -1

    /**
     * Anki vehicle advertisement manufacturer data layout: [product_id, model_id, _reserved,
     * identifier_lo, identifier_hi]. model_id maps to vehicleTypes.json `id` (the car model).
     * Returns -1 if the data is too short.
     */
    fun modelIdFromMfg(data: ByteArray?): Int =
        if (data != null && data.size >= 2) data[1].toInt() and 0xff else -1
}
