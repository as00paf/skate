package com.pafoid.skate.engine.render.shader

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShaderVariantsTest {
    private val none: (String) -> String? = { null }

    @Test
    fun ShaderVariant_KeyIsOrderIndependent() {
        val a = ShaderVariantKey.of("/s.glsl", listOf(ShaderFeature.SHADOWED, ShaderFeature.FOG))
        val b = ShaderVariantKey.of("/s.glsl", listOf(ShaderFeature.FOG, ShaderFeature.SHADOWED))
        assertEquals(a, b)
        assertEquals("/s.glsl?FOG&SHADOWED", a.id)
        assertEquals("/s.glsl", ShaderVariantKey.of("/s.glsl", emptyList()).id)
    }

    @Test
    fun ShaderPreprocessor_DefinesGoAfterVersionLine() {
        val out = ShaderPreprocessor.process("#version 330 core\nvoid main(){}\n", setOf("FOG", "SKINNED"), "x", none)
        assertEquals("#version 330 core\n#define FOG\n#define SKINNED\nvoid main(){}\n", out)
    }

    @Test
    fun ShaderPreprocessor_DefinesWithoutVersionArePrepended() {
        assertEquals("#define A\nfoo\n", ShaderPreprocessor.process("foo\n", setOf("A"), "x", none))
    }

    @Test
    fun ShaderPreprocessor_ExpandsNestedIncludesOnce() {
        val files = mapOf("a.glsl" to "#include \"b.glsl\"\nA", "b.glsl" to "B")
        val out = ShaderPreprocessor.process("#include \"a.glsl\"\n#include \"b.glsl\"\nmain", emptySet(), "m") { files[it] }
        assertEquals("B\nA\nmain", out)
    }

    @Test
    fun ShaderPreprocessor_RejectsCyclesMissingAndTraversal() {
        val files = mapOf("a" to "#include \"b\"", "b" to "#include \"a\"")
        assertFailsWith<ShaderPreprocessException> { ShaderPreprocessor.process("#include \"a\"", emptySet(), "m") { files[it] } }
        assertFailsWith<ShaderPreprocessException> { ShaderPreprocessor.process("#include \"nope\"", emptySet(), "m", none) }
        assertFailsWith<ShaderPreprocessException> { ShaderPreprocessor.process("#include \"../x\"", emptySet(), "m") { "" } }
    }

    @Test
    fun ShaderVariantCache_CompilesEachKeyOnce() {
        val cache = ShaderVariantCache<String>({ "prog:${it.id}" }, {})
        val k = ShaderVariantKey.of("/s", listOf(ShaderFeature.FOG))
        assertEquals(cache.get(k), cache.get(k))
        cache.get(ShaderVariantKey.of("/s", emptyList()))
        assertEquals(2, cache.compileCount)
    }

    @Test
    fun ShaderVariantCache_ReloadKeepsOldProgramOnFailure() {
        var broken = false
        val swapped = mutableListOf<String>()
        val cache = ShaderVariantCache<String>({ if (broken) error("syntax") else "p${it.id}" }, {})
        cache.get(ShaderVariantKey.of("/a", listOf(ShaderFeature.FOG)))
        broken = true
        val errors = cache.reload("/a") { _, n -> swapped += n }
        assertEquals(1, errors.size)
        assertTrue(swapped.isEmpty())
        broken = false
        assertTrue(cache.reload("/a") { _, n -> swapped += n }.isEmpty())
        assertEquals(1, swapped.size)
    }

    @Test
    fun ShaderVariantCache_DisposeAllDestroysEverything() {
        val destroyed = mutableListOf<String>()
        val cache = ShaderVariantCache<String>({ it.id }, { destroyed += it })
        cache.get(ShaderVariantKey.of("/a", emptyList())); cache.get(ShaderVariantKey.of("/b", emptyList()))
        cache.disposeAll()
        assertEquals(2, destroyed.size); assertEquals(0, cache.size)
    }

    @Test
    fun ShaderHotReload_ReportsOnlyChangedFilesAndThrottles() {
        val dir = Files.createTempDirectory("shaders").toFile()
        val f = File(dir, "a.glsl").apply { writeText("1"); setLastModified(1000) }
        File(dir, "b.glsl").apply { writeText("1"); setLastModified(1000) }
        var now = 0L
        val seen = mutableListOf<List<String>>()
        val r = ShaderHotReloader(dir, 500, { now }) { seen += it }
        now = 1000; r.poll()
        assertTrue(seen.isEmpty())
        f.setLastModified(5000)
        now = 1100; r.poll()
        assertTrue(seen.isEmpty(), "within interval")
        now = 1600; r.poll()
        assertEquals(listOf(listOf("a.glsl")), seen)
        now = 3000; r.poll()
        assertEquals(1, seen.size)
        dir.deleteRecursively()
    }
}
