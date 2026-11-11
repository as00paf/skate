package com.pafoid.skate.engine.assets.pack

import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.loaders.GltfReferences
import java.io.File

/**
 * Finds the project files reachable from [roots] by following string values in JSON-like asset files
 * (scenes, prefabs, materials and `.skmat` material assets, ...). A string counts as a reference when it is an absolute or project-relative
 * path of an existing project file, or a GUID known to [registry].
 *
 * `engine:` references (AST-26 §4.6) are collected separately in [engineReferences] when [engineDefaults] resolves
 * them (the project's override or the engine's file); the packer stores them under the reference itself.
 *
 * A model brings the files it names (AST-26 §3.1), not every image in its folder: a `.gltf` its URIs, an `.obj` its
 * `mtllib` and the `.mtl`'s `map_*` images, a `.dae` its `init_from` images; a `.glb` is self-contained.
 */
class ReferenceCollector(
    private val projectDir: File,
    private val registry: AssetRegistry? = null,
    private val scanExtensions: Set<String> = setOf("scene", "prefab", "json", "mat", "material", "skmat", "skateproject"),
    private val engineDefaults: EngineDefaultResolver? = null,
) {
    private val root = projectDir.canonicalFile
    private val stringLiteral = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")

    /** `engine:` references reached by the last [collect], in discovery order. */
    val engineReferences = LinkedHashSet<String>()

    fun collect(roots: Collection<File>): Set<File> {
        engineReferences.clear()
        val found = LinkedHashSet<File>()
        val queue = ArrayDeque<File>()
        fun add(file: File) {
            if (found.add(file)) queue.add(file)
        }
        roots.filter { it.isFile }.forEach { add(it.canonicalFile) }
        while (queue.isNotEmpty()) {
            val file = queue.removeFirst()
            companions(file).forEach { name ->
                resolveRelative(file, name)?.takeIf { it.isFile && it.canonicalFile.path.startsWith(root.path) }?.let { add(it.canonicalFile) }
            }
            if (file.extension.lowercase() !in scanExtensions) continue
            for (token in stringLiteral.findAll(file.readText()).map { unescape(it.groupValues[1]) }) {
                if (EngineDefaults.isReference(token)) addEngine(token) else resolve(token)?.let { add(it) }
            }
        }
        return found
    }

    /** Records [reference] and the engine files its model names (`.mtl`, maps), when they resolve. */
    private fun addEngine(reference: String) {
        val resolver = engineDefaults ?: return
        val logical = EngineDefaults.logicalPath(reference) ?: return
        if (reference in engineReferences) return
        val file = resolver.resolve(reference) ?: return
        engineReferences += reference
        val dir = logical.substringBeforeLast('/', "")
        companions(file).forEach { name ->
            val companion = normalizeLogical(if (dir.isEmpty()) name else "$dir/$name") ?: return@forEach
            addEngine(EngineDefaults.reference(companion))
        }
    }

    /** Relative names of the files [file] loads with it (see the class doc). */
    private fun companions(file: File): List<String> = when (file.extension.lowercase()) {
        "gltf" -> GltfReferences.uris(file.readText())
        "obj" -> OBJ_MTLLIB.findAll(file.readText()).flatMap { m ->
            val mtl = m.groupValues[1].trim()
            listOf(mtl) + resolveRelative(file, mtl)?.takeIf { it.isFile }?.let { materialMaps(it, mtl) }.orEmpty()
        }.toList()
        "mtl" -> materialMaps(file, file.name)
        "dae" -> DAE_IMAGE.findAll(file.readText()).map { it.groupValues[1].trim().removePrefix("file://").removePrefix("./") }
            .filter { !it.contains("://") }.toList()
        else -> emptyList()
    }

    /** The `map_*` / `bump` images of [mtl] (named [mtlName] relative to its model), relative to the model. */
    private fun materialMaps(mtl: File, mtlName: String): List<String> {
        val prefix = mtlName.replace('\\', '/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        return MTL_MAP.findAll(mtl.readText()).map { prefix + it.groupValues[1].trim().split(Regex("\\s+")).last() }.toList()
    }

    private fun resolveRelative(model: File, name: String): File? {
        if (name.isBlank() || name.contains(':')) return null
        return File(model.parentFile, name.replace('\\', '/'))
    }

    private fun resolve(token: String): File? {
        if (token.isBlank() || token.length > 512 || token.contains("://")) return null
        registry?.resolve(token)?.takeUnless(EngineDefaults::isReference)?.let { rel ->
            File(root, rel).takeIf { it.isFile }?.let { return it.canonicalFile }
        }
        // A colon only appears in absolute Windows paths; anything else would make File probe an NTFS stream.
        if (token.indexOf(':') > 1) return null
        val direct = File(token)
        val candidates = if (direct.isAbsolute) listOf(direct) else listOf(File(root, token))
        return candidates.firstOrNull { it.isFile && it.canonicalFile.path.startsWith(root.path) }?.canonicalFile
    }

    private fun unescape(s: String) = s.replace("\\\\", "\\").replace("\\/", "/")

    private companion object {
        val OBJ_MTLLIB = Regex("""(?m)^\s*mtllib\s+(.+)$""")
        val MTL_MAP = Regex("""(?m)^\s*(?:map_\w+|bump|disp|decal)\s+(.+)$""")
        val DAE_IMAGE = Regex("""<init_from>\s*([^<]+?)\s*</init_from>""")

        /** [path] with `.` and `..` segments folded; null when it leaves the engine assets folder. */
        fun normalizeLogical(path: String): String? {
            val out = ArrayList<String>()
            for (part in path.replace('\\', '/').split('/')) {
                when (part) {
                    "", "." -> Unit
                    ".." -> if (out.isEmpty()) return null else out.removeAt(out.size - 1)
                    else -> out += part
                }
            }
            return out.joinToString("/").takeIf { it.isNotEmpty() }
        }
    }
}
