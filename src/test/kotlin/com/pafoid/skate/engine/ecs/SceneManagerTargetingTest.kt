package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.contracts.EngineLogger
import com.pafoid.skate.engine.physics3d.Physics3DFactory
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SceneManagerTargetingTest {

    @Test
    fun `closeOtherScenes keeps referenced scene under reordered list`() {
        val sceneManager = createSceneManager()
        val sceneA = mockScene(1, "A")
        val sceneB = mockScene(2, "B")
        val sceneC = mockScene(3, "C")
        sceneManager.openScenes.addAll(listOf(sceneC, sceneA, sceneB))
        sceneManager.activeSceneIndex = 0

        sceneManager.closeOtherScenes(sceneB)

        assertEquals(1, sceneManager.openScenes.size)
        assertSame(sceneB, sceneManager.openScenes.first())
        assertEquals(0, sceneManager.activeSceneIndex)
    }

    private fun createSceneManager(): SceneManager {
        return SceneManager(
            logger = mockk<EngineLogger>(relaxed = true),
            resourceManager = mockk<ResourceManager>(relaxed = true),
            sceneEventPublisher = mockk<SceneEventPublisher>(relaxed = true),
            physics3DFactory = mockk<Physics3DFactory>(relaxed = true)
        )
    }

    private fun mockScene(uid: Int, name: String): Scene {
        val scene = mockk<Scene>(relaxed = true)
        every { scene.getUid() } returns uid
        every { scene.name } returns name
        return scene
    }
}
