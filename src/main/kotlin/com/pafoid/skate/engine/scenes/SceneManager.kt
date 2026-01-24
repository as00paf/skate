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
import com.pafoid.skate.pafcraft.GameLoop
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
    }

    private var currentScene: Scene? = null
    private lateinit var shader3D: Shader
    private lateinit var shader2D: Shader
    private lateinit var shaderPicking: Shader
    private lateinit var shaderSkybox: Shader
    private lateinit var renderer: Renderer

    fun initializeScene(imguiLayer: ImGuiLayer) {
        shader3D = AssetPool.getShader(Shader.SHADER_3D_DEFAULT)
        shader2D = AssetPool.getShader(Shader.SHADER_2D_BATCH)
        shaderPicking = AssetPool.getShader(Shader.PICKING)
        shaderSkybox = AssetPool.getShader(Shader.SKYBOX)
        renderer = Renderer(shader3D, shader2D, shaderPicking, shaderSkybox)

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
        val scene = currentScene
        if (dt >= 0 && scene != null) {
            scene.update(dt)
            renderer.render(scene)
            imguiLayer.update(dt, scene)
        }
    }

    fun destroy() {
        shader3D.destroy()
        shader2D.destroy()
        shaderPicking.destroy()
        shaderSkybox.destroy()
        renderer.destroy()
    }
}