package com.pafoid.skate.engine.procedural

import com.pafoid.skate.engine.ecs.random.RandomService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** REN-28 §5.1: procedural seeds derive from the ECS-14 project seed and a name, never from a sequential stream. */
class ProceduralSeedsTest {

    @Test
    fun seed_IsTheEcs14StreamSeed() {
        assertEquals(RandomService.streamSeed(99L, "terrain/island"), ProceduralSeeds.seed(99L, "terrain/island"))
        assertEquals(RandomService.streamSeed(99L, "terrain/island"), ProceduralSeeds.seed(99L, "terrain/island", offset = 0))
    }

    @Test
    fun noiseSeed_FoldsBothHalves() {
        assertEquals(0, ProceduralSeeds.fold(0L))
        assertEquals(0x12345678 xor 0x0ABCDEF0, ProceduralSeeds.fold(0x0ABCDEF0_12345678L))
        assertEquals(-1 xor 0, ProceduralSeeds.fold(0x00000000_FFFFFFFFL))
        val s = ProceduralSeeds.seed(7L, "terrain/a")
        assertEquals(ProceduralSeeds.fold(s), ProceduralSeeds.noiseSeed(7L, "terrain/a"))
    }

    @Test
    fun seeds_DependOnProjectSeedNameAndOffset_Only() {
        val base = ProceduralSeeds.noiseSeed(1L, "terrain/a")
        assertEquals(base, ProceduralSeeds.noiseSeed(1L, "terrain/a"))
        assertNotEquals(base, ProceduralSeeds.noiseSeed(2L, "terrain/a"))
        assertNotEquals(base, ProceduralSeeds.noiseSeed(1L, "terrain/b"))
        assertNotEquals(base, ProceduralSeeds.noiseSeed(1L, "terrain/a", offset = 1))
        assertNotEquals(ProceduralSeeds.noiseSeed(1L, "terrain/a", offset = 1), ProceduralSeeds.noiseSeed(1L, "terrain/a", offset = 2))
    }

    /** Drawing from ECS-14 streams (gameplay, other systems) never changes procedural seeds. */
    @Test
    fun seeds_IgnoreSequentialStreamDraws() {
        val before = ProceduralSeeds.noiseSeed(5L, "terrain/a")
        val service = RandomService(5L)
        repeat(1000) { service.stream("terrain/a").nextLong() }
        assertEquals(before, ProceduralSeeds.noiseSeed(service.seed, "terrain/a"))
    }

    @Test
    fun chunkSeeds_AreStableAndDistinctPerChunk() {
        val seeds = buildSet { for (x in -16..16) for (z in -16..16) add(ProceduralSeeds.chunkSeed(3L, "terrain/a/scatter/rocks", x, z)) }
        assertEquals(33 * 33, seeds.size)
        assertEquals(ProceduralSeeds.seed(3L, "terrain/a/scatter/rocks/-2,5"), ProceduralSeeds.chunkSeed(3L, "terrain/a/scatter/rocks", -2, 5))
        assertNotEquals(ProceduralSeeds.chunkSeed(3L, "n", 1, 12), ProceduralSeeds.chunkSeed(3L, "n", 11, 2))
    }

    @Test
    fun field_IsSeededFromTheName() {
        val s = NoiseSettings()
        val a = ProceduralSeeds.field(s, 10L, "terrain/a/height")
        assertEquals(ProceduralSeeds.noiseSeed(10L, "terrain/a/height"), a.seed)
        assertEquals(a.sample(5f, 9f), ProceduralSeeds.field(s, 10L, "terrain/a/height").sample(5f, 9f))
        assertNotEquals(a.sample(5f, 9f), ProceduralSeeds.field(s, 10L, "terrain/a/moisture").sample(5f, 9f))
    }

    /** engine/procedural is pure logic shared by render, gameplay and scripts: no GL, editor, ImGui or renderer. */
    @Test
    fun proceduralPackage_HasNoGlEditorOrRenderDependencies() {
        val roots = listOf(File("src/main/kotlin/com/pafoid/skate/engine/procedural"), File("src/main/java/com/pafoid/skate/engine/procedural"))
        val files = roots.flatMap { r -> r.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList() }
        assertTrue(files.size >= 4, "found $files")
        val forbidden = listOf("org.lwjgl.", "imgui.", "com.pafoid.skate.editor.", "com.pafoid.skate.engine.render.")
        for (f in files) {
            val imports = f.readLines().filter { it.startsWith("import ") }
            assertEquals(emptyList<String>(), imports.filter { line -> forbidden.any { it in line } }, f.path)
        }
    }
}
