package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.imgui.loadFonts
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import com.pafoid.skate.editor.loading.LoadingRunner
import com.pafoid.skate.editor.loading.StepExecutor
import com.pafoid.skate.editor.project.ExportMemory
import com.pafoid.skate.editor.project.ExportSummary
import com.pafoid.skate.editor.project.ExportWizard
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.project.ProjectFileReader
import com.pafoid.skate.editor.settings.EditorSettings
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.ProjectActionHandler
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.JobSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.physics3d.PhysicsBackend
import com.pafoid.skate.engine.utils.BuildMode
import com.pafoid.skate.engine.utils.ExportArt
import com.pafoid.skate.engine.utils.ExportNotice
import com.pafoid.skate.engine.utils.ExportOptions
import com.pafoid.skate.engine.utils.ExportResult
import com.pafoid.skate.engine.utils.ExportWarning
import com.pafoid.skate.engine.utils.PackEstimate
import imgui.ImGui
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** EDT-91: the export dialog edits every AST-29 setting, undoably, persists them and shows the warnings. */
class ExportDialogSettingsTest {
    private val dir: File = kotlin.io.path.createTempDirectory("skate-export-ui").toFile()
    private val serializer = Serializer()
    private val events = EventSystem()
    private val logger = LoggerService().also { it.consoleSink = { _, _ -> } }

    @AfterEach
    fun cleanup() {
        runCatching { dir.deleteRecursively() }
    }

    // --- Game section: requests → undoable commands → saved project -------------------------------------------

    @Test
    fun GameSection_EachSettingIsAnUndoableCommandSavedWithTheProject() {
        val sceneManager = SceneManager(mockk(relaxed = true), events, serializer, mockk(relaxed = true), mockk(relaxed = true))
        val engine = mockk<Engine>(relaxed = true).also { e ->
            every { e.eventSystem } returns events
            every { e.sceneManager } returns sceneManager
            every { e.serializer } returns serializer
            every { e.jobSystem.isMainThread() } returns true
        }
        val projects = ProjectManager(engine)
        val settings = EditorSettingsManager(serializer, events, mockk(relaxed = true), mockk(relaxed = true), File(dir, "editor.settings"))
        val undo = UndoRedoManager(events, mockk(relaxed = true))
        val inline = object : StepExecutor {
            override fun background(block: () -> Unit) = block()
            override fun main(block: () -> Unit) = block()
        }
        ProjectActionHandler(engine, projects, settings, undo, mockk(relaxed = true), LoadingRunner(events, inline))
        events.publish(ProjectEvent.CreateProjectRequested("P", dir.absolutePath))
        val project = projects.currentProject!!
        val icon = File(project.getProjectDirectory(), "Assets/Textures/icon.png").apply { parentFile.mkdirs(); writeText("png") }

        // An image from outside the project is copied in, so the saved path stays project-relative (review).
        val outside = File(dir, "Downloads/logo.png").apply { parentFile.mkdirs(); writeText("logo") }
        events.publish(ProjectEvent.SetProjectSplashRequested(outside.absolutePath, 2f))
        val copied = File(project.getProjectDirectory(), "Assets/Textures/logo.png")
        assertEquals(copied.absolutePath, project.splashPath)
        assertEquals("logo", copied.readText())

        events.publish(ProjectEvent.SetProjectIconRequested(icon.absolutePath))
        events.publish(ProjectEvent.SetProjectSplashRequested(project.splashPath, 3.5f))
        events.publish(ProjectEvent.SetProjectTargetFpsRequested(1000))
        events.publish(ProjectEvent.SetProjectPhysicsBackendRequested(PhysicsBackend.JOLT))
        assertEquals(icon.absolutePath, project.iconPath)
        assertEquals(3.5f, project.splashSeconds)
        assertEquals(Project.MAX_TARGET_FPS, project.targetFps, "clamped")
        assertEquals(PhysicsBackend.JOLT, project.physicsBackend)

        undo.undo()
        assertEquals(PhysicsBackend.BULLET, project.physicsBackend)
        undo.redo()
        events.publish(ProjectEvent.SetProjectTargetFpsRequested(Project.MAX_TARGET_FPS)) // same value: no command
        undo.undo()
        assertEquals(PhysicsBackend.BULLET, project.physicsBackend, "the no-op request added nothing to undo")
        undo.redo()

        events.publish(ProjectEvent.SaveRequested)
        val saved = ProjectFileReader.read(serializer, project.getProjectFile())
        assertEquals("Assets/Textures/icon.png", saved.iconPath.replace('\\', '/').substringAfter("P/"), "saved project-relative")
        assertEquals(3.5f, saved.splashSeconds)
        val raw = project.getProjectFile().readText()
        assertTrue("\"splashPath\": \"Assets/Textures/logo.png\"" in raw, "saved project-relative: $raw")
        assertTrue("Downloads" !in raw, "nothing outside the project is referenced")
        assertEquals(Project.MAX_TARGET_FPS, saved.targetFps)
        assertEquals(PhysicsBackend.JOLT, saved.physicsBackend)
    }

