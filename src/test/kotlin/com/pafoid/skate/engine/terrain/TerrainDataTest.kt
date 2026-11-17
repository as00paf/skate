package com.pafoid.skate.engine.terrain

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.TerrainSource
import com.pafoid.skate.engine.ecs.systems.TerrainSystem
import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.procedural.NoiseSettings
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** REN-28 task 3: terrain layout, recipe, chunks with aprons, the height field and the component. */
class TerrainDataTest {
    private val seed = 1234L

    private fun generator(
        recipe: TerrainRecipe = TerrainPreset.ISLAND.recipe(), stamps: List<TerrainStamp> = emptyList(),
        size: Float = 128f, spacing: Float = 1f, quads: Int = 32, seed: Long = this.seed, name: String = "terrain/T",
    ) = TerrainGenerator(recipe, stamps, TerrainLayout(size, spacing, quads), 40f, seed, name)

    @Test
    fun Layout_RoundsUpToWholeChunks_AndCentresOnTheTransform() {
        val l = TerrainLayout(100f, 1f, 32)
        assertEquals(4, l.chunksPerSide)
        assertEquals(128f, l.size)
        assertEquals(-64f, l.chunkCornerX(0)); assertEquals(32f, l.chunkCornerZ(3))
        assertEquals(33, l.samplesPerChunk)
        assertEquals(0, l.chunkX(-64f)); assertEquals(3, l.chunkX(64f)); assertEquals(2, l.chunkX(0.5f))
        assertThrows<IllegalArgumentException> { TerrainLayout(100f, 1f, 30) }
    }

    @Test
    fun Recipe_IsDeterministicPerSeedAndName_AndRerollsWithEither() {
        val a = generator(); val b = generator()
        val points = listOf(0f to 0f, 12.5f to -30f, -40f to 22f)
        for ((x, z) in points) assertEquals(a.heightAt(x, z), b.heightAt(x, z))
        assertTrue(points.any { (x, z) -> generator(seed = 99L).heightAt(x, z) != a.heightAt(x, z) })
        assertTrue(points.any { (x, z) -> generator(name = "terrain/Other").heightAt(x, z) != a.heightAt(x, z) })
    }

