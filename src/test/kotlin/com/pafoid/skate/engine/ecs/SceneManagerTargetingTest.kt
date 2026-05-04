package com.pafoid.skate.engine.ecs

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.core.EventSystem
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class SceneManagerTargetingTest {

    @AfterEach
    fun teardown() {
        stopKoin()
    }

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
        stopKoin()
        startKoin {
            modules(
                module {
                    single { mockk<LoggerService>(relaxed = true) }
                    single { mockk<ResourceManager>(relaxed = true) }
                    single { EventSystem() }
                }
            )
        }
        return SceneManager()
    }

    private fun mockScene(uid: Int, name: String): Scene {
        val scene = mockk<Scene>(relaxed = true)
        every { scene.getUid() } returns uid
        every { scene.name } returns name
        return scene
    }
}
