package com.pafoid.skate.engine.render.terrain

import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.ecs.components.TerrainLayer
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.render.material.MaterialAsset
import com.pafoid.skate.engine.render.material.MaterialSchema
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.terrain.BiomeClimate
import com.pafoid.skate.engine.terrain.BiomePresets
import com.pafoid.skate.engine.terrain.ScatterPlacer
import com.pafoid.skate.engine.terrain.ScatterRule
import com.pafoid.skate.engine.terrain.TerrainChunkBuilder
import com.pafoid.skate.engine.terrain.TerrainGenerator
import com.pafoid.skate.engine.terrain.TerrainLayout
import com.pafoid.skate.engine.terrain.TerrainPreset
import io.mockk.mockk
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** REN-28 tasks 5 and 7 (GPU side, fake device): layer images and arrays, splat atlas, scatter batches. */
class TerrainMaterialTest {
    private val device = FakeRhiDevice()
    @BeforeEach fun setUp() { RhiContext.device = device }
    @AfterEach fun tearDown() { RhiContext.device = null }

    @Test
    fun Resample_KeepsFlatImagesFlat_AndInterpolates() {
        val flat = RgbaImage(3, 3, ByteArray(36) { 100 })
        assertTrue(ImageResample.bilinear(flat, 8, 8).all { it.toInt() == 100 })
        val ramp = RgbaImage(2, 1, byteArrayOf(0, 0, 0, 0, 200.toByte(), 200.toByte(), 200.toByte(), 200.toByte()))
        val out = ImageResample.bilinear(ramp, 4, 1)
        val reds = (0 until 4).map { out[it * 4].toInt() and 255 }
        assertEquals(0, reds.first()); assertEquals(200, reds.last()); assertTrue(reds[1] in 1..199)
    }

    @Test
    fun LayerImages_DefaultsWithoutMaterial_TintedTexturesWithOne() {
        val sand = TerrainLayerImages.build(0, null, 4, { null }, { null })
        assertEquals(0.76f, sand.averageColor[0], 0.01f)
        assertEquals(128, sand.normal[0].toInt() and 255)
        val asset = MaterialAsset(params = mapOf(MaterialSchema.Companion.Param.BASE_COLOR to listOf(0.5f, 1f, 1f, 1f)),
            textures = mapOf(MaterialSchema.Companion.Tex.BASE_COLOR to "white.png"))
        val tinted = TerrainLayerImages.build(1, TerrainLayer(material = "m.skmat"), 4, { asset }, { RgbaImage(1, 1, ByteArray(4) { -1 }) })
        assertEquals(0.5f, tinted.averageColor[0], 0.01f)
        val alpha = tinted.albedo[3].toInt() and 255 // height from luminance
        assertEquals(((0.3f * 0.5f + 0.59f + 0.11f) * 255 + 0.5f).toInt(), alpha)
        assertEquals(8, TerrainLayerImages.buildAll(emptyList(), 4, { null }, { null }).size)
    }

    @Test
    fun LayerArrays_UploadEveryLayerOfThreeArrays_WithMips() {
        val arrays = TerrainLayerArrays(device, TerrainLayerImages.buildAll(emptyList(), 8, { null }, { null }), 8)
        val textures = device.textures.takeLast(3).map { it as FakeRhiDevice.Texture }
        textures.forEach { t ->
            assertEquals(8, t.desc.layers)
            assertEquals((0 until 8).map { 0 to it }, t.uploads)
            assertTrue(t.mipsGenerated)
        }
        arrays.close()
    }

    @Test
    fun SplatAtlas_HandsOutAndTakesBackLayers_UploadingBothHalves() {
        val atlas = SplatAtlas(device, 32, 3)
        val g = TerrainGenerator(TerrainPreset.ISLAND.recipe(), emptyList(), TerrainLayout(64f, 1f, 32), 30f, 1L, "t", 0, BiomePresets.defaults(), BiomeClimate())
        val layer = atlas.acquire()
        atlas.upload(layer, TerrainChunkBuilder.build(g, 0, 0))
        assertEquals(listOf(0 to layer), (atlas.first as FakeRhiDevice.Texture).uploads)
        assertEquals(listOf(0 to layer), (atlas.second as FakeRhiDevice.Texture).uploads)
        atlas.acquire(); atlas.acquire()
        assertEquals(-1, atlas.acquire(), "full")
        atlas.release(layer)
        assertEquals(layer, atlas.acquire())
        atlas.close()
    }

    @Test
    fun Scatter_VisibleCountThinsWithDistance() {
        assertEquals(100, TerrainScatterRenderer.visibleCount(100, 50f, 100f, 150f))
        assertEquals(50, TerrainScatterRenderer.visibleCount(100, 125f, 100f, 150f))
        assertEquals(0, TerrainScatterRenderer.visibleCount(100, 150f, 100f, 150f))
    }

    @Test
    fun Scatter_BuildsOneBatchPerPart_SortedByRandom_AndRebuildsOnChange() {
        val loader = VAOLoader { device }
        val part = loader.loadToVAO(FloatArray(9), FloatArray(6), FloatArray(9), intArrayOf(0, 1, 2))
        val model = `3dModel`(mesh = listOf(part))
        val renderer = TerrainScatterRenderer(ModelRenderer(mockk(relaxed = true), { device }, loader), { model }, { null }) { device }
        val rule = ScatterRule(id = "r", model = "rock.obj", densityPer100m2 = 10f, minSpacing = 2f, maxSlope = 90f)
        val biome = BiomePresets.defaults()[0].copy(scatter = listOf(rule))
        val g = TerrainGenerator(TerrainPreset.FLAT.recipe(), emptyList(), TerrainLayout(64f, 1f, 32), 30f, 1L, "t", 0, listOf(biome), BiomeClimate())
        val chunk = TerrainChunkBuilder.build(g, 0, 0)
        val batches = renderer.batches(chunk, g.scatterEntries, Vector3f())
        assertEquals(1, batches.size)
        assertEquals(chunk.scatter.size, batches[0].count)
        val randoms = (0 until batches[0].count).map { batches[0].batches[0].data[it * 16 + 13] }
        assertEquals(randoms.sorted(), randoms)
        assertTrue(batches === renderer.batches(chunk, g.scatterEntries, Vector3f()), "cached")
        chunk.scatter = chunk.scatter.take(1)
        assertNotEquals(batches, renderer.batches(chunk, g.scatterEntries, Vector3f()))
        renderer.destroy()
    }

    @Test
    fun Scatter_MatrixSitsOnTheGround_AndAlignsWithTheNormal() {
        val r = TerrainScatterRenderer(mockk(relaxed = true))
        val item = com.pafoid.skate.engine.terrain.ScatterInstance(0, 0, 1f, 2f, 3f, 0f, 2f, 0f, 0.8f, 0.6f, 0.5f)
        val upright = r.matrixOf(item, ScatterRule(alignToNormal = 0f), Vector3f(10f, 0f, 0f))
        assertEquals(Vector3f(11f, 2f, 3f), upright.getTranslation(Vector3f()))
        assertTrue(upright.transformDirection(Vector3f(0f, 1f, 0f)).normalize().equals(Vector3f(0f, 1f, 0f), 1e-5f))
        val aligned = r.matrixOf(item, ScatterRule(alignToNormal = 1f), Vector3f())
        assertTrue(aligned.transformDirection(Vector3f(0f, 1f, 0f)).normalize().equals(Vector3f(0f, 0.8f, 0.6f), 1e-4f))
    }
}
