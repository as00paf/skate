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

    fun load(file: File): PrefabAsset {
        val root = serializer.decodeFile<JsonObject>(FileKind.PREFAB, file.readText())
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
    }
}
