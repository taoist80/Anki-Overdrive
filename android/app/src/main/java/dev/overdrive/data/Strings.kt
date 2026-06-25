package dev.overdrive.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Localized string table. The original strings file is `{ "key": { "translation": "..." }, ... }`
 * (plus a `smartling` meta entry we skip). Resolves keys like `gameMode.race.name`.
 */
class Strings(private val map: Map<String, String>) {

    /** Resolve a key; returns [fallback] (default = the key itself) when absent. */
    fun get(key: String?, fallback: String = key.orEmpty()): String =
        key?.let { map[it] } ?: fallback

    /** Resolve a key or null if absent. */
    fun opt(key: String?): String? = key?.let { map[it] }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun load(ctx: Context, assetPath: String): Strings {
            val text = ctx.assets.open(assetPath).bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(text).jsonObject
            val out = HashMap<String, String>(root.size)
            for ((key, value) in root) {
                val obj = value as? JsonObject ?: continue
                val t = (obj["translation"] as? JsonPrimitive)?.contentOrNull
                if (t != null) out[key] = t
            }
            return Strings(out)
        }

        val EMPTY = Strings(emptyMap())
    }
}
