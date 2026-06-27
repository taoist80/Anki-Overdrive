package dev.overdrive.net

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Client for the multiplayer race-room (server/src/rooms.js) over a WebSocket. Singleton + Compose-
 * observable, same shape as [BackendClient] / ProfileRepository. The broker URL is derived from
 * [BackendClient.baseUrl] — all devices (host + clients) connect to the same Node server on the LAN.
 *
 * Host-authoritative: this client only relays. The host's [onControl] receives remote inputs to feed
 * RaceEngine (Phase 3); clients read [raceState] for their HUD.
 */
object RoomClient {
    enum class Conn { Disconnected, Connecting, Connected }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)   // keep the socket alive across NAT/Wi-Fi idle
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var ws: WebSocket? = null

    // ---- observable state ----
    var conn by mutableStateOf(Conn.Disconnected); private set
    var lobby by mutableStateOf<MpLobby?>(null); private set
    var you by mutableStateOf<MpPlayer?>(null); private set
    var rooms by mutableStateOf<List<MpRoomSummary>>(emptyList()); private set
    var lastError by mutableStateOf<String?>(null); private set
    var raceState by mutableStateOf<List<MpCarState>>(emptyList()); private set

    val isHost: Boolean get() = you?.isHost == true
    val inRoom: Boolean get() = lobby != null

    // ---- host/race hooks (set by the race wiring in Phase 3) ----
    var onControl: ((MpControl) -> Unit)? = null
    var onRaceState: ((List<MpCarState>) -> Unit)? = null
    var onPlayerDisconnected: ((Int) -> Unit)? = null
    var onResults: ((List<MpStanding>) -> Unit)? = null
    var onGameStarting: (() -> Unit)? = null
    var onRoomClosed: ((String) -> Unit)? = null

    fun clearError() { onMain { lastError = null } }

    private fun wsUrl(): String {
        val base = BackendClient.baseUrl
        val scheme = if (base.startsWith("https")) "wss" else "ws"
        return "$scheme://${base.substringAfter("://").trimEnd('/')}/ws/room"
    }

    fun connect() {
        if (conn != Conn.Disconnected) return
        onMain { conn = Conn.Connecting; lastError = null }
        ws = http.newWebSocket(Request.Builder().url(wsUrl()).build(), listener)
    }

    fun disconnect() {
        ws?.close(1000, "bye"); ws = null
        onMain { conn = Conn.Disconnected; lobby = null; you = null; rooms = emptyList(); raceState = emptyList() }
    }

    /** Leave the current room but keep the socket open (e.g. lobby → join screen). */
    fun leaveRoom() {
        send(msg("leaveRoom"))
        onMain { lobby = null; you = null }
    }

    // ---- outbound ----
    fun createRoom(displayName: String, mode: Int = Mp.MODE_RACE, valueToReach: Int = 3) = send(msg("createRoom",
        "displayName" to JsonPrimitive(displayName), "mode" to JsonPrimitive(mode), "valueToReach" to JsonPrimitive(valueToReach)))

    fun listRooms() = send(msg("listRooms"))

    fun joinRoom(code: String, displayName: String) = send(msg("joinRoom",
        "code" to JsonPrimitive(code.uppercase().trim()), "displayName" to JsonPrimitive(displayName)))

    fun selectVehicle(vehicleId: Int, uuid: String? = null, connectionType: String? = null) = send(msg("selectVehicle",
        "vehicleId" to JsonPrimitive(vehicleId),
        "vehicleConnectionUUID" to (uuid?.let { JsonPrimitive(it) } ?: JsonNull),
        "connectionType" to (connectionType?.let { JsonPrimitive(it) } ?: JsonNull)))

    fun setMode(mode: Int) = send(msg("setMode", "mode" to JsonPrimitive(mode)))
    fun setTrack(roadMapFileName: String?, valueToReach: Int) = send(msg("setTrack",
        "roadMapFileName" to (roadMapFileName?.let { JsonPrimitive(it) } ?: JsonNull), "valueToReach" to JsonPrimitive(valueToReach)))
    fun setReady(ready: Boolean) = send(msg("setReady", "ready" to JsonPrimitive(ready)))
    fun startGame() = send(msg("startGame"))
    fun raceStarted() = send(msg("raceStarted"))
    fun backToLobby() = send(msg("backToLobby"))
    fun emote(emotion: String) = send(msg("emote", "emotion" to JsonPrimitive(emotion)))

    fun sendControl(throttle: Float, lane: Int?, fireBay: String?) = send(msg("control",
        "throttle" to JsonPrimitive(throttle),
        "lane" to (lane?.let { JsonPrimitive(it) } ?: JsonNull),
        "fireBay" to (fireBay?.let { JsonPrimitive(it) } ?: JsonNull)))

    fun sendRaceState(tick: Int, cars: List<MpCarState>) = send(buildJsonObject {
        put("t", JsonPrimitive("raceState")); put("tick", JsonPrimitive(tick)); put("cars", json.encodeToJsonElement(cars))
    })

    fun sendResults(standings: List<MpStanding>) = send(buildJsonObject {
        put("t", JsonPrimitive("raceResults")); put("standings", json.encodeToJsonElement(standings))
    })

    // ---- inbound ----
    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = onMain { conn = Conn.Connected }
        override fun onMessage(webSocket: WebSocket, text: String) {
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            val t = obj["t"]?.jsonPrimitive?.contentOrNull ?: return
            handle(t, obj)
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
            onMain { conn = Conn.Disconnected; lastError = t.message ?: "connection failed" }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onMain { conn = Conn.Disconnected }
    }

    private fun handle(t: String, obj: JsonObject) {
        when (t) {
            "roomCreated", "roomJoined" -> onMain {
                lobby = obj.lobby(); you = obj["you"]?.let { json.decodeFromJsonElement(MpPlayer.serializer(), it) }; lastError = null
            }
            "gameLobbyUpdate" -> onMain {
                val l = obj.lobby(); lobby = l
                you = l?.players?.firstOrNull { it.gamePlayerId == you?.gamePlayerId } ?: you
            }
            "gameLobbyStateUpdate" -> onMain {
                val s = obj["state"]?.jsonPrimitive?.contentOrNull
                lobby = lobby?.let { if (s != null) it.copy(state = s) else it }
            }
            "roomList" -> onMain {
                rooms = obj["rooms"]?.let { json.decodeFromJsonElement(ListSerializer(MpRoomSummary.serializer()), it) } ?: emptyList()
            }
            "hostReadyForGameStart" -> onMain { onGameStarting?.invoke() }
            "control" -> { val c = json.decodeFromJsonElement(MpControl.serializer(), obj); onMain { onControl?.invoke(c) } }
            "raceState" -> {
                val cars = obj["cars"]?.let { json.decodeFromJsonElement(ListSerializer(MpCarState.serializer()), it) } ?: emptyList()
                onMain { raceState = cars; onRaceState?.invoke(cars) }
            }
            "results" -> {
                val st = obj["standings"]?.let { json.decodeFromJsonElement(ListSerializer(MpStanding.serializer()), it) } ?: emptyList()
                onMain { onResults?.invoke(st) }
            }
            "playerDisconnected" -> { val pid = obj["playerId"]?.jsonPrimitive?.intOrNull ?: return; onMain { onPlayerDisconnected?.invoke(pid) } }
            "roomClosed" -> { val r = obj["reason"]?.jsonPrimitive?.contentOrNull ?: "room closed"; onMain { lobby = null; you = null; onRoomClosed?.invoke(r) } }
            "roomError" -> { val e = obj["error"]?.jsonPrimitive?.contentOrNull ?: "error"; onMain { lastError = e } }
            // lobbySyncPlayerReady / lobbySyncAllPlayersReady are reflected in gameLobbyUpdate; no-op here
        }
    }

    private fun JsonObject.lobby(): MpLobby? = this["lobby"]?.let { json.decodeFromJsonElement(MpLobby.serializer(), it) }

    private fun msg(t: String, vararg pairs: Pair<String, JsonElement>): JsonObject = buildJsonObject {
        put("t", JsonPrimitive(t)); for ((k, v) in pairs) put(k, v)
    }

    private fun send(obj: JsonObject) { ws?.send(obj.toString()) }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
