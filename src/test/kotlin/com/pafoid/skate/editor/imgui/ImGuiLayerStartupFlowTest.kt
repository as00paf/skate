package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.project.ProjectWizard
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.systems.WindowRegistry
import com.pafoid.skate.editor.ui.windows.ProjectWizardWindow
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.render.renderer.Renderer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest

class ImGuiLayerStartupFlowTest : KoinTest {
    private lateinit var eventSystem: EventSystem
    private lateinit var projectManager: ProjectManager
    private lateinit var layer: ImGuiLayer
    private lateinit var windowRegistry: WindowRegistry
    private lateinit var projectWizardWindow: ProjectWizardWindow
    private lateinit var wizard: ProjectWizard

    @BeforeEach
    fun setup() {
        stopKoin()
        eventSystem = EventSystem()
        projectManager = mockk(relaxed = true)

        windowRegistry = mockk(relaxed = true)
        projectWizardWindow = mockk(relaxed = true)
        wizard = ProjectWizard()

        every { windowRegistry.projectWizardWindow } returns projectWizardWindow
        every { projectWizardWindow.wizard } returns wizard

        startKoin {
            modules(
                module {
                    single { eventSystem }
                    single { projectManager }
                }
            )
        }

        layer = ImGuiLayer(
            inputProvider = mockk<IInputProvider>(relaxed = true),
            settingsManager = mockk<SettingsManager>(relaxed = true),
            sceneManager = mockk<SceneManager>(relaxed = true),
            clipboardService = mockk<ClipboardService>(relaxed = true),
            stringManager = mockk<StringManager>(relaxed = true),
            undoRedoManager = mockk<UndoRedoManager>(relaxed = true),
            renderer = mockk<Renderer>(relaxed = true),
            resourceManager = mockk<ResourceManager>(relaxed = true),
            windowRegistry = windowRegistry,
        )
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `process startup flow publishes load-last once and opens wizard when no project exists`() {
        every { projectManager.hasProject() } returns false

        var loadLastRequests = 0
        eventSystem.subscribe<ProjectEvent.LoadLastProjectRequested> { loadLastRequests++ }

        layer.processProjectStartupFlow()
        layer.processProjectStartupFlow()

        assertEquals(1, loadLastRequests)
        assertTrue(wizard.isOpen.get())
    }

    @Test
    fun `process startup flow shows default windows and dismisses wizard once project opens`() {
        var hasProject = false
        every { projectManager.hasProject() } answers { hasProject }
        wizard.open()

        layer.processProjectStartupFlow()

        hasProject = true
        layer.processProjectStartupFlow()

        verify(exactly = 1) { windowRegistry.showDefaultWindows() }
        assertFalse(wizard.isOpen.get())
    }

    @Test
    fun `process startup flow does not reopen wizard after user dismisses it`() {
        every { projectManager.hasProject() } returns false
        wizard.dismiss()

        layer.processProjectStartupFlow()

        assertFalse(wizard.isOpen.get())
        assertTrue(wizard.userDismissed)
    }

    @Test
    fun `process startup flow hides windows when project closes`() {
        var hasProject = true
        every { projectManager.hasProject() } answers { hasProject }

        layer.processProjectStartupFlow()

        hasProject = false
        layer.processProjectStartupFlow()

        verify(exactly = 1) { windowRegistry.hideAllWindows() }
    }

    @Test
    fun `process startup flow does not request load-last or open wizard when project already exists`() {
        every { projectManager.hasProject() } returns true

        var loadLastRequests = 0
        eventSystem.subscribe<ProjectEvent.LoadLastProjectRequested> { loadLastRequests++ }

        layer.processProjectStartupFlow()

        assertEquals(0, loadLastRequests)
        assertFalse(wizard.isOpen.get())
        verify(exactly = 1) { windowRegistry.showDefaultWindows() }
        verify(exactly = 0) { windowRegistry.hideAllWindows() }
    }

    @Test
    fun `process startup flow does not re-request load-last after a project is closed`() {
        var hasProject = false
        every { projectManager.hasProject() } answers { hasProject }

        var loadLastRequests = 0
        eventSystem.subscribe<ProjectEvent.LoadLastProjectRequested> { loadLastRequests++ }

        layer.processProjectStartupFlow()
        hasProject = true
        layer.processProjectStartupFlow()
        hasProject = false
        layer.processProjectStartupFlow()

        assertEquals(1, loadLastRequests)
    }

    @Test
    fun `process startup flow shows default windows only on no-project to project transition`() {
        var hasProject = false
        every { projectManager.hasProject() } answers { hasProject }

        layer.processProjectStartupFlow()

        hasProject = true
        layer.processProjectStartupFlow()
        layer.processProjectStartupFlow()

        verify(exactly = 1) { windowRegistry.showDefaultWindows() }
    }

    @Test
    fun `process startup flow does not hide windows when project was never open`() {
        every { projectManager.hasProject() } returns false

        layer.processProjectStartupFlow()
        layer.processProjectStartupFlow()

        verify(exactly = 0) { windowRegistry.hideAllWindows() }
    }

    @Test
    fun `process startup flow suppresses wizard when load-last opens project immediately`() {
        var hasProject = false
        every { projectManager.hasProject() } answers { hasProject }

        eventSystem.subscribe<ProjectEvent.LoadLastProjectRequested> {
            hasProject = true
        }

        layer.processProjectStartupFlow()

        verify(exactly = 1) { windowRegistry.showDefaultWindows() }
        assertFalse(wizard.isOpen.get())
    }

    @Test
    fun `process startup flow opens wizard after project closes when not dismissed`() {
        var hasProject = true
        every { projectManager.hasProject() } answers { hasProject }

        layer.processProjectStartupFlow()
        hasProject = false
        layer.processProjectStartupFlow()

        assertTrue(wizard.isOpen.get())
        assertFalse(wizard.userDismissed)
    }

    @Test
    fun `process startup flow keeps wizard closed after project closes when dismissed`() {
        var hasProject = true
        every { projectManager.hasProject() } answers { hasProject }
        wizard.dismiss()

        layer.processProjectStartupFlow()
        hasProject = false
        layer.processProjectStartupFlow()

        assertFalse(wizard.isOpen.get())
        assertTrue(wizard.userDismissed)
    }
}
