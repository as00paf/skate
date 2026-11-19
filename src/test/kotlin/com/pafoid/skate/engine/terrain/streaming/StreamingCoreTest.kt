package com.pafoid.skate.engine.terrain.streaming

import com.pafoid.skate.engine.terrain.TerrainChunkBuilder
import com.pafoid.skate.engine.terrain.TerrainGenerator
import com.pafoid.skate.engine.terrain.TerrainKey
import com.pafoid.skate.engine.terrain.TerrainLayout
import com.pafoid.skate.engine.terrain.TerrainOverview
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.engine.terrain.TerrainRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executor

/** REN-28 tasks 14 and 15: overview, far-field tiles, planner, streamer, upload queue, budgets (no GL). */
class StreamingCoreTest {
    private val layout = TerrainLayout(1024f, 1f, 32) // 32 x 32 chunks of 32 m
    private val seed = 3L

    private fun generator(l: TerrainLayout = layout) = TerrainGenerator(TerrainPreset.ROLLING_HILLS.recipe(), emptyList(), l, 30f, seed, "terrain/S")

    private fun streamedKey(size: Float = 1024f) = TerrainKey(size, 1f, 32, 30f, TerrainPreset.ROLLING_HILLS.recipe(), emptyList(), seed, "terrain/S", 0, streamed = true)

    // --- Task 14: overview and far field ---

    @Test
    fun Overview_SamplesTheGenerator_AndInterpolates() {
        val g = generator(TerrainLayout(256f, 1f, 32))
        val o = TerrainOverview.build(g, spacing = 8f, parallel = true)
        assertEquals(33, o.samplesPerSide)
        assertEquals(g.heightAt(-128f + 16f, -128f + 24f), o.sample(2, 3), 1e-5f)
        val mid = (o.sample(2, 3) + o.sample(3, 3)) / 2f
        assertEquals(mid, o.heightAt(-128f + 20f, -128f + 24f), 1e-4f)
        // An edited chunk refreshes its overview samples
        val chunk = TerrainChunkBuilder.build(g, 0, 0)
        for (j in 0..32) for (i in 0..32) chunk.setHeight(i, j, 99f)
        o.refresh(chunk)
        assertEquals(99f, o.sample(1, 1))
        assertEquals(g.heightAt(-128f + 64f, -128f + 64f), o.sample(8, 8), 1e-5f, "outside the chunk: untouched")
    }

    @Test
    fun StreamedRuntime_BuildsNoChunks_AndFallsBackToTheOverview() {
        val rt = TerrainRuntime()
        rt.ensureBuilt(streamedKey())
        assertTrue(rt.streamed)
        assertTrue(rt.chunks.isEmpty())
        val overview = rt.awaitOverview()!!
        assertFalse(rt.heightField!!.isDetailed(0f, 0f))
        assertEquals(overview.heightAt(10f, 20f), rt.heightField!!.heightAt(10f, 20f), 1e-5f)
        val c = TerrainChunkBuilder.build(rt.generator!!, 16, 16)
        val v = rt.version
        rt.putChunk(c)
        assertTrue(rt.version > v)
        assertTrue(rt.heightField!!.isDetailed(c.cornerX + 1f, c.cornerZ + 1f))
        assertTrue(rt.removeChunk(16, 16) === c)
    }

    @Test
    fun FarField_CoversTheTerrainOnce_SkippingDetailedChunks() {
        val tiles = ArrayList<FarTile>()
        val detailed = setOf(15 to 15, 16 to 15, 15 to 16, 16 to 16)
        FarFieldQuadtree.select(layout, 0f, 0f, 1e6f, { cx, cz -> (cx to cz) in detailed }, tiles)
        val covered = HashMap<Pair<Int, Int>, Int>()
        for (t in tiles) {
            val span = 1 shl t.level
            for (cz in t.tz * span until minOf((t.tz + 1) * span, 32)) for (cx in t.tx * span until minOf((t.tx + 1) * span, 32)) covered.merge(cx to cz, 1, Int::plus)
        }
        for (cz in 0 until 32) for (cx in 0 until 32) {
            val expected = if ((cx to cz) in detailed) null else 1
            assertEquals(expected, covered[cx to cz], "chunk ($cx, $cz)")
        }
        assertTrue(tiles.any { it.level >= 2 }, "far away the tiles are large")
        // Beyond the far distance nothing draws
        FarFieldQuadtree.select(layout, 0f, 0f, 100f, { _, _ -> false }, tiles)
        assertTrue(tiles.all { t -> val side = (1 shl t.level) * 32f; t.level <= 3 && side > 0f })
        assertTrue(tiles.size < 64)
    }

    // --- Task 15: planner ---

    private val rings = StreamingRings(physics = 64f, detail = 160f)

