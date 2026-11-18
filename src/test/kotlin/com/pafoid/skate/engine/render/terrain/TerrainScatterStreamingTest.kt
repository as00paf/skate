package com.pafoid.skate.engine.render.terrain

import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.terrain.Biome
import com.pafoid.skate.engine.terrain.LayerRule
import com.pafoid.skate.engine.terrain.ScatterRule
import com.pafoid.skate.engine.terrain.TerrainKey
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.engine.terrain.TerrainRuntime
import com.pafoid.skate.engine.terrain.scatter.TerrainScatter
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** REN-28 tasks 18 and 23 (render side): budgeted scatter batch builds, recorded items skipped. */
class TerrainScatterStreamingTest {
    private val device = FakeRhiDevice()
    @BeforeEach fun setUp() { RhiContext.device = device }
    @AfterEach fun tearDown() { RhiContext.device = null }

    private fun setup(): Triple<TerrainRuntime, TerrainScatterRenderer, `3dModel`> {
        val loader = VAOLoader { device }
        val model = `3dModel`(mesh = listOf(loader.loadToVAO(FloatArray(9), FloatArray(6), FloatArray(9), intArrayOf(0, 1, 2))))
        val renderer = TerrainScatterRenderer(ModelRenderer(mockk(relaxed = true), { device }, loader), { model }, { null }) { device }
        val biome = Biome(layerRules = listOf(LayerRule(0)), scatter = listOf(ScatterRule(id = "rocks", model = "rock.obj", densityPer100m2 = 5f, minSpacing = 3f, maxSlope = 90f)))
        val rt = TerrainRuntime().apply {
            ensureBuilt(TerrainKey(128f, 1f, 32, 20f, TerrainPreset.ROLLING_HILLS.recipe(), emptyList(), 1L, "t", 0, listOf(biome)), parallel = false)
        }
        return Triple(rt, renderer, model)
    }

    @Test
    fun BatchBuilds_AreBudgetedPerFrame() {
        val (rt, renderer, _) = setup()
        renderer.buildsPerFrame = 2
        renderer.resetCounters()
        val entries = rt.generator!!.scatterEntries
        val built = rt.chunks.count { renderer.batches(it, entries, Vector3f(), rt).isNotEmpty() }
        assertEquals(2, built, "two chunks this frame, the rest later")
        renderer.resetCounters()
        assertEquals(4, rt.chunks.count { renderer.batches(it, entries, Vector3f(), rt).isNotEmpty() })
        renderer.destroy()
    }

    @Test
    fun RecordedItems_AreSkipped_AndTheBatchRebuildsWhenStateChanges() {
        val (rt, renderer, _) = setup()
        val entries = rt.generator!!.scatterEntries
        val chunk = rt.chunks.first { it.scatter.size > 2 }
        renderer.resetCounters()
        val before = renderer.batches(chunk, entries, Vector3f(), rt).single().count
        assertEquals(chunk.scatter.size, before)
        assertTrue(TerrainScatter.remove(rt, TerrainScatter.keyOf(rt, chunk, chunk.scatter[1])!!))
        renderer.resetCounters()
        assertEquals(before - 1, renderer.batches(chunk, entries, Vector3f(), rt).single().count)
        renderer.destroy()
    }
}