    @Test
    fun ProjectImages_KeepsProjectFiles_CopiesOutsideOnes_ReusesIdenticalCopies() {
        val project = File(dir, "Proj").also { File(it, "Assets/Textures").mkdirs() }
        val inside = File(project, "Assets/Textures/icon.png").apply { writeText("in") }
        assertEquals(inside.absolutePath, com.pafoid.skate.editor.project.ProjectImages.inProject(project, inside.absolutePath))
        assertEquals(EngineDefaults.Textures.APP_ICON, com.pafoid.skate.editor.project.ProjectImages.inProject(project, EngineDefaults.Textures.APP_ICON))
        assertEquals("", com.pafoid.skate.editor.project.ProjectImages.inProject(project, ""))
        val a = File(dir, "a/icon.png").apply { parentFile.mkdirs(); writeText("other") }
        val first = com.pafoid.skate.editor.project.ProjectImages.inProject(project, a.absolutePath)
        assertEquals(File(project, "Assets/Textures/icon_2.png").absolutePath, first, "a different file with the same name")
        assertEquals(first, com.pafoid.skate.editor.project.ProjectImages.inProject(project, a.absolutePath), "the same file again reuses its copy")
        assertEquals("in", inside.readText(), "the project's own file is never overwritten")
    }

    // --- Build section: remembered without exporting ----------------------------------------------------------

    @Test
    fun BuildChoices_PersistAsSoonAsTheyChange() {
        var settings = EditorSettings()
        val memory = ExportMemory.inSettings({ settings }) { change -> settings = change(settings) }
        val project = project()
        val w = ExportWizard(memory).also { it.load(project, emptyList()) }
        w.mode = BuildMode.DEBUG
        w.compress = false
        w.remember() // what the dialog does every frame
        val next = ExportWizard(memory).also { it.load(project, emptyList()) }
        assertEquals(BuildMode.DEBUG, next.mode, "kept across Cancel and an editor restart, without exporting")
        assertEquals(false, next.compress)
    }

    // --- Summary ------------------------------------------------------------------------------------------------

    private fun project(): Project {
        val folder = File(dir, "Game").also { it.mkdirs() }
        return Project(name = "Game", projectPath = File(folder, "Game.skateproject").absolutePath, defaultScene = "", scenesPath = emptyList())
    }

    @Test
    fun Summary_RecomputesOnlyWhenAnInputChanges_AndListsTheWarnings() {
        var computed = 0
        val art = ExportArt(EngineDefaults.Textures.APP_ICON, "", 0f, listOf(ExportNotice(ExportWarning.ICON_NOT_SET)))
        val summary = ExportSummary({ it() }, { it() }) { _, _ -> computed++; art to PackEstimate(12, 3456) }
        val project = project().apply { physicsBackend = PhysicsBackend.JOLT }
        summary.refresh(project, ExportOptions(), staleJar = null)
        summary.refresh(project, ExportOptions(), staleJar = null)
        assertEquals(1, computed, "unchanged inputs: no recomputation")
        val snap = summary.snapshot!!
        assertEquals(12, snap.estimate.assetCount)
        assertEquals(listOf(ExportWarning.ICON_NOT_SET, ExportWarning.PHYSICS_JOLT_FALLBACK), snap.warnings.map { it.warning })

        summary.refresh(project, ExportOptions(onlyReferenced = false), staleJar = File("old.jar"))
        assertEquals(2, computed)
        assertTrue(summary.snapshot!!.warnings.any { it.warning == ExportWarning.GAME_JAR_STALE })
        project.splashSeconds = 0f
        summary.refresh(project, ExportOptions(onlyReferenced = false), staleJar = File("old.jar"))
        assertEquals(3, computed, "a Game-section change recomputes")
    }

