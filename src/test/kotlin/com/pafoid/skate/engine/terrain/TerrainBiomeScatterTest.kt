package com.pafoid.skate.engine.terrain

import com.pafoid.skate.engine.ecs.components.TerrainComponent
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** REN-28 tasks 6 and 7: biome map, layer weights and splat packing, macro colours, scatter placement. */
class TerrainBiomeScatterTest {
    private val seed = 42L
    private val biomes = BiomePresets.defaults()

    private fun generator(biomes: List<Biome> = this.biomes, stamps: List<TerrainStamp> = emptyList(), source: BiomeSource = BiomeSource.PROCEDURAL) =
        TerrainGenerator(TerrainPreset.ISLAND.recipe(), stamps, TerrainLayout(128f, 1f, 32), 40f, seed, "terrain/B", 0, biomes, BiomeClimate(), source)

    @Test
    fun BiomeMap_CellCentresArePure_BordersBlend_AndWeightsSumToOne() {
        val map = BiomeMap(biomes, BiomeClimate(), seed, "terrain/B")
        val w = FloatArray(biomes.size)
        // Grid columns: cold = peak (2), mid = grassland (0), hot = beach (1); cell centres at t = -2/3, 0, 2/3
        map.weightsForClimate(0f, 0f, w)
        assertEquals(1f, w[0], 1e-3f)
        map.weightsForClimate(-2f / 3f, 0f, w)
        assertEquals(1f, w[2], 1e-3f)
        map.weightsForClimate(-1f / 3f, 0f, w) // halfway between peak and grassland
        assertEquals(0.5f, w[0], 0.02f); assertEquals(0.5f, w[2], 0.02f)
        for (t in listOf(-0.9f, -0.2f, 0.4f, 0.95f)) assertEquals(1f, map.weightsForClimate(t, 0.3f, w).sum(), 1e-4f)
        val sharp = BiomeMap(biomes, BiomeClimate(borderSharpness = 12f), seed, "terrain/B")
        assertTrue(sharp.weightsForClimate(-0.25f, 0f, FloatArray(3))[0] > map.weightsForClimate(-0.25f, 0f, w)[0], "sharper borders")
    }

    @Test
    fun BiomeMap_IsSeeded_AndColderHigherUp() {
        val a = BiomeMap(biomes, BiomeClimate(), seed, "terrain/B")
        val b = BiomeMap(biomes, BiomeClimate(), seed, "terrain/B")
        assertArrayEquals(a.climateAt(10f, 20f, 5f), b.climateAt(10f, 20f, 5f))
        assertTrue(a.climateAt(10f, 20f, 60f)[0] < a.climateAt(10f, 20f, 0f)[0])
    }

    @Test
    fun LayerRules_WeightsNormalize_AndPackTo255() {
        val rule = LayerRule(1, minSlope = 30f, blendSlope = 10f)
        assertEquals(1f, rule.weightAt(0f, 40f)); assertEquals(0.5f, rule.weightAt(0f, 25f), 1e-6f); assertEquals(0f, rule.weightAt(0f, 10f))
        val w = TerrainWeights.layerWeights(biomes, floatArrayOf(1f, 0f, 0f), 10f, 45f, FloatArray(8))
        assertEquals(1f, w.sum(), 1e-5f)
        assertEquals(1f, w[BiomePresets.ROCK], 1e-5f, "steep grassland is rock")
        val none = TerrainWeights.layerWeights(listOf(Biome(layerRules = emptyList())), floatArrayOf(1f), 0f, 0f, FloatArray(8))
        assertEquals(1f, none[0], "no rule: layer 0")
        val bytes = ByteArray(8)
        TerrainWeights.pack(floatArrayOf(0.333f, 0.333f, 0.334f, 0f, 0f, 0f, 0f, 0f), bytes, 0)
        assertEquals(255, bytes.sumOf { it.toInt() and 255 })
        val layers = IntArray(4); val top = FloatArray(4)
        TerrainWeights.topFour(floatArrayOf(0.1f, 0.3f, 0.05f, 0.2f, 0.15f, 0.1f, 0.05f, 0.05f), layers, top)
        assertArrayEquals(intArrayOf(1, 3, 4, 0), layers)
        assertEquals(1f, top.sum(), 1e-5f)
    }

    @Test
    fun Chunks_CarrySplatAndBiomes_ContinuousAcrossEdges() {
        val g = generator()
        val a = TerrainChunkBuilder.build(g, 1, 1)
        val b = TerrainChunkBuilder.build(g, 2, 1)
        val n = a.chunkQuads + 1
        val splat = a.splat!!
        for (p in 0 until n * n) assertEquals(255, (0 until 8).sumOf { splat[p * 8 + it].toInt() and 255 })
        for (j in 0 until n) for (l in 0 until 8) {
            assertEquals(a.splat!![(j * n + a.chunkQuads) * 8 + l], b.splat!![(j * n) * 8 + l], "shared edge weights")
        }
        assertTrue(a.biomeIds!!.all { it in 0 until biomes.size })
        // No biomes: no splat, height/slope shading
        assertEquals(null, TerrainChunkBuilder.build(generator(emptyList()), 0, 0).splat)
    }

