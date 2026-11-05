package com.pafoid.skate.engine.assets.loaders

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.render.rhi.FakeRhiDevice
import com.pafoid.skate.engine.render.rhi.ShaderCompileError
import com.pafoid.skate.engine.render.rhi.ShaderStage
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
        assertEquals("/shaders/post_gamma.glsl", desc.name)
        assertTrue(desc.vertexSource.trimStart().startsWith("#version"))
        assertTrue(desc.fragmentSource.contains("#define TEST_DEFINE"))
        assertEquals(device.shaders.single(), shader.rhi)
    }

    @Test
    fun CompileError_IsLoggedWithTheDriverLogAndRethrown() {
        device.shaderError = ShaderCompileError(ShaderStage.FRAGMENT, "0:12 syntax error")
        val e = assertThrows<ShaderCompileException> { loader.loadShader("/shaders/post_gamma.glsl") }
        assertEquals("Fragment compilation failed for '/shaders/post_gamma.glsl'", e.message)
        verify {
            logger.log("Fragment compilation failed for '/shaders/post_gamma.glsl': 0:12 syntax error", LoggerService.LogLevel.ERROR)
        }
    }
}
