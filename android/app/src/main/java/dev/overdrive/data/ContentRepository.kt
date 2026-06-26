package dev.overdrive.data

import android.content.Context
import dev.overdrive.CarType
import dev.overdrive.GameData
import dev.overdrive.data.model.Chapter
import dev.overdrive.data.model.Commander
import dev.overdrive.data.model.Commander26
import dev.overdrive.data.model.GameMode
import dev.overdrive.data.model.GameModeDisplayFile
import dev.overdrive.data.model.Mission
import dev.overdrive.data.model.RawStarChallenge
import kotlinx.serialization.json.Json

/**
 * Single source of static game content, loaded from the bundled 3.4.0 config (assets/gamedata) and
 * resolved against the localized string table. Idempotent [load]; everything is read-only after.
 *
 * Phase 1 surfaces: game modes (mode-select grid), the campaign (chapters → missions → commanders),
 * and the car catalog (via [GameData]). Items/mutators/loot/upgrades remain bundled for later phases.
 */
object ContentRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile var loaded = false
        private set

    var strings: Strings = Strings.EMPTY
        private set
    var modes: List<GameMode> = emptyList()
        private set
    var chapters: List<Chapter> = emptyList()
        private set
    var missionsById: Map<String, Mission> = emptyMap()
        private set
    var commandersById: Map<String, Commander> = emptyMap()
        private set
    /** The real 2.6 Tournament roster (authentic names/bios/portraits), keyed by commander_gen2_NN. */
    var commanders26ById: Map<String, Commander26> = emptyMap()
        private set
    var starChallengesById: Map<String, RawStarChallenge> = emptyMap()
        private set

    val cars: List<CarType> get() = GameData.cars

    /** Strings keys for modes whose internal name differs from the string key suffix. */
    private val modeKeyAlias = mapOf("koth" to "king", "timetrial" to "timeTrial")

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        GameData.load(ctx)
        strings = Strings.load(ctx, "gamedata/strings-en.json")

        val display = json.decodeFromString<GameModeDisplayFile>(read(ctx, "gamedata/gameModeDisplay.json"))
        modes = display.standardModes.filter { it.enabled }.map { raw ->
            val keyName = modeKeyAlias[raw.name] ?: raw.name
            val img = raw.image.substringAfterLast('/')
            GameMode(
                gameId = raw.gameId,
                internalName = raw.name,
                title = strings.get("gameMode.$keyName.name", raw.name.replaceFirstChar { it.uppercase() }),
                description = strings.opt("gameMode.$keyName.description"),
                imageAsset = img.ifBlank { null }?.let { "ui/$it.png" },
                tintArgb = parseTint(raw.tint),
            )
        }

        chapters = json.decodeFromString(read(ctx, "gamedata/campaign/chapters.json"))
        val missions: List<Mission> = json.decodeFromString(read(ctx, "gamedata/campaign/campaign.json"))
        missionsById = missions.associateBy { it.id }
        val commanders: List<Commander> = json.decodeFromString(read(ctx, "gamedata/commanders.json"))
        commandersById = commanders.associateBy { it.id }
        val commanders26: List<Commander26> = json.decodeFromString(read(ctx, "gamedata/campaign/commanders_2_6.json"))
        commanders26ById = commanders26.associateBy { it.id }
        val challenges: List<RawStarChallenge> = json.decodeFromString(read(ctx, "gamedata/star_challenges.json"))
        starChallengesById = challenges.associateBy { it.id }

        ItemRepository.load(ctx)   // real item/weapon/loot/upgrade catalog (Phase 7)

        loaded = true
    }

    fun modeByInternalName(name: String): GameMode? = modes.firstOrNull { it.internalName == name }
    fun missionsFor(chapter: Chapter): List<Mission> = chapter.missions.mapNotNull { missionsById[it] }
    fun commander(id: String): Commander? = commandersById[id]
    /** The authentic 2.6 opponent (real name/bio/portrait) for a mission's `opponent` id. */
    fun commander26(id: String?): Commander26? = id?.let { commanders26ById[it] }

    private fun read(ctx: Context, path: String): String =
        ctx.assets.open(path).bufferedReader().use { it.readText() }

    /**
     * Parse the config tint hex into an ARGB long. Configs use `#RRGGBB` or `#RRGGBBAA` (alpha
     * last); we normalize both to `0xAARRGGBB`.
     */
    private fun parseTint(hex: String): Long {
        val h = hex.removePrefix("#")
        return runCatching {
            when (h.length) {
                6 -> 0xFF000000L or h.toLong(16)
                8 -> {
                    val v = h.toLong(16)
                    val r = (v ushr 24) and 0xFF
                    val g = (v ushr 16) and 0xFF
                    val b = (v ushr 8) and 0xFF
                    val a = v and 0xFF
                    (a shl 24) or (r shl 16) or (g shl 8) or b
                }
                else -> 0xFFFFFFFFL
            }
        }.getOrDefault(0xFFFFFFFFL)
    }
}
