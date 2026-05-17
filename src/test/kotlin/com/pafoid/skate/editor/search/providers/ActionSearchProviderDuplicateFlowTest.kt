package com.pafoid.skate.editor.search.providers

import com.pafoid.skate.editor.events.ViewportDuplicate
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class ActionSearchProviderDuplicateFlowTest {

    @AfterEach
    fun teardown() {
        stopKoin()
    }

    @Test
    fun `duplicate action publishes viewport duplicate for selected object`() = runBlocking {
        val eventSystem = EventSystem()
        val sceneManager = mockk<SceneManager>(relaxed = true)
        val logger = mockk<LoggerService>(relaxed = true)
        val selected = GameObject("Selected")
        val scene = mockk<Scene>(relaxed = true)

        every { scene.selectedGameObject } returns selected
        every { sceneManager.currentScene } returns scene

        stopKoin()
        startKoin {
            modules(
                module {
                    single { eventSystem }
                }
            )
        }

        var received: ViewportDuplicate? = null
        eventSystem.subscribe<ViewportDuplicate> { received = it }

        val provider = ActionSearchProvider(sceneManager, logger)
        val duplicateResult = provider.search("duplicate")
            .firstOrNull { it.metadata["actionId"] == "duplicate" }

        assertNotNull(duplicateResult)

        val result = duplicateResult ?: return@runBlocking
        provider.navigate(result)

        assertEquals(selected, received?.gameObject)
    }
}
