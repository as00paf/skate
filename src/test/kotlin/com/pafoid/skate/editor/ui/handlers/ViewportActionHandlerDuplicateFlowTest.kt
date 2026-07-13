package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.EditorMutationGate
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.CameraManager
import com.pafoid.skate.engine.utils.IJobSystem
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin

class ViewportActionHandlerDuplicateFlowTest {

    private val engine = mockk<Engine>(relaxed = true)
    private val sceneManager = mockk<SceneManager>(relaxed = true)
    private val undoRedoManager = mockk<UndoRedoManager>(relaxed = true)
    private val eventSystem = EventSystem()
    private val logger = mockk<LoggerService>(relaxed = true)
    private val clipboardService = mockk<ClipboardService>(relaxed = true)
    private val mutationGate = mockk<EditorMutationGate>(relaxed = true)
    private val prefabsGenerator = mockk<PrefabsGenerator>(relaxed = true)
    private val assetsManager = mockk<AssetsManager>(relaxed = true)
    private val gameObjectManager = mockk<GameObjectManager>(relaxed = true)
    private val cameraManager = mockk<CameraManager>(relaxed = true)
    private val systemManager = mockk<SystemManager>(relaxed = true)
    private val jobSystem = mockk<IJobSystem>(relaxed = true)

    @BeforeEach
    fun setup() {
        every { mutationGate.blockIfPlaying(any()) } returns false
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }

        stopKoin()
    }

    @AfterEach
    fun teardown() {
        stopKoin()
    }

    @Test
    fun `viewport duplicate event executes canonical duplicate command behavior`() {
        val scene = mockk<Scene>(relaxed = true)
        every { sceneManager.currentScene } returns scene
        every { scene.selectedGameObject = any() } just runs

        val original = GameObject("Crate")
        val transform = Transform().apply { translation.set(2f, 0f, 3f) }
        original.addComponent(transform)

        val addedObject = slot<GameObject>()
        every { gameObjectManager.addGameObject(capture(addedObject), any()) } just runs

        val handler = ViewportActionHandler(
            engine,
            sceneManager,
            undoRedoManager,
            eventSystem,
            logger,
            clipboardService,
            mutationGate,
            prefabsGenerator,
            assetsManager,
            gameObjectManager,
            cameraManager,
            systemManager,
            jobSystem
        )
        handler.init()

        eventSystem.publish(ViewportAction.Duplicate(original))

        assertNotNull(addedObject.captured.getComponent<Transform>())
        assertEquals("Crate (Copy)", addedObject.captured.name)
        assertEquals(3f, addedObject.captured.getComponent<Transform>()?.translation?.x)
        assertEquals(0f, addedObject.captured.getComponent<Transform>()?.translation?.y)
        assertEquals(3f, addedObject.captured.getComponent<Transform>()?.translation?.z)
        verify { scene.selectedGameObject = addedObject.captured }
    }
}
