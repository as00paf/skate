package com.pafoid.skate.engine.gameplay

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.logging.Logger

/** Loads/saves standalone controller config files (`*.character.json`): project file first, then bundled resource. */
object CharacterConfigs {

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val logger = Logger.getLogger("CharacterConfigs")

    fun readText(path: String): String? {
        if (path.isBlank()) return null
        val file = File(path)
        if (file.isFile) return file.readText()
        return CharacterConfigs::class.java.classLoader.getResourceAsStream(path.trimStart('/'))?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    inline fun <reified T> load(path: String, default: () -> T): T = decode(path, readText(path), default)

    inline fun <reified T> decode(path: String, text: String?, default: () -> T): T {
        if (text == null) return default()
        return try {
            json.decodeFromString<T>(text)
        } catch (e: Exception) {
            report("Invalid controller config '$path', using defaults: ${e.message}")
            default()
        }
    }

    inline fun <reified T> save(path: String, config: T) {
        File(path).apply { parentFile?.mkdirs() }.writeText(json.encodeToString(config))
    }

    fun report(message: String) = logger.warning(message)
}

/** Flat property view of a config so an inspector can list and edit every tuning value generically. */
object ConfigProperties {

    /** Dotted path -> primitive for every leaf (numbers, booleans, strings) of [element]. */
    fun flatten(element: JsonElement, prefix: String = ""): Map<String, JsonPrimitive> {
        val out = linkedMapOf<String, JsonPrimitive>()
        when (element) {
            is JsonObject -> element.forEach { (k, v) -> out += flatten(v, if (prefix.isEmpty()) k else "$prefix.$k") }
            is JsonPrimitive -> out[prefix] = element
            else -> Unit
        }
        return out
    }

    /** Returns a copy of [root] with the leaf at dotted [path] replaced by [value]; unknown paths are ignored. */
    fun set(root: JsonObject, path: String, value: JsonPrimitive): JsonObject {
        val head = path.substringBefore('.')
        val rest = path.substringAfter('.', "")
        val current = root[head] ?: return root
        val updated: JsonElement = when {
            rest.isEmpty() -> if (current is JsonPrimitive) value else return root
            current is JsonObject -> set(current, rest, value)
            else -> return root
        }
        return buildJsonObject { root.forEach { (k, v) -> put(k, if (k == head) updated else v) } }
    }
}
