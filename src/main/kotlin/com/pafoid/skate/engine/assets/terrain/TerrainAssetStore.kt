package com.pafoid.skate.engine.assets.terrain

import com.pafoid.skate.engine.assets.AssetId
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.TerrainSource
import com.pafoid.skate.engine.terrain.TerrainChunkBuilder
import com.pafoid.skate.engine.terrain.TerrainChunkData
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.abs

/** What [TerrainAssetStore.save] wrote. */
data class TerrainSaveResult(val chunksWritten: Int, val chunksRemoved: Int, val regionsWritten: Int, val regionsDeleted: Int)

/**
 * Reads and writes one `.skterrain` folder (REN-28 §10, §15.6):
 *
 * ```
 * <name>.skterrain/            the asset (GUID in the folder's own `<name>.skterrain.meta`)
 *   terrain.json               TerrainDocument
 *   regions/r_<rx>_<rz>.bin    TerrainRegionFile: every chunk of a DATA terrain, edited chunks of a procedural one
 * ```
 *
 * Every write goes to a temp file renamed over the target, so a crash never leaves a half-written file. The far-field
 * `overview.r16` and `macro.png` belong to the streaming phase (REN-28 phase D) and are not written yet.
 */
class TerrainAssetStore(val folder: File) {
    val documentFile: File get() = File(folder, DOCUMENT)
    val regionsDir: File get() = File(folder, REGIONS_DIR)

    fun regionFile(rx: Int, rz: Int): File = File(regionsDir, regionName(rx, rz))

    fun loadDocument(): TerrainDocument = TerrainDocument.decode(documentFile.readText())

    fun saveDocument(document: TerrainDocument) = atomicWrite(documentFile, document.encode().toByteArray(Charsets.UTF_8))

    /** Chunk ([cx], [cz]) from its region record, or null (no region, no record, other grid settings). */
    fun readChunk(cx: Int, cz: Int, chunkQuads: Int, sampleSpacing: Float): TerrainRegionFile.ChunkRecord? {
        val f = regionFile(TerrainRegionFile.regionOf(cx), TerrainRegionFile.regionOf(cz))
        if (!f.isFile) return null
        return chunkFrom(f.readBytes(), cx, cz, chunkQuads, sampleSpacing)
    }

    /**
     * Saves [terrain]'s document and chunk data. A `DATA` terrain stores every resident chunk; a procedural one only
     * chunks whose heights differ from what its recipe and stamps generate (sculpted) or that carry painted biome ids
     * (AST-38), and drops records of chunks that match again. Only regions that change are rewritten; regions left empty are deleted. The terrain's
     * runtime must be built ([TerrainComponent.runtime]).
     */
    fun save(terrain: TerrainComponent): TerrainSaveResult {
        folder.mkdirs()
        saveDocument(TerrainDocument.of(terrain))
        val runtime = terrain.runtime
        val layout = runtime.layout ?: return TerrainSaveResult(0, 0, 0, 0)
        val generator = runtime.generator
        val keep = HashMap<Pair<Int, Int>, TerrainChunkData>()
        val drop = HashSet<Pair<Int, Int>>()
        for (chunk in runtime.chunks) {
            val edited = terrain.source == TerrainSource.DATA || generator == null || chunk.paintedBiomeIds != null ||
                differs(chunk.heights, TerrainChunkBuilder.build(generator, chunk.cx, chunk.cz).heights)
            if (edited) keep[chunk.cx to chunk.cz] = chunk else drop += chunk.cx to chunk.cz
        }
        return writeChunks(keep.values, drop, layout.chunkQuads, layout.spacing)
    }

