package com.pafoid.skate.engine.render.shader

import com.pafoid.skate.engine.assets.AssetId
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.ProjectLayout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/** REN-31 / AST-26 §5.2–5.3: which file a shader reference reads, with project overrides, contracts and includes. */
class ShaderSourceResolverTest {
    private val engineRoot = Files.createTempDirectory("engine").toFile()
    private val project = Files.createTempDirectory("project").toFile()
    private val errors = ArrayList<String>()
    private var packed = mapOf<String, String>()
    private val defaults = EngineDefaultResolver(engineRoot) { project }
    private val resolver = ShaderSourceResolver(
        engineDefaults = { defaults },
        readPacked = { packed[it]?.toByteArray() },
        projectFile = { File(project, it) },
        onError = { errors += it },
    )

    private val engineShaders = File(engineRoot, "assets/Shaders").apply { mkdirs() }
    private val projectShaders = File(ProjectLayout.assetsDir(project), "Shaders").apply { mkdirs() }

    init {
        engine("shader_3d_default.glsl", "// @skate-contract 2\n#include \"fog.glsl\"\nENGINE DEFAULT")
        engine("fog.glsl", "// @skate-contract 1\nENGINE FOG")
        engine("post_bloom.glsl", "// @skate-contract 1\nENGINE BLOOM")
        engine("picking.glsl", "ENGINE PICKING")
    }

    @AfterEach
    fun cleanup() {
        engineRoot.deleteRecursively()
        project.deleteRecursively()
    }

    private fun engine(name: String, text: String) = File(engineShaders, name).writeText(text)

    /** A project copy of an engine shader: [copied] is what was copied (its hash goes in the .meta), [now] its text. */
    private fun copy(name: String, copied: String, now: String = copied, dir: File = projectShaders): File {
        val file = File(dir, name).apply { parentFile.mkdirs(); writeText(now) }
        AssetMeta.write(file, AssetMeta(
            guid = AssetId.generate(),
            settings = mapOf(
                EngineDefaults.META_SOURCE to "Shaders/$name",
                EngineDefaults.META_SHA256 to EngineDefaultResolver.sha256(copied.toByteArray()),
            ),
        ))
        defaults.invalidate()
        return file
    }

    @Test
    fun engineReference_WithoutOverride_ReadsTheEngineFile() {
        val s = resolver.resolve(EngineDefaults.Shaders.DEFAULT_3D)!!
        assertEquals(ShaderOrigin.ENGINE_FILE, s.origin)
        assertTrue(s.text.endsWith("ENGINE DEFAULT"))
        assertEquals(EngineDefaults.Shaders.DEFAULT_3D, ShaderSourceResolver.canonical("/shaders/shader_3d_default.glsl"))
        assertEquals(s.text, resolver.resolve("/shaders/shader_3d_default.glsl")!!.text)
    }

    @Test
    fun unchangedCopy_FollowsTheEngine() {
        val engineText = File(engineShaders, "post_bloom.glsl").readText()
        copy("post_bloom.glsl", engineText)
        assertEquals(ShaderOrigin.ENGINE_FILE, resolver.resolve(EngineDefaults.Shaders.POST_BLOOM)!!.origin)
        // ... also once the engine changed and the copy was not refreshed yet
        engine("post_bloom.glsl", "// @skate-contract 1\nENGINE BLOOM v2")
        assertTrue(resolver.resolve(EngineDefaults.Shaders.POST_BLOOM)!!.text.endsWith("v2"))
    }

