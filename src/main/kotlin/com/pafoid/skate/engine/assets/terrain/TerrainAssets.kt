package com.pafoid.skate.engine.assets.terrain

import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.assets.pack.PackedGuids
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.terrain.TerrainChunkBuilder
import com.pafoid.skate.engine.terrain.TerrainRuntime
import java.io.File
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Where `.skterrain` assets are read from: a reference (the asset GUID in `TerrainComponent.asset`) and a path inside
 * the asset folder (`terrain.json`, `regions/…`). [folder] is the writable folder in the editor (watched for hot
 * reload); null in an exported game, which reads from the pack.
 */
interface TerrainAssetSource {
    fun read(reference: String, relativePath: String): ByteArray?
    fun folder(reference: String): File? = null

    companion object {
        val NONE = object : TerrainAssetSource {
            override fun read(reference: String, relativePath: String): ByteArray? = null
        }

        /** The editor: GUIDs resolve through the project's [registry] to folders under its root. */
        fun forRegistry(registry: AssetRegistry): TerrainAssetSource = object : TerrainAssetSource {
            @Volatile private var scanned = false

            override fun folder(reference: String): File? {
                val path = registry.resolve(reference) ?: if (!scanned) {
                    scanned = true // scanned once on the first unknown GUID (a new terrain registers itself on create)
                    registry.scan()
                    registry.resolve(reference)
                } else null
                return path?.let { File(registry.root, it) }?.takeIf { it.isDirectory }
            }

            override fun read(reference: String, relativePath: String): ByteArray? =
                folder(reference)?.let { File(it, relativePath) }?.takeIf { it.isFile }?.readBytes()
        }

        /** An exported game: GUIDs resolve through the pack's GUID table; files are pack entries under the folder path. */
        fun forPack(paths: AssetPaths): TerrainAssetSource = object : TerrainAssetSource {
            private val table by lazy { PackedGuids.read(paths) }
            override fun read(reference: String, relativePath: String): ByteArray? {
                val folder = PackedGuids.resolve(table, reference) ?: return null
                return paths.readBytes("${folder.trimEnd('/')}/$relativePath")
            }
        }
    }
}

/**
 * Process-wide `.skterrain` access (AST-36, REN-28 §10), like `CollisionMeshes`: the host sets [source] (the editor
 * from the open project's registry, an exported game from its pack). Documents and region files are cached until
 * [invalidate]; [version] of an asset grows on every change so terrains reapply it.
 *
 * Hot reload: [poll] (called by `TerrainSystem` every frame, throttled) watches each used asset's folder in the
 * editor: a changed `terrain.json` reapplies the document (rebuilding the chunks), a changed, new or deleted region
 * file reloads the resident chunks. A change counts once it is stable for two polls, so half-written files are skipped.
 */
object TerrainAssets {
    @Volatile
    var source: TerrainAssetSource = TerrainAssetSource.NONE
        set(value) {
            field = value
            clear()
        }

    /** Milliseconds between hot-reload checks. */
    var pollIntervalMs: Long = 500
    var clock: () -> Long = System::currentTimeMillis

    private val documents = ConcurrentHashMap<String, Result<TerrainDocument>>()
    private val regions = ConcurrentHashMap<String, Result<ByteArray?>>()
    private val versions = ConcurrentHashMap<String, Long>()
    private val watched = ConcurrentHashMap<String, Watch>()
    private val applied = WeakHashMap<TerrainRuntime, Pair<String, Long>>()
    private var lastPoll = Long.MIN_VALUE

    private class Watch(var stamp: Map<String, Pair<Long, Long>>, var pending: Map<String, Pair<Long, Long>>? = null)

    fun version(reference: String): Long = versions[reference] ?: 0L

    /** The asset's document, or null (unknown GUID, missing or unreadable `terrain.json`). */
    fun document(reference: String): TerrainDocument? {
        watch(reference)
        return documents.getOrPut(reference) {
            runCatching {
                val bytes = source.read(reference, TerrainAssetStore.DOCUMENT) ?: error("no ${TerrainAssetStore.DOCUMENT}")
                TerrainDocument.decode(bytes.toString(Charsets.UTF_8))
            }
        }.getOrNull()
    }

    /** Chunk ([cx], [cz]) from the asset's region files (heights, painted biomes), or null when it has none. */
    fun chunk(reference: String, cx: Int, cz: Int, chunkQuads: Int, sampleSpacing: Float): TerrainRegionFile.ChunkRecord? {
        val path = TerrainAssetStore.regionPath(cx, cz)
        val bytes = regions.getOrPut("$reference|$path") { runCatching { source.read(reference, path) } }.getOrNull() ?: return null
        return runCatching { TerrainAssetStore.chunkFrom(bytes, cx, cz, chunkQuads, sampleSpacing) }.getOrNull()
    }

