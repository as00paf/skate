package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.prefab.PrefabAsset
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Persists [PrefabAsset] templates as versioned `.prefab` files (`{ "schema", "id", "template" }`). */
class PrefabStore(private val serializer: Serializer) {

    fun save(asset: PrefabAsset, file: File) {
        val root = JsonObject(
            mapOf(
                "id" to kotlinx.serialization.json.JsonPrimitive(asset.id),
                "template" to asset.template
            )
        )
        file.parentFile?.mkdirs()
        file.writeText(serializer.encodeFile(FileKind.PREFAB, root))
    }

    /**
     * Reads [file]. A prefab with Git conflict markers is refused with a clear error instead of a JSON parse error
     * (EDT-60 §7.2, same rule and message as the editor's scene loaders; engine-side, so no editor dependency).
     */
    fun load(file: File): PrefabAsset {
        val text = file.readText()
        if (hasConflictMarkers(text)) throw PrefabConflictException(file.name)
        val root = serializer.decodeFile<JsonObject>(FileKind.PREFAB, text)
        val id = root["id"]?.jsonPrimitive?.content ?: throw IllegalStateException("Prefab '${file.name}' has no id")
        val template = root["template"]?.jsonObject ?: throw IllegalStateException("Prefab '${file.name}' has no template")
        return PrefabAsset(id, template)
    }

    /** Loads every `.prefab` under [root], keyed by prefab id; unreadable files are reported through [onError]. */
    fun loadAll(root: File, onError: (File, Throwable) -> Unit = { _, _ -> }): Map<String, PrefabAsset> {
        val result = LinkedHashMap<String, PrefabAsset>()
        root.walkTopDown().filter { it.isFile && it.extension == EXTENSION }.sortedBy { it.path }.forEach { file ->
            try {
                val asset = load(file)
                result[asset.id] = asset
            } catch (e: Exception) {
                onError(file, e)
            }
        }
        return result
    }

    companion object {
        const val EXTENSION = "prefab"

        /** A line starting with `<<<<<<< ` or `>>>>>>> ` (or the bare marker). */
        private val CONFLICT_MARKER = Regex("""^(<{7}|>{7})( |$)""", RegexOption.MULTILINE)

        fun hasConflictMarkers(text: String): Boolean = CONFLICT_MARKER.containsMatchIn(text)
    }
}

/** A `.prefab` file left with unresolved Git conflict markers. */
class PrefabConflictException(val fileName: String) :
    IllegalStateException("$fileName has unresolved Git conflict markers; resolve it in Version Control (Keep mine or Take theirs) first")
