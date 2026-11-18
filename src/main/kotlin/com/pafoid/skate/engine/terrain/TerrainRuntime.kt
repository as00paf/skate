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
    private var overviewBuild: java.util.concurrent.CompletableFuture<TerrainOverview>? = null

    /** Far-field colour (REN-28 §6), baked by the renderer from the splat weights and layer colours; null before. */
    var macro: TerrainMacro? = null

    /** Bumped on every rebuild and on [markEdited]. */
    var version = 0L
        private set

    /** Resident chunks in (cz, cx) order. */
    val chunks: List<TerrainChunkData> get() = grid.filterNotNull()

    fun chunk(cx: Int, cz: Int): TerrainChunkData? {
        val l = layout ?: return null
        return if (l.isValidChunk(cx, cz)) grid[cz * l.chunksPerSide + cx] else null
    }

    /** Sampler over the resident chunks, falling back to the overview, else the generator (procedural), else 0. */
    val heightField: HeightField?
        get() {
            val l = layout ?: return null
            val g = generator
            return ChunkedHeightField(l, ::chunk) { x, z -> overview?.heightAt(x, z) ?: g?.heightAt(x, z) ?: 0f }
        }

    /** True when chunks stream ([putChunk] / [removeChunk] by a `TerrainStreamer`) instead of all being built. */
    var streamed = false
        private set

    /** The whole terrain at 8 m (REN-28 §15.4), built on workers for streamed terrains; null until ready. */
    @Volatile
    var overview: TerrainOverview? = null

    /** Chunk indices (`cz * chunksPerSide + cx`) never evicted: unsaved edits (REN-28 §15.7). */
    val pinned: MutableSet<Int> = java.util.Collections.synchronizedSet(HashSet())

    /** Adds a streamed-in chunk (replacing any at its position). */
    fun putChunk(chunk: TerrainChunkData) {
        val l = layout ?: return
        if (!l.isValidChunk(chunk.cx, chunk.cz)) return
        grid[chunk.cz * l.chunksPerSide + chunk.cx] = chunk
        version++
    }

    /** Removes a streamed-out chunk; returns it. */
    fun removeChunk(cx: Int, cz: Int): TerrainChunkData? {
        val l = layout ?: return null
        if (!l.isValidChunk(cx, cz)) return null
        val index = cz * l.chunksPerSide + cx
        val old = grid[index] ?: return null
        grid[index] = null
        version++
        return old
    }

    /** Rebuilds every chunk when [key] differs from the built one; returns true when it did. */
    /** Waits for the overview of a streamed terrain (tests, loading screens); null when not streamed. */
    fun awaitOverview(): TerrainOverview? = overviewBuild?.join()?.takeIf { overview === it }

    fun ensureBuilt(key: TerrainKey, parallel: Boolean = true): Boolean {
        if (key == this.key) return false
        val l = TerrainLayout(key.size, key.spacing, key.chunkQuads)
        val g = TerrainGenerator(
            key.recipe, key.stamps, l, key.heightScale, key.projectSeed, key.seedName, key.seedOffset,
            key.biomes, key.climate, key.biomeSource,
        )
        grid = arrayOfNulls(l.chunkCount)
        streamed = key.streamed
        overview = null
        if (key.streamed) {
            // Chunks arrive through a TerrainStreamer; the overview covers the rest (built on workers)
            overviewBuild?.cancel(true)
            overviewBuild = java.util.concurrent.CompletableFuture.supplyAsync { TerrainOverview.build(g, parallel = parallel) }
                .thenApply { o -> if (this.generator === g) overview = o; o }
        } else {
            for (c in TerrainChunkBuilder.buildAll(g, parallel)) grid[c.cz * l.chunksPerSide + c.cx] = c
        }
        layout = l
        generator = g
        this.key = key
        version++
        return true
    }

    /**
     * Sculpting or loaded data changed chunk heights in place: recomputes the edited chunks' splat, biomes and scatter
     * and bumps [version] (chunks carry their own dirty flag for the GPU).
     */
    fun markEdited() {
        val g = generator
        if (g != null) for (c in chunks) if (c.dirty) TerrainChunkBuilder.refreshDerived(g, c)
        version++
    }

    fun clear() {
        overviewBuild?.cancel(true); overviewBuild = null; overview = null
        key = null; generator = null; layout = null; grid = emptyArray(); streamed = false; pinned.clear(); version++
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
    val biomes: List<Biome> = emptyList(),
    val climate: BiomeClimate = BiomeClimate(),
    val biomeSource: BiomeSource = BiomeSource.PROCEDURAL,
    /** Chunks stream around focuses instead of all being built (REN-28 §15). */
    val streamed: Boolean = false,
)
