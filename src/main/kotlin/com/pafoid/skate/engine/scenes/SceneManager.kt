package com.pafoid.skate.engine.scenes

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

    private lateinit var shader3D: Shader
    private lateinit var shader2D: Shader
    private lateinit var renderer: Renderer

    private lateinit var currentScene: Scene

    fun initializeScene() {
        shader3D = AssetPool.getShader(Shader.SHADER_3D_DEFAULT)
        shader2D = AssetPool.getShader(Shader.SHADER_2D_BATCH)
        renderer = Renderer(shader3D, shader2D)

        changeScene(LevelEditorSceneInitializer(), true)
    }

    private fun changeScene(initializer: SceneInitializer, isFirstScene: Boolean = false) {
        if (!isFirstScene) currentScene.destroy()

        val scene = Scene(initializer)
        currentScene = scene
        //scene.load()
        scene.init()
        scene.start()
    }

    fun draw(dt: Float) {
        if (dt >= 0) {
            currentScene.update(dt)
            renderer.render(currentScene)
        }
    }

    fun destroy() {
        shader3D.destroy()
        shader2D.destroy()
        renderer.destroy()
    }
}