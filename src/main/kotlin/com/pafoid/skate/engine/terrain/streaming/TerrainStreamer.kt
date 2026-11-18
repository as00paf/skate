package com.pafoid.skate.engine.terrain.streaming

import com.pafoid.skate.engine.terrain.TerrainChunkData
import com.pafoid.skate.engine.terrain.TerrainGenerator
import com.pafoid.skate.engine.terrain.TerrainKey
import com.pafoid.skate.engine.terrain.TerrainRuntime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import kotlin.math.PI

/** Builds one chunk on a worker: the stored record if any, else the recipe (REN-28 §15.2). */
fun interface ChunkSource {
    fun load(generator: TerrainGenerator, cx: Int, cz: Int): TerrainChunkData
}

/**
 * Streams one terrain's chunks (REN-28 §15.3) into its [TerrainRuntime], which every consumer (renderer, colliders,
 * assets) already follows through `runtime.chunks` and `runtime.version`.
 *
 * Chunk states: `UNLOADED → LOADING` (worker, through [executor]) `→ RESIDENT` (in the runtime) `→ UNLOADED`. A
 * chunk that leaves every keep radius while loading is cancelled. Evicted chunks stay in a CPU **LRU cache** up to
 * [cacheBytes], so turning around doesn't regenerate them. At most [maxInFlight] loads run at once and
 * [maxEvictionsPerUpdate] chunks leave per update. Pure (no GL): the headless server streams physics with it too.
 */
