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
}
