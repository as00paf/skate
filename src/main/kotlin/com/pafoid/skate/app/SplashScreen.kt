package com.pafoid.skate.app

import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.RawModel
import com.pafoid.skate.engine.core.EngineState
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.utils.ShaderConst
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.glfw.GLFW
import java.util.concurrent.atomic.AtomicReference

class SplashScreen : KoinComponent {
    private val resourceManager: ResourceManager by inject()
    private val vaoLoader: VAOLoader by inject()

    val loadingProgress = AtomicReference(0f)
    var loadingText = "Initializing Engine..."

    var splashAlpha = 1.0f
    var isDestroyed = false

    private var splashShader: Shader? = null
    private var splashTexture: Texture? = null
    private var splashQuad: RawModel? = null
    private val fadeDuration = 2f

    suspend fun init() {
        splashShader = resourceManager.loadShader(Assets.Shaders.SPLASH)
        splashTexture = resourceManager.loadTexture(Assets.Textures.SPLASH)
        splashQuad = vaoLoader.loadToVAO(
            positions = floatArrayOf(
                -1f, -1f, 0f,
                1f, -1f, 0f,
                1f,  1f, 0f,
                -1f,  1f, 0f
            ),
            textureCoords = floatArrayOf(
                0f, 0f,
                1f, 0f,
                1f, 1f,
                0f, 1f
            ),
            normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
            indices = intArrayOf(0, 1, 2, 2, 3, 0)
        )
    }

    fun update(dt: Float, engineState: EngineState) {
        if (splashAlpha > 0f) {
            if (engineState == EngineState.RUNNING) {
                splashAlpha -= dt / fadeDuration
            }
            if (splashAlpha < 0f) splashAlpha = 0f
        } else if (!isDestroyed) {
            destroy()
        }
    }

    fun render(dt: Float, imguiLayer: ImGuiLayer, engineState: EngineState) {
        if(engineState != EngineState.RUNNING) {
            GL11.glClearColor(0f, 0f, 0f, 0.0f)
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)
        }

        // Ensure viewport is set to full screen before rendering splash
        // This prevents viewport changes from renderer during fade
        val fbWidth = IntArray(1)
        val fbHeight = IntArray(1)
        GLFW.glfwGetFramebufferSize(GLFW.glfwGetCurrentContext(), fbWidth, fbHeight)
        GL11.glViewport(0, 0, fbWidth[0], fbHeight[0])

        renderSplashQuad()

        if (engineState != EngineState.RUNNING) {
            showImGui(imguiLayer)
        }
    }

    private fun renderSplashQuad() {
        val shader = splashShader
        val texture = splashTexture
        val quad = splashQuad

        if (shader != null && texture != null && quad != null) {
            shader.start()
            shader.uploadFloat(ShaderConst.Uniforms.PROGRESS, loadingProgress.get())
            shader.uploadFloat(ShaderConst.Uniforms.ALPHA, splashAlpha)

            GL13.glActiveTexture(GL13.GL_TEXTURE0)

            texture.bind()
            shader.uploadInt(ShaderConst.Uniforms.TEXTURE, 0)

            GL30.glBindVertexArray(quad.vaoId)
            GL20.glEnableVertexAttribArray(0)
            GL20.glEnableVertexAttribArray(1)
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL11.glDrawElements(GL11.GL_TRIANGLES, quad.vertexCount, GL11.GL_UNSIGNED_INT, 0)
            GL20.glDisableVertexAttribArray(0)
            GL20.glDisableVertexAttribArray(1)
            GL30.glBindVertexArray(0)

            texture.unbind()
            shader.stop()
            GL11.glDisable(GL11.GL_BLEND)
        }
    }

    private fun showImGui(imguiLayer: ImGuiLayer) {
        imguiLayer.startFrame()

        val viewport = ImGui.getMainViewport()
        ImGui.setNextWindowPos(viewport.getCenter().x, viewport.getCenter().y + 200f, ImGuiCond.Always, 0.5f, 0.5f)
        ImGui.setNextWindowSize(400f, 100f)

        if (ImGui.begin(
                "Loading Status",
                ImGuiWindowFlags.NoDecoration or ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoSavedSettings or ImGuiWindowFlags.NoBackground
            )
        ) {
            ImGui.setWindowFontScale(1.5f)
            ImGui.text(loadingText)
            ImGui.end()
        }

        imguiLayer.endFrame()
    }

    fun increaseLoadingProgress(message: String = "...", progress:Float = 0.1f) {
        loadingProgress.set(loadingProgress.get() + progress)
        loadingText = message
    }

    fun destroy() {
        splashShader?.destroy()

        splashQuad?.let {
            vaoLoader.deleteVAO(it.vaoId)
        }
        splashQuad = null

        splashTexture?.destroy()
        splashTexture = null
        isDestroyed = true
    }
}