    // --- Headless render --------------------------------------------------------------------------------------

    private fun imgui(block: () -> Unit) {
        ImGui.createContext()
        try {
            val io = ImGui.getIO()
            io.iniFilename = null
            io.loadFonts(javaClass.getResourceAsStream(Assets.Fonts.FONTS_FILE)!!.readAllBytes())
            io.setDisplaySize(1600f, 1000f)
            io.deltaTime = 1f / 60f
            ImGuiStyleManager.invalidate()
            ImGuiStyleManager.library = ThemeLibrary.load(File(Assets.Themes.DIR))
            ImGuiStyleManager.sync("dark")
            block()
        } finally {
            ImGuiStyleManager.library = ThemeLibrary(emptyList())
            ImGui.destroyContext()
        }
    }

    @Test
    fun Dialog_RendersEverySectionAndState() = imgui {
        val project = project().apply { File(getProjectDirectory(), "Scenes").mkdirs() }
        val strings = mockk<StringManager>(relaxed = true)
        val jobs = JobSystem(logger = logger)
        val engine = mockk<Engine>(relaxed = true).also { e ->
            every { e.stringManager } returns strings
            every { e.eventSystem } returns events
            every { e.logger } returns logger
            every { e.jobSystem } returns jobs
        }
        val projects = mockk<ProjectManager>(relaxed = true).also {
            every { it.currentProject } returns project
            every { it.hasProject() } returns true
        }
        val settingsManager = mockk<EditorSettingsManager>(relaxed = true).also { every { it.editorSettings } returns EditorSettings() }
        val window = ExportWizardWindow(engine, projects, settingsManager)
        window.open()
        val published = ArrayList<ProjectEvent>()
        events.subscribe<ProjectEvent.SetProjectTargetFpsRequested> { published += it }

        fun frames(n: Int = 2) = repeat(n) {
            ImGui.newFrame()
            ImGui.setNextWindowPos(20f, 20f)
            ImGui.setNextWindowSize(900f, 900f)
            window.imgui()
            ImGui.render()
        }

        frames() // editing, summary still computing
        val deadline = System.currentTimeMillis() + 10_000
        while (window.summary.snapshot == null && System.currentTimeMillis() < deadline) { jobs.update(); Thread.sleep(10) }
        assertNotNull(window.summary.snapshot, "the summary is computed off the main thread")
        assertTrue(window.summary.snapshot!!.warnings.isEmpty() || window.summary.snapshot!!.warnings.all { it.warning.key.startsWith("lbl.export.warning.") })
        frames() // with the summary and its warnings

        // Branches: no splash, Jolt (warning line), a custom frame cap, Debug mode.
        project.splashSeconds = 0f
        project.physicsBackend = PhysicsBackend.JOLT
        project.targetFps = 75
        window.wizard.mode = BuildMode.DEBUG
        frames()
        project.iconPath = ""
        frames()

        // Finished export with warnings, then a failed one.
        window.wizard.start()
        window.wizard.onFinished(ExportResult(File(dir, "out"), assetCount = 3, warnings = listOf(ExportNotice(ExportWarning.ICON_NOT_SET), ExportNotice(ExportWarning.SPLASH_NOT_FOUND, listOf("x.png")))))
        frames()
        window.wizard.reset()
        window.wizard.start()
        window.wizard.onFinished(ExportResult(File(dir, "out"), com.pafoid.skate.engine.utils.ExportFailure.IO_ERROR))
        frames()
        assertTrue(published.isEmpty(), "rendering alone publishes nothing")
    }

