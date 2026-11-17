package com.pafoid.skate.engine.gameplay

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.EngineDefaults
import java.io.File
import java.util.logging.Logger

/**
 * Loads/saves standalone controller config files (`*.character.json`). A path is read through [reader] when one is
 * installed (the engine's asset paths: project files, the exported game's pack and `engine:` references with their
 * project override, GPL-26), then as a file, then as a classpath resource. An `engine:` reference without a reader
 * resolves to the engine's own file ([EngineDefaultResolver]).
 */
object CharacterConfigs {

    /** Reads a path through the running engine's assets (`AssetsManager.readText`); null outside an engine (tests). */
    @Volatile
    var reader: ((String) -> String?)? = null

    private val engineDefaults by lazy { EngineDefaultResolver() }

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val logger = Logger.getLogger("CharacterConfigs")

    fun readText(path: String): String? {
        if (path.isBlank()) return null
        reader?.let { read -> runCatching { read(path) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it } }
        if (EngineDefaults.isReference(path)) return engineDefaults.resolve(path)?.takeIf { it.isFile }?.readText()
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

/**
 * Flat property view of a config so an inspector can list and edit every tuning value generically. Paths are dotted;
 * an array element is its index (`states.0.loop`), so animator controllers can be edited the same way (GPL-26).
 */
object ConfigProperties {

    /** Dotted path -> primitive for every leaf (numbers, booleans, strings) of [element], in document order. */
    fun flatten(element: JsonElement, prefix: String = ""): Map<String, JsonPrimitive> {
        val out = linkedMapOf<String, JsonPrimitive>()
        fun child(key: String) = if (prefix.isEmpty()) key else "$prefix.$key"
        when (element) {
            is JsonObject -> element.forEach { (k, v) -> out += flatten(v, child(k)) }
            is JsonArray -> element.forEachIndexed { i, v -> out += flatten(v, child(i.toString())) }
            is JsonNull -> Unit
            is JsonPrimitive -> out[prefix] = element
        }
        return out
    }

    /** Returns a copy of [root] with the leaf at dotted [path] replaced by [value]; unknown paths are ignored. */
    fun set(root: JsonObject, path: String, value: JsonPrimitive): JsonObject = (setIn(root, path, value) as? JsonObject) ?: root

    private fun setIn(node: JsonElement, path: String, value: JsonPrimitive): JsonElement? {
        val head = path.substringBefore('.')
        val rest = path.substringAfter('.', "")
        fun updated(current: JsonElement): JsonElement? = when {
            rest.isEmpty() -> if (current is JsonPrimitive && current !is JsonNull) value else null
            else -> setIn(current, rest, value)
        }
        return when (node) {
            is JsonObject -> {
                val replaced = updated(node[head] ?: return null) ?: return null
                buildJsonObject { node.forEach { (k, v) -> put(k, if (k == head) replaced else v) } }
            }
            is JsonArray -> {
                val index = head.toIntOrNull()?.takeIf { it in node.indices } ?: return null
                val replaced = updated(node[index]) ?: return null
                JsonArray(node.mapIndexed { i, v -> if (i == index) replaced else v })
            }
            else -> null
        }
    }
}
