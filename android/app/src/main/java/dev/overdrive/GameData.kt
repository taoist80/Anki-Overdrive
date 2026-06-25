package dev.overdrive

import android.content.Context
import org.json.JSONObject

/** A car definition, loaded from the original vehicleTypes.json (reused as-is). */
data class CarType(
    val id: Int,
    val name: String,
    val assetName: String?,
    val series: String?,
    val attackBays: Int,
    val supportBays: Int,
    val spriteFile: String?,   // a file in assets/cars/, or null
)

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
            out.add(CarType(id, name, asset, series, atk, sup, sprite))
        }
        cars = out.sortedBy { it.id }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key).ifBlank { null }
