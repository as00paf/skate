package com.pafoid.skate.engine.assets.loaders

import com.pafoid.skate.engine.assets.AssetId
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.ShaderCompileError
import com.pafoid.skate.engine.render.rhi.ShaderStage
import com.pafoid.skate.engine.render.shader.ShaderLoadException
import com.pafoid.skate.engine.render.shader.ShaderOrigin
import com.pafoid.skate.engine.render.shader.ShaderSourceResolver
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files

/** REN-31: ShaderLoader end to end over the resolver (overrides, includes, dependencies, line map, Vulkan subset). */
class ShaderLoaderResolutionTest {
    private val engineRoot = Files.createTempDirectory("engine").toFile()
    private val project = Files.createTempDirectory("project").toFile()
    private val device = FakeRhiDevice()
    private val logged = ArrayList<String>()
    private val logger = mockk<LoggerService>(relaxed = true).also {
        every { it.log(any<String>(), any()) } answers { logged += firstArg<String>() }
    }
    private val defaults = EngineDefaultResolver(engineRoot) { project }
    private val loader = ShaderLoader(
        logger = logger,
        sources = ShaderSourceResolver(engineDefaults = { defaults }, projectFile = { File(project, it) }, onError = { logged += it }),
        device = { device },
    )
    private val projectShaders = File(ProjectLayout.assetsDir(project), "Shaders").apply { mkdirs() }

    private val post = """
        // @skate-contract 1
        #type vertex
        #version 330 core
        layout (location=0) in vec2 aPos;
        void main() { gl_Position = vec4(aPos, 0.0, 1.0); }
        #type fragment
        #version 330 core
        #include "fog.glsl"
        out vec4 color;
        void main() { color = vec4(fogTint(), 1.0); }
    """.trimIndent()

    init {
        File(engineRoot, "assets/Shaders").apply { mkdirs() }.let { dir ->
            File(dir, "post_bloom.glsl").writeText(post)
            File(dir, "fog.glsl").writeText("// @skate-contract 1\nuniform vec3 uFogColor;\nvec3 fogTint() { return uFogColor; }")
        }
    }

    @AfterEach
    fun cleanup() {
        engineRoot.deleteRecursively()
        project.deleteRecursively()
    }

    private fun override(name: String, text: String) {
        val file = File(projectShaders, name).apply { writeText(text) }
        AssetMeta.write(file, AssetMeta(guid = AssetId.generate(), settings = mapOf(
            EngineDefaults.META_SOURCE to "Shaders/$name", EngineDefaults.META_SHA256 to "copied-from-an-older-engine",
        )))
        defaults.invalidate()
    }

    @Test
    fun engineShader_ListsItsIncludesAsDependencies() {
        val loaded = loader.load(EngineDefaults.Shaders.POST_BLOOM)
        assertEquals(setOf(EngineDefaults.Shaders.POST_BLOOM, EngineDefaults.Shaders.FOG), loaded.dependencies)
        assertEquals(setOf(ShaderOrigin.ENGINE_FILE), loaded.origins)
        assertTrue(device.shaders.single().desc.fragmentSource.contains("uniform vec3 uFogColor;"))
    }

    @Test
    fun includeOverride_ChangesEveryIncluder() {
        override("fog.glsl", "// @skate-contract 1\nuniform vec3 uFogColor;\nvec3 fogTint() { return uFogColor * 0.5; }")
        val loaded = loader.load(EngineDefaults.Shaders.POST_BLOOM)
        assertTrue(device.shaders.single().desc.fragmentSource.contains("uFogColor * 0.5"))
        assertTrue(ShaderOrigin.PROJECT_OVERRIDE in loaded.origins)
    }

    @Test
    fun loadEngine_IgnoresBrokenOverrides() {
        override("post_bloom.glsl", "// @skate-contract 1\nnot a shader")
        assertThrows<ShaderLoadException> { loader.load(EngineDefaults.Shaders.POST_BLOOM) }
        loader.loadEngine(EngineDefaults.Shaders.POST_BLOOM)
        assertTrue(device.shaders.single().desc.fragmentSource.contains("fogTint()"))
    }

    @Test
    fun compileError_InAnOverride_NamesTheFileAndOriginalLine() {
        override("post_bloom.glsl", post.replace("color = vec4(fogTint(), 1.0);", "color = oops;"))
        device.shaderError = ShaderCompileError(ShaderStage.FRAGMENT, "ERROR: 0:7: 'oops' : undeclared identifier")
        val e = assertThrows<ShaderLoadException> { loader.load(EngineDefaults.Shaders.POST_BLOOM) }
        // Fragment output line 7 = #version(2) + fog include (3 lines) ... -> post_bloom.glsl line 10
        assertTrue(e.message!!.contains("post_bloom.glsl:10: 'oops'"), e.message)
        assertTrue(e.message!!.contains(File(projectShaders, "post_bloom.glsl").path), e.message)
        assertTrue(EngineDefaults.Shaders.FOG in e.dependencies)
    }

    @Test
    fun projectShader_OutsideTheVulkanSubset_IsReported_ButStillLoadsOnGl() {
        File(projectShaders, "mine.glsl").writeText(post.replace("out vec4 color;", "uniform float uGain = 2.0;\nout vec4 color;"))
        loader.load("Assets/Shaders/mine.glsl")
        assertEquals(1, device.shaders.size)
        assertTrue(logged.any { it.contains("outside the GLSL subset") && it.contains("mine.glsl") }, logged.toString())
    }

    @Test
    fun engineShaders_AreNotSubsetChecked_AndValidProjectShadersAreQuiet() {
        File(projectShaders, "ok.glsl").writeText(post)
        loader.load("Assets/Shaders/ok.glsl")
        loader.load(EngineDefaults.Shaders.POST_BLOOM)
        assertFalse(logged.any { it.contains("outside the GLSL subset") }, logged.toString())
    }

    @Test
    fun missingShader_IsALoadErrorWithItsId() {
        val e = assertThrows<ShaderLoadException> { loader.load("Assets/Shaders/none.glsl") }
        assertEquals(setOf("Assets/Shaders/none.glsl"), e.dependencies)
    }
}
