package com.pafoid.skate.engine.assets

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps [AssetId]s to the current project-relative path of their source file (always `/`-separated).
 * The `.meta` sidecar is the source of truth: moving or renaming a file together with its sidecar keeps the GUID.
 */
class AssetRegistry(val root: File) {
    private val pathById = ConcurrentHashMap<AssetId, String>()
    private val idByPath = ConcurrentHashMap<String, AssetId>()

    val size: Int get() = pathById.size

    fun idOf(path: String): AssetId? = idByPath[normalize(path)]

    fun pathOf(id: AssetId): String? = pathById[id]

    fun fileOf(id: AssetId): File? = pathOf(id)?.let { File(root, it) }

    /** Accepts either a GUID string or a (possibly stale) path and returns the current path. */
    fun resolve(reference: String): String? {
        AssetId.parseOrNull(reference)?.let { return pathOf(it) }
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
            .onEnter { !it.name.startsWith(".") }
            .filter { it.isFile && !AssetMeta.isSidecar(it) && !it.name.startsWith(".") }
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

    private fun relativePath(file: File): String =
        normalize(file.absoluteFile.relativeTo(root.absoluteFile).path)

    private fun normalize(path: String): String = path.replace('\\', '/').removePrefix("./").trimStart('/')
}

/** Serialisable reference to an asset by GUID; resolve the current path through [AssetRegistry]. */
@kotlinx.serialization.Serializable
@JvmInline
value class AssetHandle(val id: AssetId) {
    fun path(registry: AssetRegistry): String? = registry.pathOf(id)
}
