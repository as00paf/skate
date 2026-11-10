package com.pafoid.skate.engine.render.shader

import com.pafoid.skate.engine.assets.EngineDefaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** REN-31 / AST-26 §5.3, §5.5: one reference-keyed cache, same-reference fallback, dependency-based reload. */
class ShaderLibraryTest {
    /** A fake program: its source text; `swap` copies the fresh text into the cached object, like Shader.replaceWith. */
    class Program(var text: String)

    private val sources = HashMap<String, String>()      // what the "project" resolves to
    private val engineSources = HashMap<String, String>() // the engine's own files
    private val deps = HashMap<String, Set<String>>()
    private val destroyed = ArrayList<String>()
    private val log = ArrayList<String>()

    private fun build(from: Map<String, String>, ref: String, defines: Set<String>): ShaderLibrary.Loaded<Program> {
        val text = from[ref] ?: throw ShaderLoadException("missing $ref", setOf(ref))
        val d = setOf(ref) + deps[ref].orEmpty()
        if (text.contains("BROKEN")) throw ShaderLoadException("syntax error in $ref", d)
        return ShaderLibrary.Loaded(Program(text + defines.joinToString(prefix = "+")), d)
    }

    private val library = ShaderLibrary(
        load = { ref, defines -> build(sources, ref, defines) },
        loadEngine = { ref, defines -> build(engineSources, ref, defines) },
        swap = { old, fresh -> old.text = fresh.text },
        destroy = { destroyed += it.text },
        log = { log += it },
    )

    private val bloom = EngineDefaults.Shaders.POST_BLOOM
    private val default3d = EngineDefaults.Shaders.DEFAULT_3D

    init {
        listOf(bloom, default3d).forEach { sources[it] = "engine $it"; engineSources[it] = "engine $it" }
        deps[default3d] = setOf(EngineDefaults.Shaders.FOG, EngineDefaults.Shaders.LIGHTING)
    }

    @Test
    fun oneCache_KeyedByCanonicalReferenceAndDefines() {
        val a = library.get(bloom)
        assertSame(a, library.get("/shaders/post_bloom.glsl"))
        val v = library.get(bloom, setOf("B", "A"))
        assertSame(v, library.get(bloom, setOf("A", "B")))
        assertEquals(2, library.size)
        assertEquals(2, library.compileCount)
    }

    @Test
    fun brokenOverride_FallsBackToTheEngineVersionOfTheSameShader_NeverTheMeshShader() {
        sources[bloom] = "my bloom BROKEN"
        val p = library.get(bloom)
        assertEquals("engine $bloom+", p.text)
        assertTrue(library.usesFallback(bloom))
        assertTrue(log.single().contains("using the engine's version"))
        assertFalse(library.contains(default3d), "the mesh shader is not involved")
    }

    @Test
    fun brokenEngineShader_AndBrokenProjectShader_AreHardErrors() {
        sources[bloom] = "BROKEN"; engineSources[bloom] = "BROKEN"
        assertThrows<ShaderLoadException> { library.get(bloom) }
        sources["Assets/Shaders/mine.glsl"] = "BROKEN"
        assertThrows<ShaderLoadException> { library.get("Assets/Shaders/mine.glsl") }
        assertThrows<ShaderLoadException> { library.get("Assets/Shaders/missing.glsl") }
    }

    @Test
    fun reload_RecompilesExactlyTheDependents_InPlace() {
        val main = library.get(default3d)
        val mainVariant = library.get(default3d, setOf("SKINNED"))
        val post = library.get(bloom)
        sources[default3d] = "edited default"
        val errors = library.reload(setOf(EngineDefaults.Shaders.FOG))
        assertTrue(errors.isEmpty())
        assertEquals("edited default+", main.text)
        assertEquals("edited default+SKINNED", mainVariant.text)
        assertEquals("engine $bloom+", post.text, "does not include fog: untouched")
        assertEquals(setOf(ShaderVariantKey(default3d, emptySet()), ShaderVariantKey(default3d, setOf("SKINNED"))),
            library.dependents(setOf(EngineDefaults.Shaders.LIGHTING)).toSet())
    }

    @Test
    fun failedReload_KeepsThePreviousProgram() {
        val p = library.get("Assets/Shaders/mine.glsl".also { sources[it] = "v1" })
        sources["Assets/Shaders/mine.glsl"] = "v2 BROKEN"
        val errors = library.reload(setOf("Assets/Shaders/mine.glsl"))
        assertEquals(1, errors.size)
        assertEquals("v1+", p.text)
        sources["Assets/Shaders/mine.glsl"] = "v3"
        library.reload(setOf("Assets/Shaders/mine.glsl"))
        assertEquals("v3+", p.text)
    }

    @Test
    fun fixedOverride_IsPickedUpAgain_AfterAFallback() {
        sources[bloom] = "my bloom BROKEN"
        val p = library.get(bloom)
        assertTrue(bloom in library.dependencies(), "the failed override stays watched")
        sources[bloom] = "my bloom fixed"
        library.reload(setOf(bloom))
        assertEquals("my bloom fixed+", p.text)
        assertFalse(library.usesFallback(bloom))
    }

    @Test
    fun unloadAndDispose_FreePrograms_AndBumpTheDependencyVersion() {
        library.get(bloom); library.get(bloom, setOf("X")); library.get(default3d)
        val v = library.dependencyVersion
        assertTrue(library.unload("/shaders/post_bloom.glsl"))
        assertEquals(2, destroyed.size)
        assertTrue(library.dependencyVersion > v)
        library.disposeAll()
        assertEquals(3, destroyed.size)
        assertEquals(0, library.size)
    }
}
