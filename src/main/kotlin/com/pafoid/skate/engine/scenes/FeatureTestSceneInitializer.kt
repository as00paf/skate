package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.Transform
import com.pafoid.skate.engine.assets.*
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneInitializer
import com.pafoid.skate.engine.scenes.components.*
import com.pafoid.skate.engine.physics3d.components.*
import org.joml.Vector3f

class FeatureTestSceneInitializer : SceneInitializer() {
    private val loader = VAOLoader()
    private lateinit var texture: Texture

    override suspend fun loadResources(scene: Scene) {
        texture = AssetPool.getTexture(Texture.WHITE)
    }

    override suspend fun init(scene: Scene) {
        // Center camera on the cube
        scene.camera.position.set(0f, 0f, 5f)
        scene.camera.pitch = 0f
        scene.camera.yaw = 0f

        // FEATURE 1: Basic Rendering (Spinning Cube)
        val cubeGo = GameObject("SpinningCube")
        cubeGo.transform.translation.set(0f, 0f, 0f)
        cubeGo.addComponent(Entity(
            model = TexturedModel(AssetPool.getRawModel(ObjLoader.CUBE, loader), texture),
            onTick = { dt ->
                cubeGo.transform.rotation.y += 45f * dt
                cubeGo.transform.rotation.x += 20f * dt
            }
        ))
        scene.addGameObjectToScene(cubeGo)
    }

    override fun imgui() {
        // Empty to keep screen clear
    }
}
