package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.assets.loaders.GltfReferences
import com.pafoid.skate.engine.utils.AssetsResolver
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

/**
 * Project-relative asset references (AST-09). Saved scenes, prefabs, project files and `.skmat` assets store paths
 * relative to the project folder (`Assets/Textures/rock.png`), so a project can be moved or exported.
 *
 * - In the editor, [projectRoot] is the open project's folder: loading turns references back into absolute paths
 *   ([absolute]) and the asset loaders accept either form.
 * - In an exported game there is no project folder: references stay relative and are read from [pack].
 *
 * Only values under [PATH_KEYS] are rewritten in saved JSON; a guard test keeps that list in sync with the
 * serializable path fields.
 *
 * Engine defaults (AST-26 §4.4) are saved as `engine:<logical path>`: [absolute] resolves them through
 * [engineDefaults] (project override, engine file, classpath copy) and [relativize] turns those files back into the
 * reference, so a load → save round trip keeps `engine:`. An exported game reads them from the pack, where the entry
 * is stored under the reference itself.
 */
class AssetPaths {
    /** Folder of the open project, or null (no project, or an exported game). */
    @Volatile
    var projectRoot: File? = null
        set(value) {
            field = value
            engineDefaults.invalidate()
        }

    /** Resolves `engine:` references; replaced in tests to point at another engine root. */
    @Volatile
    var engineDefaults: EngineDefaultResolver = EngineDefaultResolver(projectRoot = { projectRoot })

    /** The exported game's pack, once opened. */
    @Volatile
    var pack: AssetsResolver? = null

    private val extracted = ConcurrentHashMap<String, File>()
    private val cacheDir: File by lazy { Files.createTempDirectory("skate-assets").toFile().also { it.deleteOnExit() } }

    /**
     * [path] as saved: an engine default file (an override or the engine's own file) becomes its `engine:` reference,
     * a project file becomes project-relative, anything else is unchanged.
     */
    fun relativize(path: String): String {
        if (path.isBlank() || EngineDefaults.isReference(path)) return path
        if (isAbsolute(path)) engineDefaults.referenceFor(File(path))?.let { return it }
        return relativize(path, projectRoot)
    }

    /** [path] as a file path: `engine:` references resolve through [engineDefaults], project-relative paths in [projectRoot]. */
    fun absolute(path: String): String {
        if (EngineDefaults.isReference(path)) return engineDefaults.resolve(path)?.path ?: path
        return absolute(path, projectRoot)
    }

    /** [path] rewritten in every saved field of [element] (see [PATH_KEYS]) to be project-relative. */
    fun relativizeJson(element: JsonElement): JsonElement = if (projectRoot != null) rewrite(element) { relativize(it) } else element

    /** The reverse of [relativizeJson]: project-relative references become absolute paths in [projectRoot]. */
    fun absoluteJson(element: JsonElement): JsonElement = if (projectRoot != null) rewrite(element) { absolute(it) } else element

    private fun packed(path: String): Boolean = pack?.let { it.initialized && it.find(path) != null } == true

    /** Bytes of the asset at [path]: from the pack when it has it, else from disk; null when neither does. */
    fun readBytes(path: String): ByteArray? {
        if (path.isBlank()) return null
        if (packed(path)) return pack?.resolveBytes(path)
        if (EngineDefaults.isReference(path)) return engineDefaults.resolve(path)?.readBytes()
        return File(absolute(path)).takeIf { it.isFile }?.readBytes()
    }

    fun readText(path: String): String? = readBytes(path)?.toString(Charsets.UTF_8)

