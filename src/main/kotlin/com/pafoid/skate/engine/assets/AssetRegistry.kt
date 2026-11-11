package com.pafoid.skate.engine.assets

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps [AssetId]s to the current project-relative path of their source file (always `/`-separated).
 * The `.meta` sidecar is the source of truth: moving or renaming a file together with its sidecar keeps the GUID.
 * Engine defaults (`engine:` references, AST-26 §4.2) have a GUID derived from the reference, copied or not.
 */
class AssetRegistry(val root: File) {
    private val pathById = ConcurrentHashMap<AssetId, String>()
    private val idByPath = ConcurrentHashMap<String, AssetId>()

    val size: Int get() = pathById.size

    fun idOf(path: String): AssetId? =
        if (EngineDefaults.isReference(path)) EngineDefaults.idOf(path) else idByPath[normalize(path)]

    /** The project-relative path of [id], or the `engine:` reference for an engine default's derived GUID. */
    fun pathOf(id: AssetId): String? = pathById[id] ?: EngineDefaults.referenceOf(id)

    fun fileOf(id: AssetId): File? = pathById[id]?.let { File(root, it) }

    /** Accepts either a GUID string or a (possibly stale) path and returns the current path. */
    fun resolve(reference: String): String? {
        AssetId.parseOrNull(reference)?.let { return pathOf(it) }
        if (EngineDefaults.isReference(reference)) return EngineDefaults.logicalPath(reference)?.let { reference }
        val normalized = normalize(reference)
        return normalized.takeIf { idByPath.containsKey(it) }
    }

    /** Converts a path reference to a GUID reference, for migrating old scenes and prefabs. */
    fun toReference(path: String): String? = idOf(path)?.value

    /** Rescans [root]: creates missing sidecars, regenerates duplicated GUIDs, drops stale entries. */
    fun scan() {
        pathById.clear()
        idByPath.clear()
        root.walkTopDown()
            .onEnter { !it.name.startsWith(".") && it.name != "builds" && it.name != "Builds" }
            .filter { it.isFile && !AssetMeta.isSidecar(it) && !it.name.startsWith(".") && it.name != "builds" && it.name != "Builds" && !isToolSideFile(it) }
            .sortedBy { relativePath(it) }
            .forEach { register(it) }
    }

    /** Ensures [file] has a sidecar and is registered. Returns its id. */
    fun register(file: File): AssetId {
        val path = relativePath(file)
        var meta = AssetMeta.read(file)
        val existing = meta?.guid?.let { pathById[it] }
        if (meta == null || (existing != null && existing != path)) {
            meta = (meta ?: AssetMeta(guid = AssetId.generate(), importer = AssetMeta.importerFor(file)))
                .copy(guid = AssetId.generate())
            AssetMeta.write(file, meta)
        }
        pathById[meta.guid]?.let { idByPath.remove(it) }
        idByPath[path]?.let { pathById.remove(it) }
        pathById[meta.guid] = path
        idByPath[path] = meta.guid
        return meta.guid
    }

    /** Moves or renames a file (or directory) together with its sidecars, keeping GUIDs. */
    fun move(from: File, to: File): Boolean {
        if (!from.exists() || to.exists()) return false
        to.parentFile?.mkdirs()
        val sidecar = AssetMeta.sidecarOf(from)
        if (from.isFile && sidecar.exists() && AssetMeta.sidecarOf(to).exists()) return false
        val wasFile = from.isFile
        if (!from.renameTo(to)) return false
        if (wasFile) {
            if (sidecar.exists()) sidecar.renameTo(AssetMeta.sidecarOf(to))
            idByPath.remove(relativePath(from))?.let { id ->
                val newPath = relativePath(to)
                pathById[id] = newPath
                idByPath[newPath] = id
            }
        } else {
            scan()
        }
        return true
    }

    /** Deletes a file and its sidecar. */
    fun delete(file: File): Boolean {
        if (!file.isFile) return false
        idByPath.remove(relativePath(file))?.let { pathById.remove(it) }
        AssetMeta.sidecarOf(file).delete()
        return file.delete()
    }

    /**
     * [file]'s path relative to [root]. Falls back to canonical forms when the plain absolute paths disagree on the
     * spelling of the root (an 8.3 short name like `RUNNER~1`, a symlink or junction): callers that resolved [file]
     * canonically must still land inside the project instead of at `../../long-name/...`.
     */
    private fun relativePath(file: File): String {
        val plain = runCatching { file.absoluteFile.relativeTo(root.absoluteFile).path }.getOrNull()
        if (plain != null && !escapesRoot(plain)) return normalize(plain)
        val canonical = runCatching { file.canonicalFile.relativeTo(root.canonicalFile).path }.getOrNull()
        return normalize(canonical?.takeUnless { escapesRoot(it) } ?: plain ?: file.absolutePath)
    }

    private fun escapesRoot(relative: String): Boolean = relative == ".." || relative.startsWith("..${File.separator}")

    companion object {
        private val TOOL_SIDE_FILE = Regex("""\.blend(\d+|@)$|\.part$""", RegexOption.IGNORE_CASE)

        /** Blender backups (`x.blend1`), its in-progress save (`x.blend@`) and our `.part` temp files: never assets. */
        fun isToolSideFile(file: File): Boolean = TOOL_SIDE_FILE.containsMatchIn(file.name)
    }

    private fun normalize(path: String): String = path.replace('\\', '/').removePrefix("./").trimStart('/')
}

/** Serialisable reference to an asset by GUID; resolve the current path through [AssetRegistry]. */
@kotlinx.serialization.Serializable
@JvmInline
value class AssetHandle(val id: AssetId) {
    fun path(registry: AssetRegistry): String? = registry.pathOf(id)
}
