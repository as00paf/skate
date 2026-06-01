package com.pafoid.skate.editor.search.providers

import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
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
import org.koin.core.context.stopKoin

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

        var received: ViewportAction.Duplicate? = null
        eventSystem.subscribe<ViewportAction.Duplicate> { received = it }

        val provider = ActionSearchProvider(sceneManager, logger, eventSystem)
        val duplicateResult = provider.search("duplicate")
            .firstOrNull { it.metadata["actionId"] == "duplicate" }

        assertNotNull(duplicateResult)

        val result = duplicateResult ?: return@runBlocking
        provider.navigate(result)

        assertEquals(selected, received?.gameObject)
    }
}
