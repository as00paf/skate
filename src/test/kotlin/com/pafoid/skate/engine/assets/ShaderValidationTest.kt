package com.pafoid.skate.engine.assets

import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.system.MemoryUtil.NULL
import org.junit.jupiter.api.*
import java.io.File
import org.junit.jupiter.api.Assertions.assertNotNull

class ShaderValidationTest {

    companion object {
        private var window: Long = NULL

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
            GL.createCapabilities()
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            if (window != NULL) {
                glfwDestroyWindow(window)
            }
            glfwTerminate()
        }
    }

    @Test
    fun `validate all shaders compile successfully`() {
        val shaderDir = File("assets/shaders")
        val shaders = shaderDir.listFiles { _, name -> name.endsWith(".glsl") } ?: emptyArray()
        
        val loader = ShaderLoader()
        for (shaderFile in shaders) {
            println("Validating shader: ${shaderFile.path}")
            val shader = loader.loadShader(shaderFile.path)
            assertNotNull(shader, "Shader ${shaderFile.name} should load")
            shader.destroy()
        }
    }
}
