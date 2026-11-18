package com.pafoid.skate.engine.terrain.streaming

import com.pafoid.skate.engine.terrain.TerrainLayout
import kotlin.math.sqrt

/**
 * Decides which chunks of one terrain to load and evict (REN-28 §15.3), pure and allocation-free in steady state
 * (arrays reused, sized by the chunk count).
 *
 * - **Wanted:** a chunk within any focus's detail ring (physics ring for the [physicsWanted] flag).
 * - **Load order:** physics-ring chunks first, then by distance to the *predicted* focus position
 *   (`position + velocity × lookahead`), minus a focus priority bonus.
 * - **Evictions:** resident chunks outside every focus's unload radius (`unloadFactor` × detail), farthest first, never
 *   pinned ones.
 */
class ChunkStreamingPlanner(val layout: TerrainLayout, val lookahead: Float = LOOKAHEAD_SECONDS) {
    private val count = layout.chunkCount

    /** Per chunk, after [plan]: inside some detail ring / physics ring / unload radius. */
    val wanted = BooleanArray(count)
    val physicsWanted = BooleanArray(count)
    val keep = BooleanArray(count)

    /** Load requests of the last [plan], best first: chunk indices `cz * chunksPerSide + cx`. */
    val loads = IntArray(count)
    var loadCount = 0
        private set

    /** Evictions of the last [plan], farthest first. */
    val evictions = IntArray(count)
    var evictionCount = 0
        private set

    private val score = FloatArray(count)

    /**
     * Plans with [focuses] (the first [focusCount]), [rings], and per-chunk [resident] / [inFlight] (loading or queued)
     * / [pinned] flags (indexed like [loads]).
     */
    fun plan(focuses: List<Focus>, focusCount: Int, rings: StreamingRings, resident: BooleanArray, inFlight: BooleanArray, pinned: BooleanArray) {
        val n = layout.chunksPerSide
        for (k in 0 until count) {
            wanted[k] = false; physicsWanted[k] = false; keep[k] = false; score[k] = Float.MAX_VALUE
        }
        for (f in 0 until focusCount) {
            val focus = focuses[f]
            // Scaled radii inline: no copy of the rings per focus (allocation-free)
            val detail = rings.detail * focus.radiusScale
            val physics = rings.physics * focus.radiusScale
            val px = focus.x + focus.vx * lookahead
            val pz = focus.z + focus.vz * lookahead
            val unload = detail * rings.unloadFactor
            // Only the chunks a ring can reach
            val c0x = layout.chunkX(focus.x - unload); val c1x = layout.chunkX(focus.x + unload)
            val c0z = layout.chunkZ(focus.z - unload); val c1z = layout.chunkZ(focus.z + unload)
            for (cz in c0z..c1z) for (cx in c0x..c1x) {
                val k = cz * n + cx
                val d = distance(cx, cz, focus.x, focus.z)
                if (d <= unload) keep[k] = true
                if (d > detail) continue
                wanted[k] = true
                val inPhysics = focus.physics && d <= physics
                if (inPhysics) physicsWanted[k] = true
                val s = (if (inPhysics) 0f else PHYSICS_FIRST) + distance(cx, cz, px, pz) - focus.priority * PRIORITY_METRES
                if (s < score[k]) score[k] = s
            }
        }
        loadCount = 0
        for (k in 0 until count) if (wanted[k] && !resident[k] && !inFlight[k]) loads[loadCount++] = k
        sortBy(loads, loadCount, ascending = true)
        evictionCount = 0
        for (k in 0 until count) if (resident[k] && !keep[k] && !pinned[k]) evictions[evictionCount++] = k
        // Farthest first: score of evictions = -distance to the nearest focus
        for (i in 0 until evictionCount) {
            val k = evictions[i]
            var nearest = Float.MAX_VALUE
            for (f in 0 until focusCount) nearest = minOf(nearest, distance(k % n, k / n, focuses[f].x, focuses[f].z))
            score[k] = -nearest
        }
        sortBy(evictions, evictionCount, ascending = true)
    }

    /** Horizontal distance from (x, z) to chunk (cx, cz)'s box, 0 inside. */
    fun distance(cx: Int, cz: Int, x: Float, z: Float): Float {
        val minX = layout.chunkCornerX(cx); val minZ = layout.chunkCornerZ(cz)
        val dx = maxOf(minX - x, 0f, x - (minX + layout.chunkSize))
        val dz = maxOf(minZ - z, 0f, z - (minZ + layout.chunkSize))
        return sqrt(dx * dx + dz * dz)
    }

    /** Insertion sort of [a]'s first [size] chunk indices by [score] (no allocation; lists are short in practice). */
    private fun sortBy(a: IntArray, size: Int, ascending: Boolean) {
        for (i in 1 until size) {
            val v = a[i]; val s = score[v]
            var j = i - 1
            while (j >= 0 && (if (ascending) score[a[j]] > s else score[a[j]] < s)) { a[j + 1] = a[j]; j-- }
            a[j + 1] = v
        }
    }

    companion object {
        /** Seconds of focus motion the load order looks ahead (a 15 m/s skater: 22 m). */
        const val LOOKAHEAD_SECONDS = 1.5f
        /** Physics-ring chunks sort before everything else. */
        private const val PHYSICS_FIRST = 1e6f
        /** One focus priority step is worth this many metres of distance. */
        private const val PRIORITY_METRES = 100f
    }
}
