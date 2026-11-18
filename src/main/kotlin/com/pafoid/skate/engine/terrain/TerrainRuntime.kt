package com.pafoid.skate.engine.terrain

/**
 * Non-serialized state of one terrain (REN-28 §3.2): its generator, resident chunks and [HeightField]. [ensureBuilt]
 * rebuilds when anything the heights depend on changed ([TerrainKey]); without streaming (REN-28 phase D) every chunk
 * is resident. [version] increases on every rebuild or edit, so meshes, colliders and scatter know to refresh.
 */
class TerrainRuntime {
    var key: TerrainKey? = null
        private set
    var generator: TerrainGenerator? = null
        private set
    var layout: TerrainLayout? = null
        private set
    private var grid: Array<TerrainChunkData?> = emptyArray()

    /** Bumped on every rebuild and on [markEdited]. */
    var version = 0L
        private set

    /** Resident chunks in (cz, cx) order. */
    val chunks: List<TerrainChunkData> get() = grid.filterNotNull()

    fun chunk(cx: Int, cz: Int): TerrainChunkData? {
        val l = layout ?: return null
        return if (l.isValidChunk(cx, cz)) grid[cz * l.chunksPerSide + cx] else null
    }

    /** Sampler over the resident chunks, falling back to the generator (procedural) or 0. */
    val heightField: HeightField?
        get() {
            val l = layout ?: return null
            val g = generator
            return ChunkedHeightField(l, ::chunk) { x, z -> g?.heightAt(x, z) ?: 0f }
        }

    /** Rebuilds every chunk when [key] differs from the built one; returns true when it did. */
    fun ensureBuilt(key: TerrainKey, parallel: Boolean = true): Boolean {
        if (key == this.key) return false
        val l = TerrainLayout(key.size, key.spacing, key.chunkQuads)
        val g = TerrainGenerator(key.recipe, key.stamps, l, key.heightScale, key.projectSeed, key.seedName, key.seedOffset)
        val built = TerrainChunkBuilder.buildAll(g, parallel)
        grid = arrayOfNulls(l.chunkCount)
        for (c in built) grid[c.cz * l.chunksPerSide + c.cx] = c
        layout = l
        generator = g
        this.key = key
        version++
        return true
    }

    /** Sculpting or loaded data changed chunk heights in place: bump [version] (chunks carry their own dirty flag). */
    fun markEdited() { version++ }

    fun clear() {
        key = null; generator = null; layout = null; grid = emptyArray(); version++
    }
}

/** Everything a terrain's heights depend on; a different key means a rebuild. */
data class TerrainKey(
    val size: Float,
    val spacing: Float,
    val chunkQuads: Int,
    val heightScale: Float,
    val recipe: TerrainRecipe,
    val stamps: List<TerrainStamp>,
    val projectSeed: Long,
    val seedName: String,
    val seedOffset: Int,
)
