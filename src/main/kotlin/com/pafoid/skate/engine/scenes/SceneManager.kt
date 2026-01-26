package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.ImGuiLayer
import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.Light
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.scenes.editor.LevelEditorSceneInitializer
import org.joml.Vector3f

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
    private lateinit var shader3D: Shader
    private lateinit var shader2D: Shader
    private lateinit var shaderPicking: Shader
    private lateinit var shaderPicking3D: Shader
    private lateinit var shaderSkybox: Shader
    private lateinit var shaderCloudDome: Shader
    private lateinit var renderer: Renderer

    fun initializeScene(imguiLayer: ImGuiLayer) {
        shader3D = AssetPool.getShader(Shader.SHADER_3D_DEFAULT)
        shader2D = AssetPool.getShader(Shader.SHADER_2D_BATCH)
        shaderPicking = AssetPool.getShader(Shader.PICKING)
        shaderPicking3D = AssetPool.getShader(Shader.PICKING_3D)
        shaderSkybox = AssetPool.getShader(Shader.SKYBOX)
        shaderCloudDome = AssetPool.getShader(Shader.CLOUD_DOME)
        renderer = Renderer(shader3D, shader2D, shaderPicking, shaderPicking3D, shaderSkybox, shaderCloudDome)
        renderer.useFbo = true

        changeScene(LevelEditorSceneInitializer(), true)
    }

    fun getPickedObject(x: Int, y: Int): GameObject? {
        val id = renderer.readPixel(x, y)
        return currentScene?.getGameObject(id)
    }

    private fun changeScene(initializer: SceneInitializer, isFirstScene: Boolean = false) {
        if (!isFirstScene) currentScene?.destroy()

        val scene = Scene(initializer)
        currentScene = scene
        //scene.load()
        scene.init()
        scene.start()
    }

    fun draw(dt: Float, imguiLayer: ImGuiLayer) {
        AssetPool.update()
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

    fun destroy() {
        shader3D.destroy()
        shader2D.destroy()
        shaderPicking.destroy()
        shaderPicking3D.destroy()
        shaderSkybox.destroy()
        shaderCloudDome.destroy()
        renderer.destroy()
    }
}