    @Test
    fun modifiedCopy_WithACurrentContract_IsTheOverride_WhereverItWasMoved() {
        val original = File(engineShaders, "post_bloom.glsl").readText()
        copy("post_bloom.glsl", original, "// @skate-contract 1\nMY BLOOM", dir = File(ProjectLayout.assetsDir(project), "Elsewhere"))
        val s = resolver.resolve(EngineDefaults.Shaders.POST_BLOOM)!!
        assertEquals(ShaderOrigin.PROJECT_OVERRIDE, s.origin)
        assertTrue(s.text.endsWith("MY BLOOM"))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun modifiedCopy_WithAnOlderContract_FallsBackToTheEngine_AndReportsOnce() {
        val original = File(engineShaders, "shader_3d_default.glsl").readText()
        copy("shader_3d_default.glsl", original, "// @skate-contract 1\nOLD EDIT")
        repeat(3) {
            val s = resolver.resolve(EngineDefaults.Shaders.DEFAULT_3D)!!
            assertEquals(ShaderOrigin.ENGINE_FILE, s.origin)
        }
        assertEquals(1, errors.size)
        assertTrue(errors.single().contains("@skate-contract 1") && errors.single().contains("needs 2"), errors.single())
        // An override without any header counts as contract 0
        copy("shader_3d_default.glsl", original, "NO HEADER")
        assertEquals(ShaderOrigin.ENGINE_FILE, resolver.resolve(EngineDefaults.Shaders.DEFAULT_3D)!!.origin)
        assertEquals(2, errors.size)
    }

    @Test
    fun internalShaders_AreNeverOverridden() {
        copy("picking.glsl", "ENGINE PICKING", "MY PICKING")
        assertEquals("ENGINE PICKING", resolver.resolve(EngineDefaults.Shaders.PICKING)!!.text)
    }

    @Test
    fun includeInAnEngineShader_UsesTheIncludeOverride() {
        copy("fog.glsl", "// @skate-contract 1\nENGINE FOG", "// @skate-contract 1\nMY FOG")
        val fog = resolver.resolveInclude("fog.glsl", EngineDefaults.Shaders.DEFAULT_3D)!!
        assertEquals(EngineDefaults.Shaders.FOG, fog.id)
        assertTrue(fog.text.endsWith("MY FOG"))
    }

    @Test
    fun explicitEngineInclude_IsTheEngineOriginal_SoAnOverrideCanWrapIt() {
        copy("fog.glsl", "// @skate-contract 1\nENGINE FOG", "// @skate-contract 1\n#include \"engine:Shaders/fog.glsl\"\nMORE")
        val original = resolver.resolveInclude("engine:Shaders/fog.glsl", EngineDefaults.Shaders.FOG)!!
        assertTrue(original.text.endsWith("ENGINE FOG"))
        assertEquals(ShaderOrigin.ENGINE_FILE, original.origin)
    }

    @Test
    fun projectShader_IncludesItsSibling_ElseTheEngineShader_AndRecordsTheMiss() {
        File(projectShaders, "my_water.glsl").writeText("#include \"waves.glsl\"\n#include \"fog.glsl\"")
        File(projectShaders, "waves.glsl").writeText("MY WAVES")
        val id = "Assets/Shaders/my_water.glsl"
        assertEquals(ShaderOrigin.PROJECT, resolver.resolve(id)!!.origin)
        val misses = HashSet<String>()
        assertEquals("MY WAVES", resolver.resolveInclude("waves.glsl", id, misses)!!.text)
        val fog = resolver.resolveInclude("fog.glsl", id, misses)!!
        assertEquals(EngineDefaults.Shaders.FOG, fog.id)
        assertEquals(setOf("Assets/Shaders/fog.glsl"), misses)
    }

    @Test
    fun packEntry_ComesFirst_ButNotWithAStaleContract() {
        packed = mapOf(EngineDefaults.Shaders.POST_BLOOM to "// @skate-contract 1\nPACKED BLOOM")
        assertEquals(ShaderOrigin.PACK, resolver.resolve(EngineDefaults.Shaders.POST_BLOOM)!!.origin)
        packed = mapOf(EngineDefaults.Shaders.DEFAULT_3D to "// @skate-contract 1\nOLD PACKED")
        assertEquals(ShaderOrigin.ENGINE_FILE, resolver.resolve(EngineDefaults.Shaders.DEFAULT_3D)!!.origin)
        packed = mapOf("Assets/Shaders/x.glsl" to "PACKED PROJECT")
        assertEquals("PACKED PROJECT", resolver.resolve("Assets/Shaders/x.glsl")!!.text)
    }

    @Test
    fun engineOnly_IgnoresOverridesPackAndProjectShaders() {
        copy("post_bloom.glsl", "x", "// @skate-contract 1\nMY BLOOM")
        packed = mapOf(EngineDefaults.Shaders.FOG to "// @skate-contract 1\nPACKED")
        File(projectShaders, "mine.glsl").writeText("MINE")
        val engineOnly = resolver.engineOnly()
        assertTrue(engineOnly.resolve(EngineDefaults.Shaders.POST_BLOOM)!!.text.endsWith("ENGINE BLOOM"))
        assertTrue(engineOnly.resolveInclude("fog.glsl", EngineDefaults.Shaders.DEFAULT_3D)!!.text.endsWith("ENGINE FOG"))
        assertNull(engineOnly.resolve("Assets/Shaders/mine.glsl"))
    }

    @Test
    fun fingerprint_ChangesWhenTheEngineFileOrAnOverrideChanges() {
        val id = EngineDefaults.Shaders.POST_BLOOM
        val before = resolver.fingerprint(id)
        assertEquals(before, resolver.fingerprint(id))
        val copy = copy("post_bloom.glsl", "x", "// @skate-contract 1\nMY BLOOM")
        val withOverride = resolver.fingerprint(id)
        assertNotEquals(before, withOverride)
        copy.writeText("// @skate-contract 1\nMY BLOOM, EDITED")
        copy.setLastModified(copy.lastModified() + 5_000)
        assertNotEquals(withOverride, resolver.fingerprint(id))
    }

    @Test
    fun withoutEngineDefaults_ReadsTheJarCopy() {
        val classpathOnly = ShaderSourceResolver()
        val fog = classpathOnly.resolve(EngineDefaults.Shaders.FOG)!!
        assertEquals(ShaderOrigin.CLASSPATH, fog.origin)
        assertTrue(fog.text.contains("applyFog"))
        assertNull(classpathOnly.resolve("Assets/Shaders/nothing.glsl"))
    }
}
