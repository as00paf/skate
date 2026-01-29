package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.EngineState

import com.pafoid.skate.engine.ImGuiLayer
import com.pafoid.skate.engine.Window

import com.pafoid.skate.engine.assets.AssetPool

import com.pafoid.skate.engine.assets.Shader

import com.pafoid.skate.engine.assets.Texture

import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.RawModel

import com.pafoid.skate.engine.render.Camera

import com.pafoid.skate.engine.render.Light

import com.pafoid.skate.engine.render.Renderer

import com.pafoid.skate.engine.render.VAOLoader

import com.pafoid.skate.engine.scenes.editor.LevelEditorSceneInitializer

import com.pafoid.skate.engine.utils.JobSystem

import kotlinx.coroutines.delay

import kotlinx.coroutines.withContext

import org.joml.Vector3f

import java.util.concurrent.atomic.AtomicReference



class SceneManager {

    companion object {
        private var instance: SceneManager? = null

        fun get(): SceneManager {
            if (instance == null) {
                instance = SceneManager()
            }

            return instance!!
        }

        fun getCurrentScene(): Scene? = get().currentScene
        fun isPlaying(): Boolean = get().runtimePlaying
        fun setPlaying(playing: Boolean) { get().runtimePlaying = playing }
    }

    private var currentScene: Scene? = null
    private var runtimePlaying = false
    private val engineState = AtomicReference(EngineState.BOOTING)
    private val loadingProgress = AtomicReference(0f)
    private var loadingText = "Initializing Engine..."
    private var splashAlpha = 1.0f
    private val fadeSpeed = 1.0f // 1 second fade

    private lateinit var shader3D: Shader
    private lateinit var shader2D: Shader
    private lateinit var shaderPicking: Shader
    private lateinit var shaderPicking3D: Shader
    private lateinit var shaderSkybox: Shader
    private lateinit var shaderSkyDome: Shader
    private lateinit var renderer: Renderer

    private var splashShader: Shader? = null
    private var splashTexture: Texture? = null
    private var splashQuad: RawModel? = null

    private val loader = VAOLoader()

    suspend fun initializeScene(imguiLayer: ImGuiLayer) {
        // Load Splash Assets on Main Thread first
        withContext(JobSystem.Main) {
            splashShader = AssetPool.getShader("assets/shaders/splash.glsl")
            splashTexture = AssetPool.getTexture("assets/textures/splash_screen.png")
            splashQuad = loader.loadToVAO(
                floatArrayOf(
                    -1f, -1f, 0f,
                     1f, -1f, 0f,
                     1f,  1f, 0f,
                    -1f,  1f, 0f
                ),

                floatArrayOf(
                    0f, 0f,
                    1f, 0f,
                    1f, 1f,
                    0f, 1f
                ),

                floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
                intArrayOf(0, 1, 2, 2, 3, 0)
            )

            Window.show()
            engineState.set(EngineState.LOADING)
        }

        loadingText = "Loading Shaders..."
        loadingProgress.set(0.1f)

        withContext(JobSystem.Main) {
            shader3D = AssetPool.getShader(Shader.SHADER_3D_DEFAULT)
            shader2D = AssetPool.getShader(Shader.SHADER_2D_BATCH)
            shaderPicking = AssetPool.getShader(Shader.PICKING)
            shaderPicking3D = AssetPool.getShader(Shader.PICKING_3D)
            shaderSkybox = AssetPool.getShader(Shader.SKYBOX)
            shaderSkyDome = AssetPool.getShader(Shader.SKY_DOME)
        }

        delay(10) // Small delay to let loading screen render

        loadingProgress.set(0.4f)
        loadingText = "Initializing Renderer..."

        withContext(JobSystem.Main) {
            renderer = Renderer(shader3D, shader2D, shaderPicking, shaderPicking3D, shaderSkybox, shaderSkyDome)
            renderer.useFbo = true
        }

        delay(10)

        loadingProgress.set(0.6f)
        loadingText = "Loading Scene..."

        // Scene initialization might also need to be on main thread if it creates GL objects
        withContext(JobSystem.Main) {
            changeScene(LevelEditorSceneInitializer(), true)
        }

        loadingProgress.set(1.0f)
        engineState.set(EngineState.RUNNING)

        withContext(JobSystem.Main) {
            Window.show()
        }
}

    fun getPickedId(x: Int, y: Int): Int {
        if (engineState.get() != EngineState.RUNNING) return -1
        return renderer.readPixel(x, y)
    }

    fun getObjectById(id: Int): GameObject? {
        if (engineState.get() != EngineState.RUNNING) return null
        return currentScene?.getGameObject(id)
    }

