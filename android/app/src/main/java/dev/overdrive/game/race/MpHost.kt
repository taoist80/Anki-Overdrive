package dev.overdrive.game.race

import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.overdrive.net.Mp
import dev.overdrive.net.MpCarState
import dev.overdrive.net.MpLobby
import dev.overdrive.net.MpStanding
import dev.overdrive.net.RoomClient

/**
 * Host-side bridge between the race-room ([RoomClient]) and the local [RaceEngine] for multiplayer
 * (Phase 12). Only the host runs this. The host runs the *real* race on its BLE cars; this object:
 *  - feeds remote players' control inputs into the engine (replacing those cars' AI), and
 *  - streams the authoritative race state + final standings back to the clients.
 *
 * It deliberately does NOT touch the race screens: the host plays through the normal
 * MatchSetup → Scan → Countdown → InRaceHud flow, which drives the engine as usual.
 */
object MpHost {
    private const val TAG = "OverdriveX"
    private val engine get() = RaceEngineHolder.engine
    private val main = Handler(Looper.getMainLooper())
    private const val BROADCAST_MS = 120L

    private var active = false
    private var tick = 0
    private var lastRunning = false
    private var resultsSent = false

    /**
     * Configure the engine for an MP host race from the lobby roster, then start relaying. Call when the
     * host leaves the lobby into the race flow. [RaceEngine.setRemotePlayers] survives MatchSetup's re-arm;
     * [RaceEngine.start] maps the remote players onto the non-player cars.
     */
    fun beginHostRace(lobby: MpLobby) {
        val you = RoomClient.you ?: return
        val remoteIds = lobby.occupied.filter { !it.isHost }.sortedBy { it.slotId }.map { it.gamePlayerId }
        engine.setRemotePlayers(you.gamePlayerId, remoteIds)
        engine.setLapTarget(lobby.valueToReach)
        engine.setRivals(emptyList())                 // cars past the remote players run generic AI
        engine.campaignMissionId = ""; engine.ladderRung = -1
        engine.arm(Mp.engineMode(lobby.mode))         // set state.mode (MatchSetup re-arms; remote players persist)

        RoomClient.onControl = { c -> engine.applyRemoteControl(c.playerId, c.throttle, c.lane, c.fireBay) }

        active = true; tick = 0; lastRunning = false; resultsSent = false
        main.removeCallbacks(broadcast); main.post(broadcast)
        Log.i(TAG, "MpHost.beginHostRace: room ${lobby.code} remotes=$remoteIds mode=${Mp.engineMode(lobby.mode)}")
    }

    fun stop() {
        active = false
        main.removeCallbacks(broadcast)
        RoomClient.onControl = null
    }

    /** Periodic: announce race start, stream authoritative car state, send standings once on finish. */
    private val broadcast = object : Runnable {
        override fun run() {
            if (!active) return
            if (!RoomClient.inRoom) { stop(); return }       // host left the room — nothing to broadcast to
            val st = engine.state
            if (st.running && !lastRunning) { RoomClient.raceStarted(); lastRunning = true; Log.i(TAG, "MpHost: race running") }
            if (st.running) RoomClient.sendRaceState(tick++, carStates())
            val ended = st.finished || (lastRunning && !st.running)
            if (ended && !resultsSent) {
                resultsSent = true
                RoomClient.sendResults(standings())
                Log.i(TAG, "MpHost: race ended — standings sent")
                stop(); return
            }
            main.postDelayed(this, BROADCAST_MS)
        }
    }

    private fun pid(addr: String): Int = engine.playerIdOf(addr).let { if (it < 0) Mp.NO_PLAYER_ID else it }

    private fun carStates(): List<MpCarState> = engine.state.standings.mapIndexed { i, c ->
        MpCarState(
            gamePlayerId = pid(c.address), vehicleId = c.modelId, speed = c.speedMmPerSec, lap = c.laps,
            place = i + 1, health = c.health.toInt(), energy = c.energy.toInt(), offTrack = c.offTrack,
        )
    }

    private fun standings(): List<MpStanding> {
        val target = engine.state.lapTarget
        return engine.state.standings.mapIndexed { i, c ->
            MpStanding(
                gamePlayerId = pid(c.address), place = i + 1, displayName = c.name,
                vehicleId = c.modelId, laps = c.laps, finished = target > 0 && c.laps >= target,
            )
        }
    }
}