    /** Writes [chunks] and removes the records of [remove], region by region (merging with what is on disk). */
    fun writeChunks(chunks: Collection<TerrainChunkData>, remove: Set<Pair<Int, Int>>, chunkQuads: Int, sampleSpacing: Float): TerrainSaveResult {
        val byRegion = chunks.groupBy { TerrainRegionFile.regionOf(it.cx) to TerrainRegionFile.regionOf(it.cz) }
        val removeByRegion = remove.groupBy { (cx, cz) -> TerrainRegionFile.regionOf(cx) to TerrainRegionFile.regionOf(cz) }
        var written = 0; var removed = 0; var regionsWritten = 0; var regionsDeleted = 0
        for (region in byRegion.keys + removeByRegion.keys) {
            val file = regionFile(region.first, region.second)
            val existing = if (file.isFile) runCatching { TerrainRegionFile.readAll(file.readBytes()) }.getOrNull() else null
            // Records saved with another grid can't be merged: the region is rewritten from scratch.
            val records = existing?.takeIf { it.first.chunkQuads == chunkQuads && it.first.sampleSpacing == sampleSpacing }
                ?.second?.toMutableMap() ?: mutableMapOf()
            val before = records.toMap()
            removeByRegion[region].orEmpty().forEach { (cx, cz) -> if (records.remove(TerrainRegionFile.slotOf(cx, cz)) != null) removed++ }
            byRegion[region].orEmpty().forEach { c ->
                val own = (chunkQuads + 1) * (chunkQuads + 1)
                records[TerrainRegionFile.slotOf(c.cx, c.cz)] = TerrainRegionFile.record(c.heights, c.paintedBiomeIds?.takeIf { it.size == own })
                written++
            }
            when {
                records.isEmpty() -> if (file.delete()) regionsDeleted++
                existing != null && records == before -> Unit
                else -> {
                    atomicWrite(file, TerrainRegionFile.encodeRecords(chunkQuads, sampleSpacing, records))
                    regionsWritten++
                }
            }
        }
        return TerrainSaveResult(written, removed, regionsWritten, regionsDeleted)
    }

    /** The folder's GUID, creating its `.meta` sidecar on first use. */
    fun ensureGuid(): AssetId {
        AssetMeta.read(folder)?.let { return it.guid }
        val id = AssetId.generate()
        AssetMeta.write(folder, AssetMeta(guid = id, importer = IMPORTER))
        return id
    }

    companion object {
        const val EXTENSION = "skterrain"
        const val DOCUMENT = "terrain.json"
        const val REGIONS_DIR = "regions"
        const val IMPORTER = "terrain"

        /** Height difference (m) under which a procedural chunk counts as unedited (region quantisation is coarser). */
        const val EDIT_EPSILON = 1e-4f

        fun regionName(rx: Int, rz: Int): String = "r_${rx}_${rz}.bin"

        /** `regions/r_<rx>_<rz>.bin` for chunk ([cx], [cz]), relative to the asset folder. */
        fun regionPath(cx: Int, cz: Int): String =
            "$REGIONS_DIR/" + regionName(TerrainRegionFile.regionOf(cx), TerrainRegionFile.regionOf(cz))

        fun isAssetFolder(file: File): Boolean = file.isDirectory && file.name.endsWith(".$EXTENSION")

        /** The chunk's data in region [bytes], or null when absent or saved with other grid settings. */
        fun chunkFrom(bytes: ByteArray, cx: Int, cz: Int, chunkQuads: Int, sampleSpacing: Float): TerrainRegionFile.ChunkRecord? {
            val header = TerrainRegionFile.readHeader(bytes)
            if (header.chunkQuads != chunkQuads || header.sampleSpacing != sampleSpacing) return null
            val slot = TerrainRegionFile.slotOf(cx, cz)
            val raw = TerrainRegionFile.readRecord(bytes, header, slot) ?: return null
            return TerrainRegionFile.decodeChunk(TerrainRegionFile.Record(raw, header.entries[slot].flags), chunkQuads)
        }

        /**
         * Creates `<dir>/<name>.skterrain` from [terrain] (document, chunk data, GUID) and points [terrain] at it.
         * Returns the folder.
         */
        fun create(dir: File, name: String, terrain: TerrainComponent): File {
            val folder = File(dir, "$name.$EXTENSION")
            val store = TerrainAssetStore(folder)
            store.save(terrain)
            terrain.asset = store.ensureGuid().value
            return folder
        }

        private fun differs(a: FloatArray, b: FloatArray): Boolean =
            a.size != b.size || a.indices.any { abs(a[it] - b[it]) > EDIT_EPSILON }

        fun atomicWrite(target: File, bytes: ByteArray) {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeBytes(bytes)
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
