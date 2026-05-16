package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.editor.events.SceneCloseOthersRequested
import com.pafoid.skate.editor.events.SceneCloseRequested
import com.pafoid.skate.editor.events.SceneTabSelected
import com.pafoid.skate.engine.utils.IJobSystem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class SceneActionHandlerTargetingTest {
    private val eventSystem = EventSystem()
    private val sceneManager = mockk<SceneManager>(relaxed = true)
    private val sceneSerializer = mockk<SceneSerializer>(relaxed = true)
    private val undoRedoManager = mockk<UndoRedoManager>(relaxed = true)
    private val loggerService = mockk<LoggerService>(relaxed = true)
    private val sceneInitializer = mockk<LevelEditorSceneInitializer>(relaxed = true)
    private val projectManager = mockk<ProjectManager>(relaxed = true)
    private val jobSystem: IJobSystem = ImmediateJobSystem()
    private val openScenes = mutableListOf<Scene>()

    @BeforeEach
    fun setup() {
        stopKoin()
        coEvery { sceneInitializer.loadResources(any()) } returns Unit
        coEvery { sceneInitializer.init(any()) } returns Unit

        every { sceneManager.openScenes } returns openScenes
        every { undoRedoManager.executeCommand(any()) } answers {
            firstArg<Command>().execute()
        }

        startKoin {
            modules(
                module {
                    single { sceneManager }
                    single { sceneSerializer }
                    single { undoRedoManager }
                    single { eventSystem }
                    single { loggerService }
                    single { sceneInitializer }
                    single { projectManager }
                    single<IJobSystem> { jobSystem }
                }
            )
        }
    }

    @AfterEach
    fun teardown() {
        jobSystem.destroy()
        stopKoin()
    }

    @Test
    fun `close request targets provided scene reference after scene order churn`() {
        val sceneA = mockScene(101, "A")
        val sceneB = mockScene(202, "B")
        val sceneC = mockScene(303, "C")
        openScenes.addAll(listOf(sceneA, sceneB, sceneC))
        openScenes.clear()
        openScenes.addAll(listOf(sceneC, sceneA, sceneB))

        SceneActionHandler().apply { init() }

        eventSystem.publish(SceneCloseRequested(sceneB))

        verify(exactly = 1) { sceneManager.closeScene(sceneB) }
    }

    @Test
    fun `tab select and close others target provided scene reference`() {
        val sceneA = mockScene(11, "A")
        val sceneB = mockScene(22, "B")
        val sceneC = mockScene(33, "C")
        openScenes.addAll(listOf(sceneB, sceneC, sceneA))

        SceneActionHandler().apply { init() }

        eventSystem.publish(SceneTabSelected(sceneA))
        eventSystem.publish(SceneCloseOthersRequested(sceneC))

        verify(exactly = 1) { sceneManager.switchScene(sceneA) }
        verify(exactly = 1) { sceneManager.closeOtherScenes(sceneC) }
    }

    private fun mockScene(uid: Int, name: String): Scene {
        val scene = mockk<Scene>(relaxed = true)
        every { scene.getUid() } returns uid
        every { scene.name } returns name
        return scene
    }

    private class ImmediateJobSystem : IJobSystem {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined

        override fun update() = Unit

        override fun runAsync(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

        override fun runOnMain(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

        override fun <T> runAsyncDeferred(block: suspend CoroutineScope.() -> T): Deferred<T> =
            scope.async(block = block)

        override fun runIO(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

        override fun destroy() {
            scope.coroutineContext[Job]?.cancel()
        }
    }
}
