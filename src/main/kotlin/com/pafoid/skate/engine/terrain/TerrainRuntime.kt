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
    @Volatile var macro: TerrainMacro? = null

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

    /**
     * Remembered scatter state (REN-28 §15.10): destroyed, taken, moved and changed items, kept across chunk unloads
     * and saved with the game. Cleared when the world changes (a different key).
     */
    val scatterState = com.pafoid.skate.engine.terrain.scatter.ScatterStateStore()

    /** Game time for scatter records (respawn timers), advanced by `TerrainSystem` while the scene runs. */
    var stateTime = 0f

    /**
     * Chunks in some focus's physics ring (REN-28 §15.1), by index; null when every chunk counts (not streamed).
     * Published by the streamer each update; colliders and scatter entities follow it (PHY-28, ECS).
     */
    @Volatile
    var physicsRing: BooleanArray? = null

    /** Per chunk index: its collider is in the physics world (set by PHY-28); bodies over a chunk without one freeze. */
    @Volatile
    var colliderPresent: BooleanArray = BooleanArray(0)

    /** True when chunk ([cx], [cz]) is in the physics ring (always, when not streamed). */
    fun isPhysicsChunk(cx: Int, cz: Int): Boolean {
        val l = layout ?: return false
        if (!l.isValidChunk(cx, cz)) return false
        return physicsRing?.get(cz * l.chunksPerSide + cx) ?: true
    }

    /** Chunk indices edited (sculpted, painted) since the terrain was built: sparse edits (REN-28 §15.2). */
    val edited: MutableSet<Int> = java.util.Collections.synchronizedSet(HashSet())

    /** True when the terrain has edits a reroll would leave behind (the editor warns, REN-28 §5.1). */
    val hasEdits: Boolean get() = edited.isNotEmpty()

    /** Bumped when [overview] changes in place (edited chunks refreshed into it), so far tiles and the macro redo. */
    var overviewVersion = 0L
        private set

    /**
     * Applies [scatterState] to a chunk that just became resident: drops records whose items no longer match the
     * placement (a patch changed a rule or the ground), and lets expired `DESTROYED` / `TAKEN` items respawn.
     */
    fun applyScatterState(chunk: TerrainChunkData) {
        val g = generator ?: return
        val store = scatterState
        if (store.delta(chunk.cx, chunk.cz) == null) return
        val entries = g.scatterEntries
        val fingerprints = HashMap<String, Long>()
        for (e in entries) fingerprints[e.rule.id] = com.pafoid.skate.engine.terrain.scatter.ScatterFingerprint.of(g.projectSeed, g.seedName, g.seedOffset, e.rule, chunk)
        store.validate(chunk.cx, chunk.cz, fingerprints) { ruleId, ordinal ->
            chunk.scatter.firstOrNull { entries.getOrNull(it.rule)?.rule?.id == ruleId && it.key == ordinal }?.let { floatArrayOf(it.x, it.z) }
        }
        store.expire(chunk.cx, chunk.cz, stateTime) { ruleId -> entries.firstOrNull { it.rule.id == ruleId }?.rule?.respawnAfterSeconds ?: 0f }
    }

    /** Adds a streamed-in chunk (replacing any at its position). */
    fun putChunk(chunk: TerrainChunkData) {
        val l = layout ?: return
        if (!l.isValidChunk(chunk.cx, chunk.cz)) return
        grid[chunk.cz * l.chunksPerSide + chunk.cx] = chunk
        applyScatterState(chunk)
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

    /** Waits for the overview of a streamed terrain (tests, loading screens); null when not streamed. */
    fun awaitOverview(): TerrainOverview? = overviewBuild?.join()?.takeIf { overview === it }

    /** Rebuilds every chunk when [key] differs from the built one; returns true when it did. */
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
        physicsRing = null
        colliderPresent = BooleanArray(l.chunkCount)
        edited.clear(); pinned.clear()
        // A different world: remembered items no longer exist (runtime reseed, recipe or rule edit in Play)
        val previous = this.key
        if (previous != null && worldOf(previous) != worldOf(key)) scatterState.clear()
        layout = l
        generator = g
        this.key = key
        if (key.streamed) {
            // Chunks arrive through a TerrainStreamer; the overview covers the rest (built on workers)
            overviewBuild?.cancel(true)
            overviewBuild = java.util.concurrent.CompletableFuture.supplyAsync { TerrainOverview.build(g, parallel = parallel) }
                .thenApply { o -> if (this.generator === g) overview = o; o }
        } else {
            for (c in TerrainChunkBuilder.buildAll(g, parallel)) grid[c.cz * l.chunksPerSide + c.cx] = c
            for (c in grid) if (c != null) applyScatterState(c)
        }
        version++
        return true
    }

    /**
     * Sculpting or loaded data changed chunk heights in place: recomputes the edited chunks' splat, biomes and scatter
     * and bumps [version] (chunks carry their own dirty flag for the GPU).
     */
    fun markEdited() {
        val g = generator
        val l = layout
        val o = overview
        for (c in chunks) if (c.dirty) {
            if (g != null) TerrainChunkBuilder.refreshDerived(g, c)
            if (l != null) edited += c.cz * l.chunksPerSide + c.cx
            // Sparse edits keep the overview (far field, far queries) in step, incrementally (REN-28 §15.7)
            o?.refresh(c)
        }
        if (o != null) overviewVersion++
        version++
    }

    /**
     * Throws the edits away (the reroll warning's "discard"): rebuilds from the recipe on the next `ensureBuilt`.
     * Saved region records are the asset's business (AST); this only drops the in-memory edits.
     */
    fun discardEdits() {
        edited.clear(); pinned.clear()
        key = null
        version++
    }

    /** Only what makes the world itself (not streaming or rendering settings). */
    private fun worldOf(k: TerrainKey) = k.copy(streamed = false)

    fun clear() {
        overviewBuild?.cancel(true); overviewBuild = null; overview = null
        key = null; generator = null; layout = null; grid = emptyArray(); streamed = false; pinned.clear(); edited.clear()
        physicsRing = null
        version++
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
