package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.EngineState
import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.imgui.ImGuiLayer
import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.render.VAOLoader
import imgui.ImGui
import kotlinx.coroutines.delay
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import java.util.concurrent.atomic.AtomicReference

class SplashScreenManager {
    val loadingProgress = AtomicReference(0f)
    var loadingText = "Initializing Engine..."

    var splashAlpha = 1.0f
    var isDestroyed = false

    private var splashShader: Shader? = null
    private var splashTexture: Texture? = null
    private var splashQuad: RawModel? = null

    fun init() {
        splashShader = AssetPool.getShader(Assets.Shaders.SPLASH)
        splashTexture = AssetPool.getTexture(Assets.Textures.SPLASH)
        splashQuad = VAOLoader().loadToVAO( // TODO: inject loader ?
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

        Window.show()
    }

    fun render(dt: Float, imguiLayer: ImGuiLayer,engineState: EngineState) {
        GL11.glClearColor(0f, 0f, 0f, 1.0f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT)

        // Render Splash Quad
        val shader = splashShader
        val texture = splashTexture
        val quad = splashQuad

        if (shader != null && texture != null && quad != null) {
            shader.start()
            shader.uploadFloat("uProgress", loadingProgress.get())
            shader.uploadFloat("uAlpha", splashAlpha)
            GL13.glActiveTexture(GL13.GL_TEXTURE0)

            texture.bind()
            shader.uploadInt("uTexture", 0)

            GL30.glBindVertexArray(quad.vaoId)
            GL20.glEnableVertexAttribArray(0)
            GL20.glEnableVertexAttribArray(1)
            GL11.glDrawElements(GL11.GL_TRIANGLES, quad.vertexCount, GL11.GL_UNSIGNED_INT, 0)
            GL20.glDisableVertexAttribArray(0)
            GL20.glDisableVertexAttribArray(1)
            GL30.glBindVertexArray(0)

            texture.unbind()
            shader.stop()
        }

        // Only show ImGui if we are still loading, not during fade out
        if (engineState != EngineState.RUNNING) {
            // Simple ImGui Loading Overlay
            imguiLayer.startFrame()

            val viewport = ImGui.getMainViewport()
            ImGui.setNextWindowPos(viewport.getCenter().x, viewport.getCenter().y + 200f, imgui.flag.ImGuiCond.Always, 0.5f, 0.5f)
            ImGui.setNextWindowSize(400f, 100f)

            if (ImGui.begin("Loading Status", imgui.flag.ImGuiWindowFlags.NoDecoration or imgui.flag.ImGuiWindowFlags.NoMove or imgui.flag.ImGuiWindowFlags.NoSavedSettings or imgui.flag.ImGuiWindowFlags.NoBackground)) {
                ImGui.setWindowFontScale(1.5f)
                ImGui.text(loadingText)
                ImGui.end()
            }

            imguiLayer.endFrame()
        }
    }

    suspend fun increaseLoadingProgress(message: String = "...", progress:Float = 0.1f) {
        loadingProgress.set(loadingProgress.get() + progress)
        loadingText = message
        delay(10)
    }

    fun destroy() {
        splashShader?.destroy()
        splashQuad = null

        splashTexture?.destroy()
        splashTexture = null
        isDestroyed = true
    }
}