package com.pafoid.skate.engine.assets

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Contents of the `<file>.meta` sidecar. Bump [CURRENT_SCHEMA] and add a migration when the format changes. */
@Serializable
data class AssetMeta(
    val schema: Int = CURRENT_SCHEMA,
    val guid: AssetId,
    val importer: String = "",
    val settings: Map<String, String> = emptyMap()
) {
    companion object {
        const val CURRENT_SCHEMA = 1
        const val EXTENSION = "meta"

        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

        fun sidecarOf(file: File): File = File(file.parentFile, "${file.name}.$EXTENSION")

        fun isSidecar(file: File): Boolean = file.name.endsWith(".$EXTENSION")

        fun encode(meta: AssetMeta): String = json.encodeToString(serializer(), meta)

        fun decode(text: String): AssetMeta = json.decodeFromString(serializer(), text)

        fun read(file: File): AssetMeta? =
            runCatching { decode(sidecarOf(file).readText()) }.getOrNull()

        fun write(file: File, meta: AssetMeta) {
            sidecarOf(file).writeText(encode(meta))
        }

        fun importerFor(file: File): String = when (file.extension.lowercase()) {
            "png", "jpg", "jpeg", "bmp", "tga", "hdr" -> "texture"
            "obj", "fbx", "gltf", "glb", "dae" -> "model"
            "wav", "ogg", "mp3" -> "sound"
            "glsl", "vert", "frag", "vs", "fs" -> "shader"
            "scene", "prefab", "json" -> "data"
            else -> "default"
        }
    }
}
