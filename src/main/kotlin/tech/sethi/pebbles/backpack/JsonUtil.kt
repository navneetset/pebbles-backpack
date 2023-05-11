package tech.sethi.pebbles.backpack

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object JsonUtil {
    val json = Json { encodeDefaults = false }

    inline fun <reified T> toJson(obj: T): String {
        return json.encodeToString(obj)
    }

    inline fun <reified T> fromJson(jsonStr: String): T {
        return json.decodeFromString(jsonStr)
    }
}