    /**
     * A file on disk for [path], for loaders that need one (sounds, streams). Packed assets are extracted once to a
     * temporary folder; anything else maps to [absolute]. The result may not exist.
     */
    fun localPath(path: String): String {
        if (path.isBlank()) return path
        // An engine default: the packed entry (it may be the project's override) before the engine's own copy.
        if (EngineDefaults.isReference(path) && !packed(path)) return engineDefaults.resolve(path)?.path ?: path
        val local = if (EngineDefaults.isReference(path)) "" else absolute(path)
        if ((local.isNotEmpty() && File(local).isFile) || !packed(path)) return local
        val target = extract(path)
        // Model files load their companions (.mtl, .bin, textures) from the same folder.
        if (path.substringAfterLast('.').lowercase() in MODEL_EXTENSIONS) siblingsInPack(path).forEach(::extract)
        // A .gltf may also reference files in subfolders (textures/...): extract exactly those (AST-22)
        if (path.substringAfterLast('.').equals("gltf", ignoreCase = true)) gltfReferencesInPack(path, target).forEach(::extract)
        return target.path
    }

    private fun extract(path: String): File = extracted.getOrPut(path) {
        val target = File(cacheDir, path.replace('\\', '/').replace(":", "").trimStart('/'))
        target.parentFile.mkdirs()
        target.writeBytes(pack?.resolveBytes(path) ?: ByteArray(0))
        target.deleteOnExit()
        target
    }

    /** Pack paths of the buffers and images [gltf] (already extracted to [extracted]) names, present in the pack. */
    private fun gltfReferencesInPack(gltf: String, extracted: File): List<String> =
        GltfReferences.uris(extracted.readText())
            .mapNotNull { GltfReferences.resolve(gltf, it) }
            .filter { packed(it) }

    private fun siblingsInPack(path: String): List<String> {
        val dir = path.replace('\\', '/').substringBeforeLast('/', "")
        val resolver = pack ?: return emptyList()
        return resolver.assetsAtlas.values.flatten().map { it.path }
            .filter { it.replace('\\', '/').substringBeforeLast('/', "") == dir && it != path }
    }

    companion object {
        /** JSON keys whose string values (or string arrays) are asset references. */
        val PATH_KEYS = setOf(
            "path", "filePath", "soundFilePath", "clips", "texturePath", "controllerPath", "materialPath", "configPath",
            "defaultScene", "scenesPath", "iconPath", "projectPath",
        )

        private val MODEL_EXTENSIONS = setOf("obj", "gltf", "dae", "fbx")

        private val WINDOWS_ABSOLUTE = Regex("^[A-Za-z]:[\\\\/]")

        /** True for `C:\...`, `C:/...`, `\\server\...` and `/...`, whatever OS runs the check. */
        fun isAbsolute(path: String): Boolean =
            WINDOWS_ABSOLUTE.containsMatchIn(path) || path.startsWith("/") || path.startsWith("\\\\") || File(path).isAbsolute

        /** [path] relative to [root] with `/` separators when it lies inside [root]; otherwise unchanged. */
        fun relativize(path: String, root: File?): String {
            if (root == null || path.isBlank() || !isAbsolute(path)) return path
            val base = root.absoluteFile.normalize()
            val file = File(path).absoluteFile.normalize()
            if (!file.startsWith(base) || file == base) return path
            return file.relativeTo(base).invariantSeparatorsPath
        }

        /**
         * [path] in [root] when it is relative and that file exists there; otherwise unchanged (absolute paths,
         * engine paths relative to the working directory, and strings that are not files).
         */
        fun absolute(path: String, root: File?): String {
            if (root == null || path.isBlank() || isAbsolute(path)) return path
            val inRoot = File(root, path)
            return if (inRoot.exists()) inRoot.absoluteFile.normalize().path else path
        }

        /** Applies [map] to every string value under [PATH_KEYS] in [element], recursively. */
        fun rewrite(element: JsonElement, map: (String) -> String): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.mapValues { (key, value) ->
                if (key in PATH_KEYS) rewriteValue(value, map) else rewrite(value, map)
            })
            is JsonArray -> JsonArray(element.map { rewrite(it, map) })
            else -> element
        }

        private fun rewriteValue(value: JsonElement, map: (String) -> String): JsonElement = when {
            value is JsonPrimitive && value.isString -> JsonPrimitive(map(value.content))
            value is JsonArray -> JsonArray(value.map { rewriteValue(it, map) })
            else -> rewrite(value, map)
        }
    }
}