    /** Forgets cached data of [reference] and bumps its version (hot reload, after a save elsewhere). */
    fun invalidate(reference: String) {
        documents.remove(reference)
        regions.keys.removeIf { it.startsWith("$reference|") }
        versions.merge(reference, 1L, Long::plus)
    }

    fun clear() {
        documents.clear(); regions.clear(); watched.clear()
        synchronized(applied) { applied.clear() }
        versions.replaceAll { _, v -> v + 1 }
    }

    /**
     * Saves [terrain] into its asset folder (editor; scene save and sculpt tools call this) and refreshes the cache
     * so the saved state is not reloaded over it. Null when the asset has no writable folder.
     */
    fun save(terrain: TerrainComponent): TerrainSaveResult? {
        val ref = terrain.asset.takeIf { it.isNotBlank() } ?: return null
        val folder = source.folder(ref) ?: return null
        val result = TerrainAssetStore(folder).save(terrain)
        documents.remove(ref)
        regions.keys.removeIf { it.startsWith("$ref|") }
        watched[ref]?.let { it.stamp = stamp(folder); it.pending = null }
        // The runtime already holds what was saved: mark it applied at the current version.
        synchronized(applied) { applied[terrain.runtime] = ref to version(ref) }
        return result
    }

    /**
     * Brings [terrain] in line with its asset (called by `TerrainSystem` before and after building): [beforeBuild]
     * copies a new document version into the component so its key reflects it.
     */
    fun beforeBuild(terrain: TerrainComponent) {
        val ref = terrain.asset.takeIf { it.isNotBlank() } ?: return
        val v = version(ref)
        val last = synchronized(applied) { applied[terrain.runtime] }
        if (last != null && last.first == ref && last.second == v) return
        document(ref)?.applyTo(terrain)
    }

    /**
     * After the runtime is built: loads region chunks when the runtime was [rebuilt] or the asset changed. Chunks
     * without a record keep the generated heights (procedural) or stay flat (`DATA` without data yet). Returns the
     * number of chunks loaded.
     */
    fun afterBuild(terrain: TerrainComponent, rebuilt: Boolean): Int {
        val ref = terrain.asset.takeIf { it.isNotBlank() } ?: return 0
        val runtime = terrain.runtime
        val v = version(ref)
        val last = synchronized(applied) { applied[runtime] }
        if (!rebuilt && last != null && last.first == ref && last.second == v) return 0
        val layout = runtime.layout ?: return 0
        val generator = runtime.generator
        var loaded = 0
        for (chunk in runtime.chunks) {
            val record = chunk(ref, chunk.cx, chunk.cz, layout.chunkQuads, layout.spacing)
            val changed = when {
                record != null && record.heights.size == chunk.heights.size -> {
                    record.heights.copyInto(chunk.heights)
                    chunk.paintedBiomeIds = record.paintedBiomeIds
                    loaded++
                    true
                }
                // A record removed since the last load (hot reload): back to the generated heights, nothing painted.
                !rebuilt && last != null && generator != null -> {
                    TerrainChunkBuilder.build(generator, chunk.cx, chunk.cz).heights.copyInto(chunk.heights)
                    chunk.paintedBiomeIds = null
                    true
                }
                else -> false
            }
            if (changed) {
                // Splat, biome ids and scatter follow the loaded heights and painted biomes (AST-38).
                generator?.let { TerrainChunkBuilder.refreshDerived(it, chunk) }
                chunk.dirty = true
            }
        }
        runtime.markEdited()
        synchronized(applied) { applied[runtime] = ref to v }
        return loaded
    }

    /** Checks watched asset folders for changes (editor hot reload); returns the references invalidated. */
    fun poll(): List<String> {
        val now = clock()
        if (lastPoll != Long.MIN_VALUE && now - lastPoll < pollIntervalMs) return emptyList()
        lastPoll = now
        val changed = ArrayList<String>()
        for ((ref, w) in watched) {
            val folder = source.folder(ref) ?: continue
            val current = stamp(folder)
            when {
                current == w.stamp -> w.pending = null
                w.pending == current -> { w.stamp = current; w.pending = null; invalidate(ref); changed += ref }
                else -> w.pending = current
            }
        }
        return changed
    }

    /** Forces the next [poll] to check now (tests, editor focus regained). */
    fun pollNow(): List<String> {
        lastPoll = Long.MIN_VALUE
        return poll()
    }

    private fun watch(reference: String) {
        if (watched.containsKey(reference)) return
        val folder = source.folder(reference) ?: return
        watched[reference] = Watch(stamp(folder))
    }

    private fun stamp(folder: File): Map<String, Pair<Long, Long>> {
        val files = listOf(File(folder, TerrainAssetStore.DOCUMENT)) +
            File(folder, TerrainAssetStore.REGIONS_DIR).listFiles { f -> f.isFile && f.name.endsWith(".bin") }.orEmpty()
        return files.filter { it.isFile }.associate { it.name to (it.lastModified() to it.length()) }
    }
}
