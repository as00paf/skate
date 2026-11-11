package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.assets.loaders.ShaderLoader
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_FALSE
import org.lwjgl.glfw.GLFW.GLFW_VISIBLE
import org.lwjgl.glfw.GLFW.glfwCreateWindow
import org.lwjgl.glfw.GLFW.glfwDefaultWindowHints
import org.lwjgl.glfw.GLFW.glfwDestroyWindow
import org.lwjgl.glfw.GLFW.glfwInit
import org.lwjgl.glfw.GLFW.glfwMakeContextCurrent
import org.lwjgl.glfw.GLFW.glfwTerminate
import org.lwjgl.glfw.GLFW.glfwWindowHint
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import org.lwjgl.system.MemoryUtil.NULL
import com.pafoid.skate.testing.GpuTest
import java.io.File

/** Compiles every engine shader on a real GL context (developer machines only, see [GpuTest]). */
@GpuTest
class ShaderValidationTest {

    companion object {
        private var window: Long = NULL
        private var device: OpenGlDevice? = null

        @BeforeAll
        @JvmStatic
        fun setup() {
            if (!glfwInit()) {
                throw IllegalStateException("Unable to initialize GLFW")
            }
            glfwDefaultWindowHints()
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
            window = glfwCreateWindow(640, 480, "Headless Test", NULL, NULL)
            if (window == NULL) {
                throw IllegalStateException("Failed to create the GLFW window")
            }
            glfwMakeContextCurrent(window)
            device = OpenGlDevice.create()
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            device?.close()
            if (window != NULL) {
                glfwDestroyWindow(window)
            }
            glfwTerminate()
        }
    }

    @Test
    fun `validate all shaders compile successfully`() {
        // The engine loads shaders from the classpath (/shaders/...); this folder is their source.
        // Include-only files (e.g. fog.glsl) have no `#type` sections; they are compiled through the shaders using them.
        val shaders = File("assets/Shaders").listFiles { _, name -> name.endsWith(".glsl") }.orEmpty()
            .filter { it.readText().contains("#type") }
        assertTrue(shaders.size >= 10, "no shaders found to validate: ${shaders.map { it.name }}")

        val loader = ShaderLoader()
        for (shaderFile in shaders.sortedBy { it.name }) {
            println("Validating shader: ${shaderFile.name}")
            val shader = loader.loadShader("/shaders/${shaderFile.name}")
            assertNotNull(shader, "Shader ${shaderFile.name} should load")
            shader.destroy()
        }
    }
}