    @Test
    fun PaintedBiomes_OverrideTheClimateMap() {
        val g = generator(source = BiomeSource.PAINTED)
        val c = TerrainChunkBuilder.build(g, 1, 1)
        c.paintedBiomeIds = ByteArray((c.chunkQuads + 1) * (c.chunkQuads + 1)) { 1 }
        TerrainChunkBuilder.refreshDerived(g, c)
        assertTrue(c.biomeIds!!.all { it.toInt() == 1 })
    }

    @Test
    fun Macro_AveragesLayerColoursPerTexel() {
        val g = generator()
        val chunks = TerrainChunkBuilder.buildAll(g, parallel = false)
        val colors = List(8) { floatArrayOf(0.5f, 0.5f, 0.5f) }
        val macro = TerrainMacro.bake(g.layout, chunks, colors)
        assertEquals(4, macro.width) // 128 m / 32 m
        val c = macro.color(1, 2)
        for (ch in c) assertEquals(0.5f, ch, 0.01f)
    }

    private fun rocks(density: Float = 4f) = Biome(layerRules = listOf(LayerRule(0)), scatter = listOf(
        ScatterRule(id = "rocks", model = "m", densityPer100m2 = density, minSpacing = 3f, maxSlope = 90f),
    ))

    @Test
    fun Scatter_IsDeterministic_PerChunkAndRule() {
        val g = generator(listOf(rocks()))
        val a = TerrainChunkBuilder.build(g, 1, 2).scatter
        val b = TerrainChunkBuilder.build(g, 1, 2).scatter
        assertTrue(a.isNotEmpty())
        assertEquals(a, b)
        assertEquals(a, TerrainChunkBuilder.buildAll(g, parallel = true).first { it.cx == 1 && it.cz == 2 }.scatter)
        // Another rule does not move these items
        val more = generator(listOf(rocks().copy(scatter = rocks().scatter + ScatterRule(id = "bushes", model = "b", densityPer100m2 = 8f, minSpacing = 2f))))
        assertEquals(a, TerrainChunkBuilder.build(more, 1, 2).scatter.filter { it.rule == 0 })
    }

    @Test
    fun Scatter_RespectsSpacingFiltersAndStamps() {
        val chunk = TerrainChunkBuilder.build(generator(listOf(rocks())), 1, 1)
        val items = chunk.scatter
        // One item per grid cell at most: no two closer than... the same cell
        assertEquals(items.size, items.map { it.key }.toSet().size)
        items.forEach { assertTrue(it.x >= chunk.cornerX && it.x < chunk.cornerX + 32f) }
        val flatOnly = generator(listOf(rocks().copy(scatter = listOf(rocks().scatter[0].copy(maxSlope = 0.5f)))))
        assertTrue(TerrainChunkBuilder.build(flatOnly, 1, 1).scatter.size < items.size)
        val stamp = TerrainStamp(x = chunk.cornerX + 16f, z = chunk.cornerZ + 16f, width = 40f, depth = 40f, y = 5f)
        assertTrue(TerrainChunkBuilder.build(generator(listOf(rocks()), listOf(stamp)), 1, 1).scatter.isEmpty(), "no rocks on stamped ground")
        assertTrue(TerrainChunkBuilder.build(generator(listOf(rocks(0f))), 1, 1).scatter.isEmpty())
    }

    @Test
    fun Scatter_ItemsSitOnTheGround() {
        val chunk = TerrainChunkBuilder.build(generator(listOf(rocks())), 1, 1)
        val field = ChunkedHeightField(TerrainLayout(128f, 1f, 32), { cx, cz -> if (cx == 1 && cz == 1) chunk else null })
        chunk.scatter.forEach { assertTrue(abs(field.heightAt(it.x, it.z) - it.y) < 1e-3f) }
    }

    @Test
    fun ShowcaseIsland_IsSmallProceduralWithAPad() {
        val t = TerrainComponent.showcaseIsland(padX = -18f, padZ = 10f, padHeight = 3f)
        val rt = t.runtime
        rt.ensureBuilt(t.key(seed), parallel = false)
        assertEquals(96f, rt.layout!!.size)
        assertEquals(3f, rt.heightField!!.heightAt(-18f, 10f), 1e-3f)
        assertTrue(rt.heightField!!.heightAt(47f, 47f) < 0f, "the edge is under sea level")
        val before = rt.heightField!!.heightAt(5f, 5f)
        t.reroll()
        rt.ensureBuilt(t.key(seed), parallel = false)
        assertTrue(rt.heightField!!.heightAt(5f, 5f) != before, "reroll changes the island")
        assertTrue(rt.chunks.any { it.scatter.isNotEmpty() } || rt.chunks.all { it.splat != null })
    }
}