    @Test
    fun Dialog_NothingIsCut_At150PercentAndEveryDensity_InEnglishAndFrench() = imgui {
        val project = project().apply { File(getProjectDirectory(), "Scenes").mkdirs(); targetFps = 75; physicsBackend = PhysicsBackend.JOLT }
        val jobs = mockk<JobSystem>(relaxed = true)
        val projects = mockk<ProjectManager>(relaxed = true).also {
            every { it.currentProject } returns project
            every { it.hasProject() } returns true
        }
        val settingsManager = mockk<EditorSettingsManager>(relaxed = true).also { every { it.editorSettings } returns EditorSettings() }
        val densest = com.pafoid.skate.editor.settings.AppearanceSettings(
            com.pafoid.skate.editor.settings.Density.CUSTOM,
            com.pafoid.skate.editor.settings.Spacing(24f, 24f, 24f, 24f, 24f, 24f),
        )
        val appearances = com.pafoid.skate.editor.settings.Density.entries.filter { it != com.pafoid.skate.editor.settings.Density.CUSTOM }
            .map { com.pafoid.skate.editor.settings.AppearanceSettings(density = it) } + densest
        val failures = ArrayList<String>()
        var fields = 0
        var buttons = 0
        try {
            for (locale in listOf("en", "fr")) {
                val strings = StringManager(logger, "en").apply { loadStrings(Assets.Strings.EDITOR_STRINGS); setLocale(locale) }
                assertTrue(strings.getString("lbl.export.splash").isNotBlank())
                val engine = mockk<Engine>(relaxed = true).also { e ->
                    every { e.stringManager } returns strings
                    every { e.eventSystem } returns events
                    every { e.logger } returns logger
                    every { e.jobSystem } returns jobs
                }
                val window = ExportWizardWindow(engine, projects, settingsManager).also { it.open() }
                for (appearance in appearances) for (scale in listOf(1f, 1.5f, EditorSettings.MAX_UI_SCALE)) for (width in listOf(520f, 900f)) {
                    ImGuiStyleManager.appearance = { appearance }
                    ImGuiStyleManager.uiScale = { scale }
                    ImGuiStyleManager.invalidate()
                    ImGuiStyleManager.sync("dark")
                    val case = "$locale ${appearance.density} @${(scale * 100).toInt()}% width=$width"
                    val seen = HashSet<String>()
                    ExportWizardWindow.fieldProbe = { name, maxX, clipMaxX ->
                        seen += name; fields++
                        if (maxX > clipMaxX + 0.5f) failures += "$case: field $name ends at $maxX past $clipMaxX"
                    }
                    com.pafoid.skate.editor.imgui.TrailingButtons.placed = { p ->
                        buttons++
                        if (p.minX < p.clipMinX - 0.5f || p.maxX > p.clipMaxX + 0.5f) failures += "$case: button cut off: $p"
                    }
                    repeat(2) {
                        ImGui.newFrame()
                        ImGui.setNextWindowPos(20f, 20f)
                        ImGui.setNextWindowSize(width, 950f)
                        window.imgui()
                        ImGui.render()
                    }
                    val expected = setOf("##export_icon_path", "##export_splash_path", "splash_seconds", "physics_backend", "target_fps", "custom_fps", "output", "game_jar")
                    if (!seen.containsAll(expected)) failures += "$case: not drawn: ${expected - seen}"
                }
            }
        } finally {
            ExportWizardWindow.fieldProbe = null
            com.pafoid.skate.editor.imgui.TrailingButtons.placed = null
            ImGuiStyleManager.uiScale = { 1f }
            ImGuiStyleManager.appearance = { com.pafoid.skate.editor.settings.AppearanceSettings.THEME_ONLY }
        }
        assertTrue(fields > 0 && buttons > 0)
        assertTrue(failures.isEmpty(), failures.take(20).joinToString("\n"))
    }

    @Test
    fun WarningText_UsesTheKeyAndItsArgs() {
        val strings = mockk<StringManager>()
        every { strings.getString("lbl.export.warning.icon_not_found", "a.png") } returns "Icon a.png was not found."
        assertEquals("Icon a.png was not found.", ExportWizardWindow.warningText(strings, ExportNotice(ExportWarning.ICON_NOT_FOUND, listOf("a.png"))))
        assertEquals("lbl.export.physics.jolt", ExportWizardWindow.physicsKey(PhysicsBackend.JOLT))
    }
}
