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

    private lateinit var shader: Shader
    private lateinit var renderer: Renderer
    private lateinit var camera: Camera
    private lateinit var light: Light

    private lateinit var currentScene: Scene

    fun initializeScene() {
        shader = AssetPool.getShader(Shader.TEST2)
        light = Light(Vector3f(0f, 0f, -20f))
        camera = Camera()

        renderer = Renderer(shader, camera, light)

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
        renderer.clearColor()

        if (dt >= 0) {
            currentScene.update(dt)
            camera.move()
            shader.start()

            currentScene.gameObjects.forEach { go ->
                go.update(dt)
                go.getComponent<Entity>()?.let { it
                    renderer.render(it)
                }
            }
            shader.stop()
        }
    }

    fun destroy() {
        shader.destroy()
        renderer.destroy()
    }
}