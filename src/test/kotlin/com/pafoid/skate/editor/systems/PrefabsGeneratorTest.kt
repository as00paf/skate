package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.RawModel
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.SystemManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PrefabsGeneratorTest {

    @Test
    fun `spawnDefaultsSync spawns skateboard skater and floor into current scene`() {
        // Mocks
        val assetsManager = mockk<AssetsManager>(relaxed = true)
        val logger = mockk<LoggerService>(relaxed = true)
        val eventSystem = mockk<EventSystem>(relaxed = true)

        // Systems
        val systemManager = SystemManager()
        val gameObjectManager = GameObjectManager()
        systemManager.addSystem(gameObjectManager)

        // Minimal scene and sceneManager
        val sceneManager = SceneManager(
            assetsManager,
            eventSystem,
            Serializer(),
            systemManager,
            logger,
        )
        val scene = Scene("TestScene")
        systemManager.loadScene(scene)

        // Prepare simple model/texture/animation objects to be returned by ResourceManager
        val raw = RawModel(0, 0, floatArrayOf())
        val tex = Texture().apply { filePath = "engine://textures/asphalt.png" }
        val texturedModel = TexturedModel(rawModel = raw, material = Material(baseColorTexture = tex))
        val skeleton = Skeleton(Bone(0, "root"), 1)
        val characterModel = TexturedModel(mesh = listOf(), skeleton = skeleton)
        val animation = Animation("idle", emptyList(), 1.0f, "anim/idle")

        // Stub ResourceManager synchronous methods used by prefabs
        every { assetsManager.loadModel(any()) } returns texturedModel
        every { assetsManager.getModel(any()) } returns characterModel
        every { assetsManager.loadAnimationSync(any(), any()) } returns animation
        every { assetsManager.getTexture(any()) } returns tex

        val prefabs = PrefabsGenerator(assetsManager, sceneManager, systemManager)

        val spawned = prefabs.spawnDefaultsSync()

        // Expect skateboard, skater, and floor (3 objects)
        assertEquals(3, spawned.size)
        assertEquals(3, scene.gameObjects.size)
    }
}
