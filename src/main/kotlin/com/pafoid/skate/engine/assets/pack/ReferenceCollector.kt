package com.pafoid.skate.engine.assets.pack

import com.pafoid.skate.engine.assets.AssetRegistry
import java.io.File

/**
 * Finds the project files reachable from [roots] by following string values in JSON-like asset files
 * (scenes, prefabs, materials, ...). A string counts as a reference when it is an absolute or project-relative
 * path of an existing project file, or a GUID known to [registry].
 */
class ReferenceCollector(
    private val projectDir: File,
    private val registry: AssetRegistry? = null,
    private val scanExtensions: Set<String> = setOf("scene", "prefab", "json", "mat", "material", "skateproject"),
    private val modelExtensions: Set<String> = setOf("gltf", "obj", "dae"),
    private val modelCompanions: Set<String> = setOf("bin", "mtl", "png", "jpg", "jpeg", "tga", "bmp"),
) {
    private val root = projectDir.canonicalFile
    private val stringLiteral = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")

    fun collect(roots: Collection<File>): Set<File> {
        val found = LinkedHashSet<File>()
        val queue = ArrayDeque<File>()
        fun add(file: File) {
            if (found.add(file)) queue.add(file)
        }
        roots.filter { it.isFile }.forEach { add(it.canonicalFile) }
        while (queue.isNotEmpty()) {
            val file = queue.removeFirst()
            if (file.extension.lowercase() in modelExtensions) {
                file.parentFile?.listFiles { f -> f.isFile && f.extension.lowercase() in modelCompanions }
                    ?.forEach { add(it.canonicalFile) }
            }
            if (file.extension.lowercase() !in scanExtensions) continue
            for (token in stringLiteral.findAll(file.readText()).map { unescape(it.groupValues[1]) }) {
                resolve(token)?.let { add(it) }
            }
        }
        return found
    }

    private fun resolve(token: String): File? {
        if (token.isBlank() || token.length > 512) return null
        registry?.resolve(token)?.let { rel -> File(root, rel).takeIf { it.isFile }?.let { return it.canonicalFile } }
        val direct = File(token)
        val candidates = if (direct.isAbsolute) listOf(direct) else listOf(File(root, token))
        return candidates.firstOrNull { it.isFile && it.canonicalFile.path.startsWith(root.path) }?.canonicalFile
    }

    private fun unescape(s: String) = s.replace("\\\\", "\\").replace("\\/", "/")
}
