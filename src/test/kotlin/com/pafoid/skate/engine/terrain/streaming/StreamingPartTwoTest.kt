package com.pafoid.skate.engine.terrain.streaming

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.TerrainStreaming
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.TerrainStreamingSystem
import com.pafoid.skate.engine.ecs.systems.TerrainSystem
import com.pafoid.skate.engine.terrain.TerrainKey
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.engine.terrain.TerrainRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-28 tasks 17, 20, 21 and 22 (REN parts). */
class StreamingPartTwoTest {
    private fun streamedKey() = TerrainKey(1024f, 1f, 32, 30f, TerrainPreset.ROLLING_HILLS.recipe(), emptyList(), 3L, "terrain/S", 0, streamed = true)
    private val direct = java.util.concurrent.Executor { it.run() }

    @Test
    fun PhysicsRing_IsPublishedForColliders_AndEverythingCountsWithoutStreaming() {
        val rt = TerrainRuntime().apply { ensureBuilt(streamedKey(), parallel = false) }
        val s = TerrainStreamer(rt, direct, maxInFlight = 1000)
        s.update(listOf(Focus().set(0f, 0f)), 1, StreamingRings(physics = 32f, detail = 128f))
        assertTrue(rt.isPhysicsChunk(16, 16))
        assertFalse(rt.isPhysicsChunk(20, 16), "detail ring only")
        assertEquals(rt.layout!!.chunkCount, rt.colliderPresent.size)
        // A camera alone streams no physics ring
        s.update(listOf(Focus().set(0f, 0f, physics = false)), 1, StreamingRings(physics = 32f, detail = 128f))
        assertFalse(rt.isPhysicsChunk(16, 16))
        val small = TerrainRuntime().apply { ensureBuilt(streamedKey().copy(size = 128f, streamed = false), parallel = false) }
        assertTrue(small.isPhysicsChunk(0, 0) && small.isPhysicsChunk(3, 3))
    }

    @Test
    fun SparseEdits_RefreshTheOverview_TrackEdits_AndCanBeDiscarded() {
        val rt = TerrainRuntime().apply { ensureBuilt(streamedKey(), parallel = false) }
        val overview = rt.awaitOverview()!!
        val s = TerrainStreamer(rt, direct, maxInFlight = 1000)
        repeat(2) { s.update(listOf(Focus().set(0f, 0f)), 1, StreamingRings(32f, 64f)) }
        val chunk = rt.chunk(16, 16)!!
        for (j in 0..32) for (i in 0..32) chunk.setHeight(i, j, 77f)
        val v = rt.overviewVersion
        rt.markEdited()
        assertTrue(rt.hasEdits)
        assertTrue(rt.overviewVersion > v)
        assertEquals(77f, overview.heightAt(chunk.cornerX + 16f, chunk.cornerZ + 16f), 1e-3f)
        rt.discardEdits()
        assertFalse(rt.hasEdits)
        assertTrue(rt.ensureBuilt(streamedKey(), parallel = false), "rebuilt from the recipe")
    }

    @Test
    fun QualityRings_ShrinkRenderRingsOnly() {
        val low = StreamingRings.forQuality("Low")
        assertTrue(low.detail < StreamingRings().detail && low.far < StreamingRings().far)
        assertEquals(StreamingRings().physics, low.physics)
        assertEquals(StreamingRings(), StreamingRings.forQuality("ULTRA"))
        assertEquals(StreamingRings(), StreamingRings.forQuality(null))
    }

    @Test
    fun Prefetch_WaitsForTheSpawnArea_InEveryTerrainThere() {
        val t = TerrainComponent(sizeMetres = 1024f, chunkQuads = 32, streaming = TerrainStreaming.ON, recipe = TerrainPreset.ROLLING_HILLS.recipe(), biomes = mutableListOf())
        val scene = Scene("S").also { it.addChild(GameObject("W").addComponent(Transform().apply { worldMatrix.translation(100f, 0f, 0f) }).addComponent(t)) }
        val build = TerrainSystem(debounceSeconds = 0f).apply { init(scene) }
        val stream = TerrainStreamingSystem(executor = direct).apply { rings = StreamingRings(32f, 96f); init(scene) }
        build.update(0f)
        val ready = stream.prefetch(300f, 50f, physicsOnly = true)
        assertFalse(ready.isDone)
        // At most 4 loads at a time (the default): a few frames. Stop when ready: with no focus left (no camera, no
        // StreamingFocus in this scene) later frames would evict the area again; in a game the player's focus keeps it.
        var frames = 0
        while (!ready.isDone && frames < 20) { stream.update(0.016f); frames++ }
        assertTrue(ready.isDone)
        assertTrue(frames > 1, "it waited for the loads")
        assertTrue(t.runtime.heightField!!.isDetailed(200f, 50f), "terrain-local (300 - 100, 50)")
        assertTrue(stream.prefetch(9000f, 0f).isDone, "outside every terrain: nothing to wait for")
        assertTrue(stream.statsLines().single().startsWith("W: "))
    }

    @Test
    fun BigIsland_IsAStreamed4kmIslandWithAPlateau() {
        val t = TerrainComponent.bigIsland(plateauX = 0f, plateauZ = 400f, plateauHeight = 12f)
        assertTrue(t.isStreamed())
        val rt = t.runtime
        rt.ensureBuilt(t.key(1L), parallel = true)
        assertEquals(4096f, rt.layout!!.size)
        assertEquals(12f, rt.awaitOverview()!!.heightAt(0f, 400f), 0.5f)
        assertEquals(12f, rt.generator!!.heightAt(10f, 410f), 1e-3f)
    }
}
