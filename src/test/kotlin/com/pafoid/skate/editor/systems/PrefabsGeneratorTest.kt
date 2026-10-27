package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.core.Engine
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrefabsGeneratorTest {

    @Test
    fun `spawnDefaultsSync returns camera skateboard skater floor and sprite without touching the scene`() {
        val assetsManager = mockk<AssetsManager>(relaxed = true)
        val engine = mockk<Engine>()
        every { engine.assetsManager } returns assetsManager
        every { engine.sceneManager } returns mockk(relaxed = true)

        val tex = Texture().apply { filePath = "engine://textures/asphalt.png" }
        val skeleton = Skeleton(Bone(0, "root"), 1)
        val model = `3dModel`(mesh = listOf(), skeleton = skeleton)
        val animation = Animation("idle", emptyList(), 1.0f, "anim/idle")

        every { assetsManager.loadModel(any()) } returns model
        every { assetsManager.loadAnimationSync(any()) } returns animation
        every { assetsManager.getTexture(any()) } returns tex
        every { assetsManager.getBundledTexture(any()) } returns tex

        val spawned = PrefabsGenerator(engine).spawnDefaultsSync()

        assertEquals(listOf("Camera", "Skateboard", "Skater", "Floor", "Sprite"), spawned.map { it.name })
    }

    @Test
    fun `createDefaultScenes attaches objects through the scene API so cached queries see them`() {
        val assetsManager = mockk<AssetsManager>(relaxed = true)
        val sceneManager = mockk<com.pafoid.skate.engine.ecs.SceneManager>(relaxed = true)
        val engine = mockk<Engine>()
        every { engine.assetsManager } returns assetsManager
        every { engine.sceneManager } returns sceneManager
        every { sceneManager.createNewScene(any(), any()) } answers {
            com.pafoid.skate.engine.ecs.Scene(firstArg())
        }
        val tex = Texture()
        val model = `3dModel`(mesh = listOf(), skeleton = Skeleton(Bone(0, "root"), 1))
        every { assetsManager.loadModel(any()) } returns model
        every { assetsManager.getModel(any()) } returns model
        every { assetsManager.loadAnimationSync(any()) } returns Animation("idle", emptyList(), 1.0f, "anim/idle")
        every { assetsManager.getTexture(any()) } returns tex
        every { assetsManager.getBundledTexture(any()) } returns tex

        lateinit var currentScene: com.pafoid.skate.engine.ecs.Scene
        val sprites = com.pafoid.skate.engine.ecs.ComponentQuery(
            root = { currentScene!! },
            required = listOf(com.pafoid.skate.engine.ecs.components.SpriteRenderer::class)
        )
        val before = com.pafoid.skate.engine.ecs.EcsChanges.version

        val scenes = PrefabsGenerator(engine).createDefaultScenes(java.io.File("."))
        currentScene = scenes[0]

        assertTrue(com.pafoid.skate.engine.ecs.EcsChanges.version > before)
        scenes.forEach { scene -> scene.children.forEach { assertSame(scene, it.parent) } }
        val names = mutableListOf<String>()
        sprites.forEach { names += it.name }
        assertTrue("Sprite" in names)
    }
}
