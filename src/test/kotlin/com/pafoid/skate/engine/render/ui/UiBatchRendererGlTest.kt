package com.pafoid.skate.engine.render.ui

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.loaders.ShaderLoader
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.BufferUtils
import com.pafoid.skate.engine.render.rhi.opengl.OpenGlDevice
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_NEAREST
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glBlendFunc
import org.lwjgl.opengl.GL11.glClear
import org.lwjgl.opengl.GL11.glClearColor
import org.lwjgl.opengl.GL11.glDeleteTextures
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glGenTextures
import org.lwjgl.opengl.GL11.glReadPixels
import org.lwjgl.opengl.GL11.glTexImage2D
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glDeleteFramebuffers
import org.lwjgl.opengl.GL30.glFramebufferTexture2D
import org.lwjgl.opengl.GL30.glGenFramebuffers
import org.lwjgl.system.MemoryUtil.NULL

/**
 * Offscreen render of UI quads with the real `shader_2d_batch.glsl` and [UiBatchRenderer] (GUI-07).
 * A stray texture is left on unit 0 on purpose, as other passes do: untextured backgrounds must ignore it.
 */
@com.pafoid.skate.testing.GpuTest
class UiBatchRendererGlTest {

    companion object {
        private const val SIZE = 64
        private var window = NULL
        private var device: OpenGlDevice? = null

        @BeforeAll
        @JvmStatic
        fun setup() {
            if (!GLFW.glfwInit()) return
            GLFW.glfwDefaultWindowHints()
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
            window = GLFW.glfwCreateWindow(SIZE, SIZE, "UiBatchRendererGlTest", NULL, NULL)
            if (window == NULL) return
            GLFW.glfwMakeContextCurrent(window)
            device = OpenGlDevice.create()
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            device?.close()
            device = null
            if (window != NULL) GLFW.glfwDestroyWindow(window)
            window = NULL
            GLFW.glfwTerminate()
        }
    }

    private lateinit var shader: Shader
    private val renderer = UiBatchRenderer()
    private var fbo = 0
    private var target = 0
    private val textures = ArrayList<Int>()

    @BeforeEach
    fun prepare() {
        assumeTrue(window != NULL, "no OpenGL context available")
        shader = ShaderLoader().loadShader("/shaders/shader_2d_batch.glsl")
        target = texture(SIZE, SIZE, null)
        fbo = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0)
        glViewport(0, 0, SIZE, SIZE)
        glDisable(GL_DEPTH_TEST)
        glClearColor(0f, 0f, 0f, 1f)
        glClear(GL_COLOR_BUFFER_BIT)
        // Whatever an earlier pass left on unit 0: a red texture.
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, texture(1, 1, byteArrayOf(-1, 0, 0, -1)))
    }

    @AfterEach
    fun cleanup() {
        if (window == NULL) return
        renderer.destroy()
        shader.destroy()
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glDeleteFramebuffers(fbo)
        textures.forEach { glDeleteTextures(it) }
        textures.clear()
    }

    private fun texture(w: Int, h: Int, rgba: ByteArray?): Int {
        val id = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, id)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        val data = rgba?.let { BufferUtils.createByteBuffer(it.size).put(it).flip() }
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, data)
        textures.add(id)
        return id
    }

    private fun gpuTexture(rgba: ByteArray) = Texture(1, 1, 1, 4).also { it.texId = texture(1, 1, rgba) }

    /** RGB of the pixel at top-left-origin (x, y), 0..255. */
    private fun pixel(x: Int, y: Int): List<Int> {
        val buf = BufferUtils.createByteBuffer(4)
        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        glReadPixels(x, SIZE - 1 - y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, buf)
        return listOf(buf.get(0).toInt() and 0xFF, buf.get(1).toInt() and 0xFF, buf.get(2).toInt() and 0xFF)
    }

    private fun assertRgb(expected: List<Int>, actual: List<Int>, what: String) {
        expected.zip(actual).forEach { (e, a) -> assertEquals(e.toFloat(), a.toFloat(), 2f, "$what: expected $expected, got $actual") }
    }

    @Test
    fun UntexturedButtonBackground_IsItsSolidColour_RegardlessOfBoundTexture() {
        val list = UiDrawList()
        list.drawQuad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), 0.2f, 0.4f, 0.8f, 1f)
        renderer.render(list, shader, SIZE, SIZE)
        assertRgb(listOf(51, 102, 204), pixel(32, 32), "button background")
    }

    @Test
    fun TexturedQuad_IsTintedTexture() {
        val list = UiDrawList()
        list.drawQuad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), 0f, 1f, 0f, 1f, gpuTexture(byteArrayOf(-1, -1, -1, -1)))
        renderer.render(list, shader, SIZE, SIZE)
        assertRgb(listOf(0, 255, 0), pixel(32, 32), "tinted white texture")
    }

    @Test
    fun SdfGlyph_OutsideTheEdge_DrawsNothing_AndBackgroundStaysUnderText() {
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        val list = UiDrawList()
        list.drawQuad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), 0.2f, 0.4f, 0.8f, 1f)
        // Distance-field texel well outside the glyph edge (alpha 0.25 < 0.5): must stay invisible.
        list.drawQuad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), 1f, 1f, 1f, 1f, gpuTexture(byteArrayOf(-1, -1, -1, 64)), sdf = true)
        renderer.render(list, shader, SIZE, SIZE)
        glDisable(GL_BLEND)
        assertRgb(listOf(51, 102, 204), pixel(32, 32), "background under an empty glyph cell")
    }
}
