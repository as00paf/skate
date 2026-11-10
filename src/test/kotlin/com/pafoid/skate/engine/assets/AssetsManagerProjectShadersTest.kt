package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.shader.ShaderFeature
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * REN-31 acceptance, GL-free (fake device): editing a project shader changes the program in place (live); an
 * incompatible or broken edited override falls back to the engine shader with an error; unedited copies follow the
 * engine. Uses the real hot-reload path (`pollShaderChanges`), so each step waits for two stable polls (~1 s).
 */
class AssetsManagerProjectShadersTest {
    private val engineRoot = Files.createTempDirectory("engine").toFile()
    private val project = Files.createTempDirectory("project").toFile()
    private val device = FakeRhiDevice()
    private val errors = ArrayList<String>()
    private val logger = mockk<LoggerService>(relaxed = true).also {
        every { it.log(any<String>(), LoggerService.LogLevel.ERROR) } answers { errors += firstArg<String>() }
    }
    private val engineShaders = File(engineRoot, "assets/Shaders").apply { mkdirs() }
    private val copy = File(ProjectLayout.assetsDir(project), "Shaders/post_bloom.glsl")
    private lateinit var assets: AssetsManager

    private fun bloom(contract: Int, body: String) = """
        // @skate-contract $contract
        #type vertex
        #version 330 core
        void main() { gl_Position = vec4(0.0); }
        #type fragment
        #version 330 core
        out vec4 color;
        void main() { color = vec4($body); }
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        RhiContext.device = device
        File(engineShaders, "post_bloom.glsl").writeText(bloom(1, "ENGINE_V1"))
        // The basic shader copy a new project gets (AST-27): unchanged, so it follows the engine
        copy.parentFile.mkdirs()
        copy.writeText(bloom(1, "ENGINE_V1"))
        AssetMeta.write(copy, AssetMeta(guid = AssetId.generate(), settings = mapOf(
            EngineDefaults.META_SOURCE to "Shaders/post_bloom.glsl",
            EngineDefaults.META_SHA256 to EngineDefaultResolver.sha256(copy),
        )))
        val paths = AssetPaths().apply {
            projectRoot = project
            engineDefaults = EngineDefaultResolver(engineRoot) { project }
        }
        assets = AssetsManager(Serializer(paths), logger)
    }

    @AfterEach
    fun tearDown() {
        assets.unloadAll()
        RhiContext.device = null
        engineRoot.deleteRecursively()
        project.deleteRecursively()
    }

    private fun Shader.fragment() = rhi!!.desc.fragmentSource

    private fun edit(file: File, text: String) {
        file.writeText(text)
        file.setLastModified(file.lastModified() + 2_000) // filesystems with coarse timestamps
    }

    /** Polls like the engine loop until [done] or 5 s. */
    private fun pollUntil(done: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end) {
            assets.pollShaderChanges()
            if (done()) return true
            Thread.sleep(50)
        }
        return false
    }

    @Test
    fun editingTheProjectCopy_ChangesTheShaderLive_AndAStaleContractFallsBackWithAnError() {
        val shader = assets.getShader(EngineDefaults.Shaders.POST_BLOOM)
        val variant = assets.getShaderVariant(EngineDefaults.Shaders.POST_BLOOM, listOf(ShaderFeature.FOG))
        assertTrue(shader.fragment().contains("ENGINE_V1"))
        assets.pollShaderChanges() // baseline

        edit(copy, bloom(1, "MY_EDIT"))
        assertTrue(pollUntil { shader.fragment().contains("MY_EDIT") }, "the edit reached the program")
        assertSame(shader, assets.getShader("/shaders/post_bloom.glsl"), "same object, recompiled in place")
        assertTrue(variant.fragment().contains("MY_EDIT") && variant.fragment().contains("#define FOG"))
        assertTrue(errors.isEmpty(), errors.toString())

        // The engine moves to contract 2: the edited override is incompatible and falls back, with an error
        edit(File(engineShaders, "post_bloom.glsl"), bloom(2, "ENGINE_V2"))
        assertTrue(pollUntil { shader.fragment().contains("ENGINE_V2") }, "fell back to the engine shader")
        assertTrue(errors.any { it.contains("@skate-contract 1") && it.contains("needs 2") }, errors.toString())

        // The user merges and bumps the header: the override is used again
        edit(copy, bloom(2, "MERGED"))
        assertTrue(pollUntil { shader.fragment().contains("MERGED") })
    }

    @Test
    fun brokenOverride_FallsBackToTheEngineVersionOfTheSameShader() {
        val shader = assets.getShader(EngineDefaults.Shaders.POST_BLOOM)
        assets.pollShaderChanges()
        edit(copy, "// @skate-contract 1\nthis is not a #type file")
        assertTrue(pollUntil { errors.isNotEmpty() })
        assertTrue(shader.fragment().contains("ENGINE_V1"), "kept or fell back to the engine program, never another shader")
        assertTrue(assets.shaderUsesEngineFallback(EngineDefaults.Shaders.POST_BLOOM) || errors.any { it.contains("keeping the previous version") }, errors.toString())

        // A fresh load of the broken override gets the engine version of post_bloom, not shader_3d_default
        assets.unloadShader(EngineDefaults.Shaders.POST_BLOOM)
        val reloaded = assets.getShader(EngineDefaults.Shaders.POST_BLOOM)
        assertTrue(reloaded.fragment().contains("ENGINE_V1"))
        assertTrue(assets.shaderUsesEngineFallback(EngineDefaults.Shaders.POST_BLOOM))
    }

    @Test
    fun uneditedCopy_FollowsTheEngine() {
        val shader = assets.getShader(EngineDefaults.Shaders.POST_BLOOM)
        assets.pollShaderChanges()
        edit(File(engineShaders, "post_bloom.glsl"), bloom(1, "ENGINE_V1_1"))
        assertTrue(pollUntil { shader.fragment().contains("ENGINE_V1_1") }, "the unchanged copy is not an override")
        assertFalse(assets.shaderUsesEngineFallback(EngineDefaults.Shaders.POST_BLOOM))
        assertTrue(errors.isEmpty(), errors.toString())
    }

    @Test
    fun materialShader_FallsBackToTheSchemaShader_AndIsRetriedOnceFixed() {
        val mine = File(ProjectLayout.assetsDir(project), "Shaders/mine.glsl").apply { parentFile.mkdirs(); writeText("broken") }
        assertTrue(assets.materialShader("Assets/Shaders/mine.glsl") == null)
        assertTrue(assets.materialShader("Assets/Shaders/mine.glsl") == null)
        assertTrue(errors.count { it.contains("can't be used") } == 1, "logged once: $errors")
        assets.pollShaderChanges()
        edit(mine, com.pafoid.skate.engine.render.shader.ShaderTemplates.PBR_MATERIAL)
        assertTrue(pollUntil { assets.materialShader("Assets/Shaders/mine.glsl") != null }, "retried after the fix")
    }

    @Test
    fun materialShader_MissingSchemaUniforms_AreAWarning() {
        val warnings = ArrayList<String>()
        every { logger.log(any<String>(), LoggerService.LogLevel.WARN) } answers { warnings += firstArg<String>() }
        File(ProjectLayout.assetsDir(project), "Shaders/plain.glsl").apply { parentFile.mkdirs(); writeText(bloom(1, "1.0")) }
        assertTrue(assets.materialShader("Assets/Shaders/plain.glsl") != null)
        assets.materialShader("Assets/Shaders/plain.glsl")
        assertTrue(warnings.single().contains("u_MetallicFactor"), warnings.toString())
    }
}