    private fun plan(focus: Focus, resident: BooleanArray = BooleanArray(layout.chunkCount), pinned: BooleanArray = BooleanArray(layout.chunkCount)): ChunkStreamingPlanner =
        ChunkStreamingPlanner(layout).apply { plan(listOf(focus), 1, rings, resident, BooleanArray(layout.chunkCount), pinned) }

    @Test
    fun Planner_WantsTheRings_PhysicsFirst_NearestFirst() {
        val p = plan(Focus().set(0f, 0f))
        val loads = (0 until p.loadCount).map { p.loads[it] }
        assertTrue(loads.all { p.wanted[it] })
        assertEquals(loads.size, (0 until layout.chunkCount).count { p.wanted[it] })
        val firstNonPhysics = loads.indexOfFirst { !p.physicsWanted[it] }
        assertTrue(loads.take(firstNonPhysics).all { p.physicsWanted[it] }, "physics ring first")
        val n = layout.chunksPerSide
        val d = loads.drop(firstNonPhysics).map { p.distance(it % n, it / n, 0f, 0f) }
        assertEquals(d.sorted(), d)
        val far = 0 * n + 0
        assertFalse(p.wanted[far])
    }

    @Test
    fun Planner_PredictsAlongTheVelocity() {
        val n = layout.chunksPerSide
        val still = plan(Focus().set(0f, 0f))
        val moving = plan(Focus().set(0f, 0f, vx = 40f))
        fun rank(p: ChunkStreamingPlanner, cx: Int, cz: Int) = (0 until p.loadCount).indexOfFirst { p.loads[it] == cz * n + cx }
        // A chunk ahead (+X) moves up the queue, one behind moves down
        assertTrue(rank(moving, 20, 16) < rank(still, 20, 16))
        assertTrue(rank(moving, 11, 16) > rank(still, 11, 16))
    }

    @Test
    fun Planner_EvictsOnlyPastTheUnloadRadius_FarthestFirst_NeverPinned() {
        val n = layout.chunksPerSide
        val resident = BooleanArray(layout.chunkCount) { true }
        val pinned = BooleanArray(layout.chunkCount).also { it[0] = true }
        val p = plan(Focus().set(0f, 0f), resident, pinned)
        val evicted = (0 until p.evictionCount).map { p.evictions[it] }
        val unload = rings.detail * rings.unloadFactor
        assertTrue(evicted.all { p.distance(it % n, it / n, 0f, 0f) > unload })
        assertFalse(0 in evicted, "pinned")
        // Hysteresis: just outside the detail ring but inside the unload radius stays
        val kept = (0 until layout.chunkCount).filter { val d = p.distance(it % n, it / n, 0f, 0f); d > rings.detail && d <= unload }
        assertTrue(kept.isNotEmpty() && kept.none { it in evicted })
        val dist = evicted.map { p.distance(it % n, it / n, 0f, 0f) }
        assertEquals(dist.sortedDescending(), dist)
    }

    @Test
    fun Planner_AllocatesNothingInSteadyState() {
        val bean = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val p = ChunkStreamingPlanner(layout)
        val focuses = listOf(Focus().set(0f, 0f, vx = 3f))
        val resident = BooleanArray(layout.chunkCount); val inFlight = BooleanArray(layout.chunkCount); val pinned = BooleanArray(layout.chunkCount)
        repeat(50) { p.plan(focuses, 1, rings, resident, inFlight, pinned) } // warm up
        val before = bean.getThreadAllocatedBytes(Thread.currentThread().id)
        repeat(200) { p.plan(focuses, 1, rings, resident, inFlight, pinned) }
        val allocated = bean.getThreadAllocatedBytes(Thread.currentThread().id) - before
        assertTrue(allocated < 4096, "allocated $allocated bytes")
    }

    // --- Task 15: streamer ---

    private class ManualExecutor : Executor {
        val tasks = ArrayList<Runnable>()
        override fun execute(command: Runnable) { tasks += command }
        fun runAll() { val copy = tasks.toList(); tasks.clear(); copy.forEach { it.run() } }
    }

