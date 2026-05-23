package com.pafoid.skate.app

import com.pafoid.skate.editor.imgui.ImGuiLayer
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.core.EngineState
import com.pafoid.skate.engine.render.renderer.Renderer
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import java.util.concurrent.atomic.AtomicReference

class SplashScreen : KoinComponent {
    private val resourceManager: ResourceManager by inject()
    private val renderer: Renderer by inject()
    private val imGuiLayer: ImGuiLayer by inject()

    val loadingProgress = AtomicReference(0f)
    var loadingText = "Initializing Engine..."

    var splashAlpha = 1.0f
    var isDestroyed = false

    private var splashTexture: Texture? = null
    private val fadeDuration = 2f

    suspend fun init() {
        splashTexture = resourceManager.loadTexture(Assets.Textures.SPLASH)
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

    fun render(dt: Float, engineState: EngineState) {
        if(engineState != EngineState.RUNNING) {
            renderer.clearColor(Vector3f(0f, 0f, 0f))
        }

        // Ensure viewport is set to full screen before rendering splash
        // This prevents viewport changes from renderer during fade
        val fbWidth = IntArray(1)
        val fbHeight = IntArray(1)
        GLFW.glfwGetFramebufferSize(GLFW.glfwGetCurrentContext(), fbWidth, fbHeight)
        GL11.glViewport(0, 0, fbWidth[0], fbHeight[0])

        renderSplashQuad()
        showImGui()
    }

    private fun renderSplashQuad() {
        val texture = splashTexture
        if (texture != null) {
            val splashRenderer = renderer.renderResources.renderers.splash
            val splashShader = renderer.renderResources.shaders.splash
            
            splashRenderer.render(
                shader = splashShader,
                texture = texture,
                progress = loadingProgress.get(),
                alpha = splashAlpha
            )
        }
    }

    private fun showImGui() {
        imGuiLayer.startFrame()

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

        imGuiLayer.endFrame()
    }

    fun increaseLoadingProgress(message: String = "...", progress:Float = 0.1f) {
        loadingProgress.set(loadingProgress.get() + progress)
        loadingText = message
    }

    fun destroy() {
        splashTexture = null
        isDestroyed = true
    }
}
