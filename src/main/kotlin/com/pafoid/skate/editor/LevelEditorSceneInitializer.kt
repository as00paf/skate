package com.pafoid.skate.editor

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.scene.SceneInitializer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LevelEditorSceneInitializer : SceneInitializer(), KoinComponent {

    private val resourceManager: ResourceManager by inject()

    override suspend fun loadResources(scene: Scene) {
        reportProgress(0.25f, "Loading Character Model...")
        resourceManager.loadModel(Assets.Models.JAMES)
        reportProgress(0.5f, "Loading Skateboard Model...")
        resourceManager.loadModel(Assets.Models.SKATEBOARD_GLB)
        reportProgress(1f, "Resources Loaded.")
    }

    override suspend fun init(scene: Scene) {
        scene.camera.position.set(0f, 5f, 20f)
        scene.camera.yaw = 0f
    }
}