    fun getJointById(id: Int): com.pafoid.skate.engine.animation.Joint? {
        if (engineState.get() != EngineState.RUNNING) return null
        currentScene?.gameObjects?.forEach { go ->
            go.getComponent<com.pafoid.skate.engine.animation.PoseGizmo>()?.let { pg ->
                val joint = pg.getJointById(id)
                if (joint != null) return joint
            }
        }
        return null
    }

    fun getPickedObject(x: Int, y: Int): GameObject? {
        if (engineState.get() != EngineState.RUNNING) return null
        val id = renderer.readPixel(x, y)
        return currentScene?.getGameObject(id)
    }



        private suspend fun changeScene(initializer: SceneInitializer, isFirstScene: Boolean = false) {



            if (!isFirstScene) currentScene?.destroy()



    



            val scene = Scene(initializer)



            currentScene = scene



            //scene.load()



            scene.init()



            scene.start()



        }



    



        fun draw(dt: Float, imguiLayer: ImGuiLayer) {



    



            val state = engineState.get()



    



            if (state != EngineState.RUNNING || splashAlpha > 0f) {



    



                renderLoadingScreen(imguiLayer)



    



                if (state == EngineState.RUNNING && splashAlpha > 0f) {



    



                    splashAlpha -= fadeSpeed * dt



    



                    if (splashAlpha < 0f) splashAlpha = 0f



    



                }



    



                if (state != EngineState.RUNNING) return



    



            }



    



    



    



            val scene = currentScene



    



            if (dt >= 0 && scene != null) {



    



                if (runtimePlaying) {



    



                    scene.update(dt)



    



                } else {



    



                    scene.editorUpdate(dt)



    



                }



    



                renderer.render(scene, imguiLayer.propertiesWindow.getActiveObject(), imguiLayer.gameViewWindow.getHoveredObject())



    



                imguiLayer.update(dt, scene)



    



            }



    



        }



    



    



    



        private fun renderLoadingScreen(imguiLayer: ImGuiLayer) {



    



            // Clear screen



    



            org.lwjgl.opengl.GL11.glClearColor(0f, 0f, 0f, 1.0f)



    



            org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT)



    



    



    



            // Render Splash Quad



    



            val shader = splashShader



    



            val texture = splashTexture



    



            val quad = splashQuad



    



            



    



            if (shader != null && texture != null && quad != null) {



    



                shader.start()



    



                shader.uploadFloat("uProgress", loadingProgress.get())



    



                shader.uploadFloat("uAlpha", splashAlpha)



    



                org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0)



    



                texture.bind()



    



                shader.uploadInt("uTexture", 0)



    



    



    



                org.lwjgl.opengl.GL30.glBindVertexArray(quad.vaoId)



    



                org.lwjgl.opengl.GL20.glEnableVertexAttribArray(0)



    



                org.lwjgl.opengl.GL20.glEnableVertexAttribArray(1)



    



                



    



                org.lwjgl.opengl.GL11.glDrawElements(org.lwjgl.opengl.GL11.GL_TRIANGLES, quad.vertexCount, org.lwjgl.opengl.GL11.GL_UNSIGNED_INT, 0)



    



    



    



                org.lwjgl.opengl.GL20.glDisableVertexAttribArray(0)



    



                org.lwjgl.opengl.GL20.glDisableVertexAttribArray(1)



    



                org.lwjgl.opengl.GL30.glBindVertexArray(0)



    



                texture.unbind()



    



                shader.stop()



    



            }



    



    



    



            // Only show ImGui if we are still loading, not during fade out



    



            if (engineState.get() != EngineState.RUNNING) {



    



                // Simple ImGui Loading Overlay



    



                imguiLayer.startFrame()



    



                



    



                val viewport = imgui.ImGui.getMainViewport()



    



                imgui.ImGui.setNextWindowPos(viewport.getCenter().x, viewport.getCenter().y + 200f, imgui.flag.ImGuiCond.Always, 0.5f, 0.5f)



    



                imgui.ImGui.setNextWindowSize(400f, 100f)



    



                



    



                if (imgui.ImGui.begin("Loading Status", imgui.flag.ImGuiWindowFlags.NoDecoration or imgui.flag.ImGuiWindowFlags.NoMove or imgui.flag.ImGuiWindowFlags.NoSavedSettings or imgui.flag.ImGuiWindowFlags.NoBackground)) {



    



                    imgui.ImGui.setWindowFontScale(1.5f)



    



                    imgui.ImGui.text(loadingText)



    



                    imgui.ImGui.end()



    



                }



    



                



    



                imguiLayer.endFrame()



    



            }



    



        }



    



        fun destroy() {



    



            if (engineState.get() != EngineState.RUNNING) return



    



            shader3D.destroy()



    



    

        shader2D.destroy()

        shaderPicking.destroy()

        shaderPicking3D.destroy()

        shaderSkybox.destroy()

        shaderSkyDome.destroy()

        renderer.destroy()

    }

}
