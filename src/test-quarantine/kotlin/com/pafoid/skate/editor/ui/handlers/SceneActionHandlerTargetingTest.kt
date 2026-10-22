package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.systems.EditorMutationGate
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.JobSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SceneActionHandlerTargetingTest {
    private val eventSystem = EventSystem()
    private val sceneManager = mockk<SceneManager>(relaxed = true)
    private val undoRedoManager = mockk<UndoRedoManager>(relaxed = true)
    private val engine = mockk<Engine>(relaxed = true)
    private val projectManager = mockk<ProjectManager>(relaxed = true)
    private val mutationGate = mockk<EditorMutationGate>(relaxed = true)
    private val jobSystem: JobSystem = JobSystem()
    private val openScenes = mutableListOf<Scene>()

    @BeforeEach
    fun setup() {
        every { sceneManager.openScenes } returns openScenes
        every { mutationGate.blockIfPlaying(any()) } returns false
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }
    }

    @AfterEach
    fun teardown() {
        jobSystem.destroy()
    }

    @Test
    fun `close request targets provided scene reference after scene order churn`() {
        val sceneA = mockScene(101, "A")
        val sceneB = mockScene(202, "B")
        val sceneC = mockScene(303, "C")
        openScenes.addAll(listOf(sceneA, sceneB, sceneC))
        openScenes.clear()
        openScenes.addAll(listOf(sceneC, sceneA, sceneB))

        SceneActionHandler(engine, projectManager, undoRedoManager, mutationGate)

        eventSystem.publish(SceneAction.CloseRequested(sceneB))

        verify(exactly = 1) { sceneManager.closeScene(sceneB) }
    }

    @Test
    fun `tab select and close others target provided scene reference`() {
        val sceneA = mockScene(11, "A")
        val sceneB = mockScene(22, "B")
        val sceneC = mockScene(33, "C")
        openScenes.addAll(listOf(sceneB, sceneC, sceneA))

        SceneActionHandler(engine, projectManager, undoRedoManager, mutationGate)

        eventSystem.publish(ViewportAction.TabSelected(sceneA))
        eventSystem.publish(SceneAction.CloseOthersRequested(sceneC))

        verify(exactly = 1) { sceneManager.switchScene(sceneA) }
        verify(exactly = 1) { sceneManager.closeOtherScenes(sceneC) }
    }

    private fun mockScene(uid: Int, name: String): Scene {
        val scene = mockk<Scene>(relaxed = true)
        every { scene.uId } returns uid
        every { scene.name } returns name
        return scene
    }
}
