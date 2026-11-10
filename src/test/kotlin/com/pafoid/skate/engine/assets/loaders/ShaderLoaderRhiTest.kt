package com.pafoid.skate.engine.assets.loaders

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.ShaderCompileError
import com.pafoid.skate.engine.render.rhi.ShaderStage
import com.pafoid.skate.engine.render.shader.ShaderLoadException
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** ShaderLoader builds its programs through the RHI device (REN-12 step 5). */
class ShaderLoaderRhiTest {
    private val device = FakeRhiDevice()
    private val logger = mockk<LoggerService>(relaxed = true)
    private val loader = ShaderLoader(logger = logger, device = { device })

    @Test
    fun LoadShader_PassesThePreprocessedStagesToTheDevice() {
        val shader = loader.loadShader("/shaders/post_gamma.glsl", setOf("TEST_DEFINE"))
        val desc = device.shaders.single().desc
        assertEquals("engine:Shaders/post_gamma.glsl", desc.name) // canonical reference (REN-31)
        assertTrue(desc.vertexSource.trimStart().startsWith("#version"))
        assertTrue(desc.fragmentSource.contains("#define TEST_DEFINE"))
        assertEquals(device.shaders.single(), shader.rhi)
    }

    @Test
    fun CompileError_IsLoggedWithTheDriverLogAndRethrown() {
        device.shaderError = ShaderCompileError(ShaderStage.FRAGMENT, "0:12 syntax error")
        // REN-31: wrapped with the files the load read, so hot reload can retry once one of them changes
        val e = assertThrows<ShaderLoadException> { loader.loadShader("/shaders/post_gamma.glsl") }
        assertTrue(e.cause is ShaderCompileException)
        assertEquals("engine:Shaders/post_gamma.glsl" in e.dependencies, true)
        assertEquals("Fragment compilation failed for 'engine:Shaders/post_gamma.glsl': 0:12 syntax error", e.message)
        verify {
            logger.log("Fragment compilation failed for 'engine:Shaders/post_gamma.glsl': 0:12 syntax error", LoggerService.LogLevel.ERROR)
        }
    }
}
