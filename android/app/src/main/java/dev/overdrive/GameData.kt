package dev.overdrive

import android.content.Context
import org.json.JSONObject

/** The three roster tabs the original car-select groups vehicles under. */
enum class CarCategory(val label: String) { SUPERCARS("Supercars"), SUPERTRUCKS("Supertrucks"), DRIVE("Drive") }

/** A car definition, loaded from the original vehicleTypes.json (reused as-is). */
data class CarType(
    val id: Int,
    val name: String,
    val assetName: String?,
    val series: String?,
    val attackBays: Int,
    val supportBays: Int,
    val spriteFile: String?,   // a file in assets/cars/, or null
    val category: CarCategory = CarCategory.SUPERCARS,
) {
    /**
     * The branded two-tone name wordmark carved from 4.0.4 (ui_logo_<asset>), if one shipped for this
     * model. Resolves by [assetName] (e.g. "groundshock" -> "ui/ui_logo_groundshock.webp"); callers
     * fall back to the [RacingName] text when the asset is absent (rememberAsset returns null).
     */
    val logoAsset: String? get() = assetName?.let { "ui/ui_logo_$it.webp" }
}

/** Loads the reused Overdrive content (car catalog, etc.) from bundled assets/gamedata. */
object GameData {
    var cars: List<CarType> = emptyList()
        private set

    fun load(ctx: Context) {
        if (cars.isNotEmpty()) return
        val sprites = (ctx.assets.list("cars") ?: emptyArray()).toList()
        val txt = ctx.assets.open("gamedata/vehicleTypes.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(txt).getJSONArray("vehicle_types")
        val out = ArrayList<CarType>()
        for (i in 0 until arr.length()) {
            val v = arr.getJSONObject(i)
            val id = v.optInt("id", -1)
            if (id == 100 || id == 101 || id < 0) continue   // Future / Unrecognized / invalid
            val name = v.optString("name", "Car $id")
            val asset = v.optStringOrNull("asset_name")
                ?: v.optStringOrNull("image")?.removeSuffix(".png")
            val series = v.optStringOrNull("series")
            val bays = v.optJSONObject("bays")
            val atk = bays?.optJSONObject("attack")?.optInt("max", 0) ?: 0
            val sup = bays?.optJSONObject("support")?.optInt("max", 0) ?: 0
            val sprite = asset?.let { a ->
                sprites.firstOrNull { it.equals("$a-left-720.png", true) }
                    ?: sprites.firstOrNull { it.startsWith("$a-left", true) }
                    ?: sprites.firstOrNull { it.startsWith("$a-", true) }
                    ?: sprites.firstOrNull { it.equals("$a.png", true) }
            }
            out.add(CarType(id, name, asset, series, atk, sup, sprite, categoryOf(v, name)))
        }
        cars = out.sortedBy { it.id }
    }

    /**
     * Map a vehicle to its car-select tab. vehicleTypes.json has no explicit category field, so we
     * derive it: Gen-1 cars carry `voice_id="Gen1"` (Drive); the two supertruck chassis `extends`
     * 102/103, plus Mammoth (the International MXT monster truck); everything else is a Supercar.
     */
    private fun categoryOf(v: JSONObject, name: String): CarCategory = when {
        v.optStringOrNull("voice_id") == "Gen1" -> CarCategory.DRIVE
        v.optInt("extends", -1) in intArrayOf(102, 103) || name.equals("Mammoth", true) -> CarCategory.SUPERTRUCKS
        else -> CarCategory.SUPERCARS
    }

    /** Resolve a car by its Anki model id (as broadcast in the BLE advertisement). */
    fun byModelId(modelId: Int): CarType? = cars.firstOrNull { it.id == modelId }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key).ifBlank { null }