class TerrainStreamer(
    val runtime: TerrainRuntime,
    private val executor: Executor = ForkJoinPool.commonPool(),
    private val source: ChunkSource = ChunkSource { g, cx, cz -> com.pafoid.skate.engine.terrain.TerrainChunkBuilder.build(g, cx, cz) },
    var cacheBytes: Long = DEFAULT_CACHE_BYTES,
    var maxInFlight: Int = DEFAULT_MAX_IN_FLIGHT,
    var maxEvictionsPerUpdate: Int = 8,
) {
    private var key: TerrainKey? = null
    private var planner: ChunkStreamingPlanner? = null
    private var resident = BooleanArray(0)
    private var inFlight = BooleanArray(0)
    private var pinnedFlags = BooleanArray(0)
    private val futures = HashMap<Int, CompletableFuture<TerrainChunkData>>()
    private val done = ConcurrentLinkedQueue<Pair<TerrainKey, TerrainChunkData>>()
    private val cache = object : LinkedHashMap<Int, TerrainChunkData>(16, 0.75f, true) {}
    private var cachedBytes = 0L
    private val prefetches = ArrayList<Prefetch>()
    private val focusScratch = ArrayList<Focus>()

    private class Prefetch(val focus: Focus, val rings: StreamingRings, val future: CompletableFuture<Unit>)

    /** Stats of the last [update]. */
    var queued = 0
        private set
    var loading = 0
        private set
    var residentCount = 0
        private set
    var cancelled = 0L
        private set
    var cacheHits = 0L
        private set

    val residentBytes: Long get() = runtime.chunks.sumOf { it.approxBytes() }

    /**
     * Advances streaming: takes finished loads, plans with [focuses] (the first [focusCount], plus pending prefetches)
     * and [rings], starts loads, cancels loads that left every keep radius, evicts into the cache.
     */
    fun update(focuses: List<Focus>, focusCount: Int, rings: StreamingRings) {
        val layout = runtime.layout ?: return
        val generator = runtime.generator ?: return
        val currentKey = runtime.key ?: return
        if (currentKey != key || planner?.layout != layout) reset(currentKey, layout)
        val plan = planner!!
        val n = layout.chunksPerSide

        // Finished loads (stale ones from an older key are dropped)
        while (true) {
            val (k, chunk) = done.poll() ?: break
            val index = chunk.cz * n + chunk.cx
            futures.remove(index)
            inFlight[index] = false
            if (k != key) continue
            if (plan.keep[index] || !plan.wanted.any()) put(chunk) else remember(chunk)
        }

        // Plan with the focuses and prefetch points
        focusScratch.clear()
        for (i in 0 until focusCount) focusScratch += focuses[i]
        for (p in prefetches) focusScratch += p.focus
        for (k in pinnedFlags.indices) pinnedFlags[k] = k in runtime.pinned
        plan.plan(focusScratch, focusScratch.size, rings, resident, inFlight, pinnedFlags)
        // Colliders and scatter entities follow the physics ring (REN-28 §15.1, PHY-28)
        runtime.physicsRing = plan.physicsWanted

        // Cancel loads nobody wants any more
        val it = futures.entries.iterator()
        while (it.hasNext()) {
            val (index, future) = it.next()
            if (!plan.keep[index]) { future.cancel(true); inFlight[index] = false; it.remove(); cancelled++ }
        }

        // Start loads, best first; cache hits are instant
        var started = futures.size
        for (i in 0 until plan.loadCount) {
            val index = plan.loads[i]
            val cachedChunk = cache.remove(index)
            if (cachedChunk != null) { cachedBytes -= cachedChunk.approxBytes(); cacheHits++; put(cachedChunk); continue }
            if (started >= maxInFlight) continue
            val cx = index % n; val cz = index / n
            val forKey = currentKey
            inFlight[index] = true
            futures[index] = CompletableFuture.supplyAsync({ source.load(generator, cx, cz) }, executor).also { f ->
                f.thenAccept { chunk -> done.add(forKey to chunk) }
            }
            started++
        }

        // Evict the farthest chunks outside every keep radius
        for (i in 0 until minOf(plan.evictionCount, maxEvictionsPerUpdate)) {
            val index = plan.evictions[i]
            runtime.removeChunk(index % n, index / n)?.let(::remember)
            resident[index] = false
        }

        completePrefetches(plan, n)
        queued = plan.loadCount
        loading = futures.size
        residentCount = resident.count { it }
    }

    /**
     * Completes when every chunk within [rings] (detail, or physics with [physicsOnly]) of local ([x], [z]) is
     * resident (REN-28 §15.3: spawns, respawns, teleports, the editor's "Focus on selected").
     */
    fun prefetch(x: Float, z: Float, rings: StreamingRings, physicsOnly: Boolean = false): CompletableFuture<Unit> {
        val r = if (physicsOnly) rings.copy(detail = rings.physics) else rings
        val f = CompletableFuture<Unit>()
        prefetches += Prefetch(Focus().set(x, z), r, f)
        return f
    }

    private fun completePrefetches(plan: ChunkStreamingPlanner, n: Int) {
        val it = prefetches.iterator()
        while (it.hasNext()) {
            val p = it.next()
            var ready = true
            for (k in resident.indices) {
                if (plan.distance(k % n, k / n, p.focus.x, p.focus.z) <= p.rings.detail && !resident[k]) { ready = false; break }
            }
            if (ready) { p.future.complete(Unit); it.remove() }
        }
    }

    private fun put(chunk: TerrainChunkData) {
        val n = runtime.layout?.chunksPerSide ?: return
        runtime.putChunk(chunk)
        resident[chunk.cz * n + chunk.cx] = true
    }

    private fun remember(chunk: TerrainChunkData) {
        val n = runtime.layout?.chunksPerSide ?: return
        val index = chunk.cz * n + chunk.cx
        cache.remove(index)?.let { cachedBytes -= it.approxBytes() }
        cache[index] = chunk
        cachedBytes += chunk.approxBytes()
        val it = cache.entries.iterator()
        while (cachedBytes > cacheBytes && it.hasNext()) { cachedBytes -= it.next().value.approxBytes(); it.remove() }
    }

    val cacheSize: Int get() = cache.size

    /** A new key (rebuild, reroll) or layout: drop everything streamed for the old one. */
    private fun reset(newKey: TerrainKey, layout: com.pafoid.skate.engine.terrain.TerrainLayout) {
        futures.values.forEach { it.cancel(true) }
        futures.clear()
        done.clear()
        cache.clear(); cachedBytes = 0
        key = newKey
        planner = ChunkStreamingPlanner(layout)
        resident = BooleanArray(layout.chunkCount)
        inFlight = BooleanArray(layout.chunkCount)
        pinnedFlags = BooleanArray(layout.chunkCount)
        for (c in runtime.chunks) resident[c.cz * layout.chunksPerSide + c.cx] = true
    }

    /** Cancels every load (terrain removed or disabled). */
    fun close() {
        futures.values.forEach { it.cancel(true) }
        futures.clear(); done.clear(); cache.clear(); cachedBytes = 0
        prefetches.forEach { it.future.cancel(false) }
        prefetches.clear()
        key = null
    }

    companion object {
        const val DEFAULT_CACHE_BYTES = 64L * 1024 * 1024
        const val DEFAULT_MAX_IN_FLIGHT = 4
    }
}

/**
 * Memory budget of the detail ring (REN-28 §15.4): the largest detail radius (down to [StreamingRings.MIN_DETAIL])
 * whose chunks fit [budgetBytes] at [bytesPerChunk]; the physics ring never shrinks.
 */
object StreamingBudget {
    fun chunksWithin(radius: Float, chunkSize: Float): Long {
        val r = radius + chunkSize // boxes touching the circle
        return ((PI * r * r) / (chunkSize * chunkSize)).toLong() + 1
    }

    fun fit(rings: StreamingRings, chunkSize: Float, bytesPerChunk: Long, budgetBytes: Long): StreamingRings {
        var detail = rings.detail
        while (detail > StreamingRings.MIN_DETAIL && chunksWithin(detail, chunkSize) * bytesPerChunk > budgetBytes) {
            detail = maxOf(StreamingRings.MIN_DETAIL, detail * 0.9f)
        }
        return rings.copy(detail = maxOf(detail, rings.physics))
    }
}
