package com.pafoid.skate.engine.assets.loaders

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * External files of a glTF and where they are relative to the model (AST-22). A `.gltf` names its buffers and images
 * by URI relative to the `.gltf` file (Poly Haven puts textures in a `textures/` subfolder), so loading, packing and
 * pack extraction all resolve them against the model's folder, never the working directory.
 */
object GltfReferences {
    private val json = Json { ignoreUnknownKeys = true }

    /** `buffers[].uri` and `images[].uri` of [gltfText], percent-decoded; `data:` URIs and bad JSON give nothing. */
    fun uris(gltfText: String): List<String> {
        val root = runCatching { json.parseToJsonElement(gltfText) as? JsonObject }.getOrNull() ?: return emptyList()
        return listOf("buffers", "images").flatMap { key ->
            (root[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("uri")?.jsonPrimitive?.contentOrNull }
        }.filterNot { it.startsWith("data:", ignoreCase = true) || it.isBlank() }.map(::decode).distinct()
    }

    /**
     * [uri] joined to the folder of [modelPath], with `/` separators and `.`/`..` segments collapsed; null when the
     * URI is absolute, has a scheme, or climbs above the model's root.
     */
    fun resolve(modelPath: String, uri: String): String? {
        val decoded = decode(uri).replace('\\', '/')
        if (decoded.startsWith("/") || Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(decoded)) return null
        val base = modelPath.replace('\\', '/').substringBeforeLast('/', "")
        val segments = ArrayList<String>()
        val absoluteRoot = base.startsWith("/")
        for (part in (if (base.isEmpty()) decoded else "$base/$decoded").split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty() || segments.last() == "..") return null else segments.removeAt(segments.size - 1)
                else -> segments += part
            }
        }
        val joined = segments.joinToString("/")
        return if (absoluteRoot) "/$joined" else joined
    }

    /**
     * Texture file for a material [uri] as Assimp reports it: the file next to [modelPath] when it exists, else [uri]
     * unchanged (engine OBJs whose `.mtl` name paths relative to the working directory).
     */
    fun texturePath(modelPath: String, uri: String, exists: (String) -> Boolean = { File(it).isFile }): String {
        if (File(uri).isAbsolute && exists(uri)) return uri
        val relative = resolve(modelPath, uri)?.let { File(it).path }
        return relative?.takeIf(exists) ?: uri
    }

    /** `%20` → space etc.; `+` stays (URIs, not form data); malformed escapes are kept as written. */
    fun decode(uri: String): String {
        if ('%' !in uri) return uri
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < uri.length) {
            val c = uri[i]
            val hex = if (c == '%' && i + 2 < uri.length) uri.substring(i + 1, i + 3).takeIf { h -> h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } }?.toInt(16) else null
            if (hex != null) {
                bytes.write(hex)
                i += 3
            } else {
                bytes.writeBytes(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return bytes.toString(Charsets.UTF_8)
    }
}