    @Test
    fun Recipe_ShapingSteps() {
        assertEquals(0f, generator(TerrainPreset.FLAT.recipe()).heightAt(10f, 10f))
        // Island: the edge drops to the floor, the centre stays above it
        val island = generator(TerrainRecipe(listOf(TerrainStep.Noise("n", NoiseSettings(), amplitude = 0f, offset = 0.5f),
            TerrainStep.Falloff(FalloffShape.ISLAND, 0.5f, 0.9f, floor = -0.1f))))
        assertEquals(0.5f * 40f, island.heightAt(0f, 0f), 1e-4f)
        assertEquals(-0.1f * 40f, island.heightAt(63f, 63f), 1e-4f)
        // Clamp, terrace, curve
        val clamped = generator(TerrainRecipe(listOf(TerrainStep.Noise("n", NoiseSettings(), 0f, 2f), TerrainStep.Clamp(0f, 1f))))
        assertEquals(40f, clamped.heightAt(1f, 1f))
        assertEquals(0.5f, TerrainGenerator.terrace(0.55f, 2, 1f), 1e-6f)
        assertEquals(0.25f, TerrainGenerator.terrace(0.25f, 1, 0f), 0.1f) // smooth ramp keeps the slope's middle
        assertEquals(0.2f, TerrainGenerator.curve(listOf(CurvePoint(0f, 0f), CurvePoint(0.4f, 0.2f), CurvePoint(1f, 1f)), 0.4f), 1e-6f)
        assertEquals(1f, TerrainGenerator.curve(listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)), 3f))
    }

    @Test
    fun Chunks_ShareEdgeSamples_AndTheirApronIsTheNeighboursInterior() {
        val g = generator()
        val left = TerrainChunkBuilder.build(g, 0, 1)
        val right = TerrainChunkBuilder.build(g, 1, 1)
        val q = left.chunkQuads
        for (j in -1..q + 1) {
            assertEquals(left.height(q, j), right.height(0, j), "shared edge row $j")
            assertEquals(left.height(q + 1, j), right.height(1, j), "apron = neighbour interior")
            assertEquals(left.height(q - 1, j), right.height(-1, j))
        }
        val n1 = left.normal(q, 5, Vector3f()); val n2 = right.normal(0, 5, Vector3f())
        assertTrue(n1.equals(n2, 1e-6f), "edge normals match")
    }

    @Test
    fun Chunks_BuildInParallelExactlyAsAlone() {
        val g = generator()
        val all = TerrainChunkBuilder.buildAll(g, parallel = true)
        assertEquals(16, all.size)
        val c = all.first { it.cx == 2 && it.cz == 3 }
        assertArrayEquals(TerrainChunkBuilder.build(g, 2, 3).heights, c.heights)
    }

    @Test
    fun ColliderHeights_FitTheHeightfieldShape() {
        val chunk = TerrainChunkBuilder.build(generator(), 1, 2)
        val n = chunk.chunkQuads + 1
        val shape = ColliderShape.Heightfield(n, chunk.spacing, chunk.colliderHeights())
        assertEquals(chunk.height(0, 0), shape.height(0, 0))
        assertEquals(chunk.height(7, 3), shape.height(7, 3))
        assertEquals(chunk.minHeight, shape.minY)
        assertEquals(chunk.maxHeight, shape.maxY)
    }

    @Test
    fun HeightField_HitsSamplesExactly_InterpolatesOnTheColliderDiagonal_AndFallsBack() {
        val g = generator()
        val runtime = TerrainRuntime()
        runtime.ensureBuilt(TerrainKey(128f, 1f, 32, 40f, TerrainPreset.ISLAND.recipe(), emptyList(), seed, "terrain/T", 0), parallel = false)
        val field = runtime.heightField!!
        val chunk = runtime.chunk(1, 1)!!
        val x = chunk.cornerX + 3; val z = chunk.cornerZ + 4
        assertEquals(chunk.height(3, 4), field.heightAt(x, z), 1e-5f)
        // On the (i, j)-(i+1, j+1) diagonal the height is the mean of its two ends
        assertEquals((chunk.height(3, 4) + chunk.height(4, 5)) / 2f, field.heightAt(x + 0.5f, z + 0.5f), 1e-4f)
        assertTrue(field.isDetailed(x, z))
        assertFalse(field.isDetailed(500f, 0f))
        assertEquals(g.heightAt(500f, 0f), field.heightAt(500f, 0f), 1e-4f, "outside: the generator fallback")
        val flat = ChunkedHeightField(TerrainLayout(64f, 1f, 32), { _, _ -> null })
        assertTrue(flat.normalAt(1f, 2f, Vector3f()).equals(Vector3f(0f, 1f, 0f), 1e-6f))
    }

    @Test
    fun Runtime_RebuildsOnlyWhenTheKeyChanges() {
        val terrain = TerrainComponent(sizeMetres = 64f, chunkQuads = 32)
        GameObject("Hill").addComponent(terrain)
        val rt = terrain.runtime
        assertTrue(rt.ensureBuilt(terrain.key(seed), parallel = false))
        val v = rt.version
        assertFalse(rt.ensureBuilt(terrain.key(seed), parallel = false))
        assertEquals("terrain/Hill", terrain.seedName)
        terrain.stamps += TerrainStamp(x = 0f, z = 0f, y = 3f)
        assertTrue(rt.ensureBuilt(terrain.key(seed), parallel = false))
        assertTrue(rt.version > v)
        assertEquals(3f, rt.heightField!!.heightAt(0f, 0f), 1e-4f)
        assertTrue(rt.ensureBuilt(terrain.key(seed + 1), parallel = false), "a new project seed rerolls")
    }

    @Test
    fun Component_SanitizesItsKey_AndDataSourceIgnoresTheRecipe() {
        val t = TerrainComponent(sizeMetres = 1e6f, sampleSpacing = 0.9f, chunkQuads = 100, seedName = "terrain/X")
        val key = t.key(seed)
        assertEquals(TerrainComponent.MAX_SIZE, key.size)
        assertEquals(1f, key.spacing)
        assertEquals(64, key.chunkQuads)
        t.source = TerrainSource.DATA
        assertEquals(TerrainPreset.FLAT.recipe(), t.key(seed).recipe)
    }

    @Test
    fun Component_RoundTripsThroughTheSceneSerializer() {
        val terrain = TerrainComponent(sizeMetres = 96f, seedName = "terrain/Island", seedOffset = 3)
        terrain.stamps += TerrainStamp(footprint = StampFootprint.CIRCLE, radius = 8f, y = 2f, mode = StampMode.RAISE_TO, priority = 2)
        terrain.applyPreset(TerrainPreset.MOUNTAINS)
        val json = Serializer().json
        val text = json.encodeToString(Component.serializer(), terrain)
        val back = json.decodeFromString(Component.serializer(), text) as TerrainComponent
        assertEquals(terrain, back)
        assertEquals(terrain.key(seed), back.key(seed))
    }

    @Test
    fun System_BuildsTerrainsOnce_AndAgainAfterAChange() {
        val terrain = TerrainComponent(sizeMetres = 64f, chunkQuads = 32)
        val scene = Scene("S")
        scene.addChild(GameObject("T").addComponent(terrain))
        val system = TerrainSystem().apply { init(scene) }
        system.update(0f)
        assertEquals(1, system.rebuilt)
        assertEquals(4, terrain.runtime.chunks.size)
        system.update(0f)
        assertEquals(0, system.rebuilt)
        terrain.heightScaleMetres = 10f
        system.update(0f)
        assertEquals(1, system.rebuilt)
        assertNotEquals(0L, terrain.runtime.version)
    }
}