    @Test
    fun Streamer_LoadsTheRings_EvictsBehind_AndReusesTheCache() {
        val rt = TerrainRuntime().apply { ensureBuilt(streamedKey(), parallel = false) }
        val exec = ManualExecutor()
        val s = TerrainStreamer(rt, exec, maxInFlight = 1000, maxEvictionsPerUpdate = 1000)
        val focus = listOf(Focus().set(0f, 0f))
        repeat(3) { s.update(focus, 1, rings); exec.runAll() }
        s.update(focus, 1, rings)
        val n = layout.chunksPerSide
        assertTrue(rt.chunks.isNotEmpty())
        assertTrue(rt.chunks.all { ChunkStreamingPlanner(layout).distance(it.cx, it.cz, 0f, 0f) <= rings.detail * rings.unloadFactor })
        val near = rt.chunk(16, 16)!!
        // Move away: chunks behind are evicted into the cache, then come back from it
        val away = listOf(Focus().set(400f, 0f))
        repeat(3) { s.update(away, 1, rings); exec.runAll() }
        assertEquals(null, rt.chunk(16, 16))
        assertTrue(s.cacheSize > 0)
        val hits = s.cacheHits
        s.update(focus, 1, rings)
        assertTrue(s.cacheHits > hits)
        assertTrue(rt.chunk(16, 16) === near, "the same chunk, not regenerated")
        assertTrue(n > 0)
    }

    @Test
    fun Streamer_CancelsLoadsThatLeftTheRing() {
        val rt = TerrainRuntime().apply { ensureBuilt(streamedKey(), parallel = false) }
        val exec = ManualExecutor()
        val s = TerrainStreamer(rt, exec, maxInFlight = 8)
        s.update(listOf(Focus().set(0f, 0f)), 1, rings)
        assertEquals(8, s.loading)
        s.update(listOf(Focus().set(-480f, -480f)), 1, rings) // far corner: the old loads are not wanted
        assertTrue(s.cancelled >= 8)
        exec.runAll()
        s.update(listOf(Focus().set(-480f, -480f)), 1, rings)
        assertTrue(rt.chunks.none { it.cx in 13..18 && it.cz in 13..18 }, "cancelled chunks never arrive")
    }

    @Test
    fun Streamer_PrefetchCompletesOnlyWhenTheRingsAreResident() {
        val rt = TerrainRuntime().apply { ensureBuilt(streamedKey(), parallel = false) }
        val exec = ManualExecutor()
        val s = TerrainStreamer(rt, exec, maxInFlight = 1000)
        val ready = s.prefetch(200f, 200f, rings, physicsOnly = true)
        s.update(emptyList(), 0, rings)
        assertFalse(ready.isDone)
        exec.runAll(); s.update(emptyList(), 0, rings)
        assertTrue(ready.isDone)
        assertTrue(rt.heightField!!.isDetailed(200f, 200f))
    }

    @Test
    fun Streamer_ResetsWhenTheTerrainIsRebuilt() {
        val rt = TerrainRuntime().apply { ensureBuilt(streamedKey(), parallel = false) }
        val exec = ManualExecutor()
        val s = TerrainStreamer(rt, exec, maxInFlight = 1000)
        s.update(listOf(Focus().set(0f, 0f)), 1, rings)
        rt.ensureBuilt(streamedKey().copy(seedOffset = 1), parallel = false) // reroll: in-flight loads are stale
        exec.runAll()
        s.update(listOf(Focus().set(0f, 0f)), 1, rings)
        exec.runAll()
        s.update(listOf(Focus().set(0f, 0f)), 1, rings)
        val c = rt.chunk(16, 16)!!
        assertEquals(TerrainChunkBuilder.build(rt.generator!!, 16, 16).heights.toList(), c.heights.toList(), "chunks of the new seed")
    }

    // --- Task 15: upload queue and budgets ---

    @Test
    fun UploadQueue_RespectsTimeAndByteBudgets_FirstItemAlwaysRuns() {
        var now = 0L
        val q = StreamingUploadQueue<Int>(timeBudgetNs = 1000, byteBudget = 100) { now }
        repeat(10) { q.add(it, 30) }
        val done = ArrayList<Int>()
        assertEquals(3, q.drain { done += it }, "100 bytes: 3 items of 30")
        q.byteBudget = 1_000_000
        assertEquals(7, q.drain { done += it; now += 100 })
        repeat(10) { q.add(it, 10) }
        assertEquals(10, q.drain { now += 99 })
        repeat(5) { q.add(it, 10) }
        assertEquals(1, q.drain { now += 5000 }, "over time: only the first")
        q.add(99, 10_000_000)
        assertTrue(q.drain { } >= 1, "a big item still goes when first")
    }

    @Test
    fun Budget_ShrinksTheDetailRingDownToItsMinimum_NeverThePhysicsRing() {
        val r = StreamingRings(physics = 128f, detail = 512f)
        assertEquals(512f, StreamingBudget.fit(r, 64f, 70_000, 1L shl 30).detail)
        val tight = StreamingBudget.fit(r, 64f, 70_000, 8L * 1024 * 1024)
        assertTrue(tight.detail < 512f && tight.detail >= StreamingRings.MIN_DETAIL)
        assertEquals(128f, tight.physics)
        assertEquals(StreamingRings.MIN_DETAIL, StreamingBudget.fit(r, 64f, 70_000, 1).detail)
    }
}
