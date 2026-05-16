package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.assets.loaders.ShaderLoader
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertNotNull
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
import org.lwjgl.opengl.GL
import org.lwjgl.system.MemoryUtil.NULL
import java.io.File

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
