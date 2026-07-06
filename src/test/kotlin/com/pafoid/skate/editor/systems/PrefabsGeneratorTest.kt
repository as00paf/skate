package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.CharacterModel
import com.pafoid.skate.engine.assets.data.models.RawModel
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.utils.IJobSystem
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PrefabsGeneratorTest {

    @Test
    fun `spawnDefaultsSync spawns skateboard skater and floor into current scene`() {
        // Mocks
        val resourceManager = mockk<ResourceManager>(relaxed = true)
        val jobSystem = mockk<IJobSystem>(relaxed = true)
        val logger = mockk<LoggerService>(relaxed = true)
        val eventSystem = mockk<EventSystem>(relaxed = true)

        // Systems
        val systemManager = SystemManager()
        val gameObjectManager = GameObjectManager()
        systemManager.addSystem(gameObjectManager)

        // Minimal scene and sceneManager
        val sceneManager = SceneManager(
            resourceManager,
            eventSystem,
            com.pafoid.skate.engine.assets.serialization.Serializer(),
            systemManager,
            logger,
            mockk(relaxed = true)
        )
        val scene = Scene("TestScene")
        systemManager.loadScene(scene)

        // Prepare simple model/texture/animation objects to be returned by ResourceManager
        val raw = RawModel(0, 0, floatArrayOf())
        val tex = Texture().apply { filePath = "engine://textures/asphalt.png" }
        val texturedModel = TexturedModel(raw, tex)
        val skeleton = Skeleton(Bone(0, "root"), 1)
        val characterModel = CharacterModel(listOf(), skeleton)
        val animation = Animation("idle", emptyList(), 1.0f, "anim/idle")

        // Stub ResourceManager synchronous methods used by prefabs
        every { resourceManager.loadModelSync(any()) } returns texturedModel
        every { resourceManager.getModel(any()) } returns characterModel
        every { resourceManager.loadAnimationSync(any(), any()) } returns animation
        every { resourceManager.loadTextureSync(any()) } returns tex

        val prefabs = PrefabsGenerator(resourceManager, sceneManager, systemManager)

        val spawned = prefabs.spawnDefaultsSync()

        // Expect skateboard, skater, and floor (3 objects)
        assertEquals(3, spawned.size)
        assertEquals(3, scene.gameObjects.size)
    }
}
