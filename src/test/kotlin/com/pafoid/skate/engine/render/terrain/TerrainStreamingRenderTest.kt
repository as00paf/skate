package com.pafoid.skate.engine.render.terrain

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.TerrainStreaming
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.TerrainSystem
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.terrain.TerrainChunkBuilder
import com.pafoid.skate.engine.terrain.TerrainPreset
import io.mockk.mockk
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** REN-28 tasks 14 and 15 (render side, fake device): budgeted chunk uploads and the far field. */
class TerrainStreamingRenderTest {
    private val device = FakeRhiDevice()
    @BeforeEach fun setUp() { RhiContext.device = device }
    @AfterEach fun tearDown() { RhiContext.device = null }

    private fun streamedScene(): Pair<Scene, TerrainComponent> {
        val t = TerrainComponent(sizeMetres = 512f, chunkQuads = 32, recipe = TerrainPreset.ROLLING_HILLS.recipe(),
            streaming = TerrainStreaming.ON, biomes = mutableListOf())
        val scene = Scene("S").also { it.addChild(GameObject("World").addComponent(Transform()).addComponent(t)) }
        TerrainSystem(debounceSeconds = 0f).apply { init(scene) }.update(0f)
        t.runtime.awaitOverview()
        return scene to t
    }

    private fun draw(renderer: TerrainRenderer, scene: Scene) = ArrayList<TerrainRenderer.Draw>().also { renderer.collect(scene, it) }.single()

    @Test
    fun StreamedChunks_UploadWithinTheByteBudget_OverSeveralFrames() {
        val (scene, t) = streamedScene()
        val g = t.runtime.generator!!
        for (cx in 0 until 4) t.runtime.putChunk(TerrainChunkBuilder.build(g, cx, 0))
        val renderer = TerrainRenderer(device = { device })
        renderer.setUploadBudget(timeNs = Long.MAX_VALUE, bytes = 1) // one chunk per frame
        val d = draw(renderer, scene)
        assertEquals(0, renderer.sync(d).size, "queued, not uploaded yet")
        assertEquals(4, renderer.pendingUploads)
        assertEquals(1, renderer.drainUploads())
        assertEquals(1, renderer.sync(d).size)
        repeat(3) { renderer.drainUploads() }
        assertEquals(4, renderer.sync(d).size)
        // An evicted chunk leaves the queue and the GPU
        t.runtime.removeChunk(0, 0)
        assertEquals(3, renderer.sync(d).size)
        renderer.destroy()
    }

    @Test
    fun FarField_DrawsTilesWhereNoChunkIsUploaded() {
        val (scene, t) = streamedScene()
        val renderer = TerrainRenderer(device = { device })
        val d = draw(renderer, scene)
        renderer.sync(d)
        renderer.setFrustum(Matrix4f().ortho(-1000f, 1000f, -1000f, 1000f, -5000f, 5000f).lookAt(0f, 100f, 0f, 0f, 0f, 0f, 0f, 0f, -1f))
        assertNotNull(t.runtime.overview, "the overview is built before drawing")
        // Count draws on this test's own device: EngineStats is a JVM-wide static other tests also write (REN-49)
        device.events.clear()
        renderer.drawFarField(mockk<Shader>(relaxed = true), d, Vector3f(), 4000f, colour = true)
        val withoutDetail = device.events.count { it.startsWith("drawIndexed") }
        assertTrue(withoutDetail > 0)
        // Upload every chunk: the far field has nothing left to cover
        val g = t.runtime.generator!!
        for (cz in 0 until 16) for (cx in 0 until 16) t.runtime.putChunk(TerrainChunkBuilder.build(g, cx, cz))
        renderer.setUploadBudget(Long.MAX_VALUE, Long.MAX_VALUE)
        renderer.sync(d); renderer.drainUploads(); renderer.sync(d)
        device.events.clear()
        renderer.drawFarField(mockk<Shader>(relaxed = true), d, Vector3f(), 4000f, colour = true)
        assertEquals(0, device.events.count { it.startsWith("drawIndexed") })
        renderer.destroy()
    }

    @Test
    fun NonStreamedTerrains_UploadAtOnce_AndHaveNoFarField() {
        val t = TerrainComponent(sizeMetres = 128f, chunkQuads = 32, streaming = TerrainStreaming.OFF, biomes = mutableListOf())
        val scene = Scene("S").also { it.addChild(GameObject("Small").addComponent(Transform()).addComponent(t)) }
        TerrainSystem(debounceSeconds = 0f).apply { init(scene) }.update(0f)
        val renderer = TerrainRenderer(device = { device })
        val d = draw(renderer, scene)
        assertEquals(16, renderer.sync(d).size)
        assertEquals(0, renderer.pendingUploads)
        EngineStats.resetDrawCalls()
        renderer.drawFarField(mockk(relaxed = true), d, Vector3f(), 4000f, colour = true)
        assertEquals(0, EngineStats.terrainFarTiles.get())
        renderer.destroy()
    }

    @Test
    fun TileData_SamplesTheOverview() {
        val (_, t) = streamedScene()
        val o = t.runtime.overview!!
        val tile = FarFieldRenderer.tileData(com.pafoid.skate.engine.terrain.streaming.FarTile(2, 1, 0), t.runtime.layout!!, o)
        assertEquals(FarFieldRenderer.TILE_QUADS, tile.chunkQuads)
        assertEquals(4f, tile.spacing) // 4 chunks of 32 m over 32 quads
        assertEquals(o.heightAt(tile.cornerX + 8f, tile.cornerZ), tile.height(2, 0), 1e-5f)
    }
}
