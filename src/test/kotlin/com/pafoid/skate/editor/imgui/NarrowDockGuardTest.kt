package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.components.imgui
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import com.pafoid.skate.editor.settings.AppearanceSettings
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * EDT-100: every component inspector, drawn in a window as narrow as the default right dock at the editor's minimum
 * size (1024 px wide, both tool window bars), at 100 % and 150 % UI scale with the real editor font and strings: no
 * value field sticks out of the window or is squeezed below a usable width, and nothing makes the window wider than
 * itself (it has no horizontal scrollbar, so that part could not be reached).
 */
class NarrowDockGuardTest {

    private val strings = StringManager(mockk(relaxed = true), "en").apply {
        loadStrings(Assets.Strings.EDITOR_STRINGS)
        addStrings(Assets.Strings.GAME_STRINGS)
    }
    private val engine = mockk<Engine>(relaxed = true).also {
        every { it.stringManager } returns strings
        every { it.eventSystem } returns EventSystem()
    }
    private val fields = ArrayList<InspectorLayout.Field>()

    @BeforeEach
    fun setup() {
        ImGui.createContext()
        val io = ImGui.getIO()
        io.iniFilename = null
        io.loadFonts(javaClass.getResourceAsStream(Assets.Fonts.FONTS_FILE)!!.readAllBytes())
        io.setDisplaySize(1600f, 1200f)
        io.deltaTime = 1f / 60f
        ImGuiStyleManager.invalidate()
        ImGuiStyleManager.library = ThemeLibrary.load(File(Assets.Themes.DIR))
        MImGui.strings = strings
        InspectorLayout.probe = { fields += it }
    }

    @AfterEach
    fun teardown() {
        InspectorLayout.probe = null
        MImGui.strings = null
        ImGuiStyleManager.uiScale = { 1f }
        ImGuiStyleManager.appearance = { AppearanceSettings.THEME_ONLY }
        ImGuiStyleManager.library = ThemeLibrary(emptyList())
        ImGui.destroyContext()
    }

    /** The right dock's width at the minimum editor size: (1024 - both bars) × (1 - left) × right ratio. */
    private fun narrowestRightDock(): Float =
        (1024f - 2f * ToolWindowBarView.width()) * (1f - DefaultDockLayout.LEFT_RATIO) * DefaultDockLayout.RIGHT_RATIO

    private class Problem(val text: String)

    /** Draws [component]'s inspector at [width]; returns what does not fit. */
    private fun check(component: Component, width: Float): List<Problem> {
        val problems = ArrayList<Problem>()
        var scrollMaxX = 0f
        // Several frames: ImGui sizes the scroll range from the previous frame, before the scrollbar took its width.
        repeat(4) {
            fields.clear()
            ImGui.newFrame()
            ImGui.setNextWindowPos(0f, 0f)
            ImGui.setNextWindowSize(width, 1180f)
            ImGui.begin("Properties###narrow_probe", ImGuiWindowFlags.NoSavedSettings)
            component.imgui(engine)
            scrollMaxX = ImGui.getScrollMaxX()
            ImGui.end()
            ImGui.render()
        }
        val name = component.javaClass.simpleName
        val minField = ImGui.getFontSize() * InspectorLayout.MIN_FIELD_EMS
        if (scrollMaxX > 0.5f) problems += Problem("$name: content is ${scrollMaxX}px wider than the window")
        for (f in fields) {
            if (f.maxX > f.areaMaxX + 0.5f) problems += Problem("$name.${f.id}: ends at ${f.maxX}, past the edge ${f.areaMaxX}")
            if (f.id.startsWith("axis_") && f.maxX - f.minX < minField - 0.5f) problems += Problem("$name.${f.id}: ${f.maxX - f.minX}px wide")
        }
        return problems
    }

    private fun components(): List<Component> = ComponentType.entries.mapNotNull { type ->
        runCatching { type.instantiate() }.getOrNull()?.also { c -> GameObject("probe").addComponent(c) }
    }

    private fun sweep(scale: Float): List<String> {
        ImGuiStyleManager.uiScale = { scale }
        ImGuiStyleManager.invalidate()
        ImGuiStyleManager.sync("dark")
        val width = narrowestRightDock()
        return components().flatMap { check(it, width) }.map { "@${(scale * 100).toInt()}% ${it.text}" }
    }

    @Test
    fun EveryInspector_FitsTheNarrowestRightDock() {
        val failures = sweep(1f) + sweep(1.5f)
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun TheShapeIsRight_AndTheProbeSawTheTransform() {
        ImGuiStyleManager.sync("dark")
        val width = narrowestRightDock()
        assertTrue(width in 150f..200f, "the right dock at 1024 px is about 170 px: $width")
        val transform = ComponentType.TRANSFORM.instantiate()!!
        check(transform, width)
        assertEquals(9, fields.count { it.id.startsWith("axis_") }, "translation, rotation and scale: 3 axes each")
    }

    @Test
    fun Rows_StackWhenNarrow_AndSitSideBySideWhenWide() {
        assertTrue(InspectorLayout.row(170f, 13f).stacked)
        val wide = InspectorLayout.row(400f, 13f)
        assertFalse(wide.stacked)
        assertEquals(160f, wide.labelWidth)
        assertEquals(13f * InspectorLayout.MAX_LABEL_EMS, InspectorLayout.row(1200f, 13f).labelWidth, "capped")
        assertTrue(InspectorLayout.axes(300f, 3, 22f, 4f, 13f).oneLine)
        assertFalse(InspectorLayout.axes(150f, 3, 22f, 4f, 13f).oneLine)
    }

    @Test
    fun Environment_HasNoRawKeys() {
        val fields = EnvironmentComponent::class.java.declaredFields.filter {
            !Modifier.isStatic(it.modifiers) && !Modifier.isTransient(it.modifiers) && it.name in Component.getCachedFields(EnvironmentComponent::class.java).map { f -> f.name }
        }
        val inspected = fields.filter { f -> f.type.isPrimitive || f.type.isEnum || f.type.name.startsWith("org.joml") || f.type == String::class.java || f.name == "skyTexture" }
        assertTrue(inspected.size > 20, "found the inspector's fields: ${inspected.map { it.name }}")
        for (locale in listOf("en", "fr")) {
            val table = StringManager(mockk(relaxed = true), "en").apply { loadStrings(Assets.Strings.EDITOR_STRINGS); if (locale != "en") setLocale(locale) }
            val missing = inspected.map { "component.EnvironmentComponent.${it.name}" }.filterNot(table::hasString)
            assertEquals(emptyList<String>(), missing, locale)
            val enumMissing = com.pafoid.skate.engine.ecs.config.FogMode.entries.map(EnumLabels::key).filterNot(table::hasString)
            assertEquals(emptyList<String>(), enumMissing, locale)
        }
    }

    /** The dockable windows a test can build: every one must fit the narrowest right dock too. */
    private fun windows(): List<EditorWindow> {
        val logger = com.pafoid.skate.engine.core.LoggerService().also { it.consoleSink = { _, _ -> } }
        val events = EventSystem(logger)
        val scenes = mockk<com.pafoid.skate.engine.ecs.SceneManager>(relaxed = true).also {
            every { it.currentScene } returns null
            every { it.openScenes } returns mutableListOf()
        }
        val windowEngine = mockk<Engine>(relaxed = true).also {
            every { it.stringManager } returns strings
            every { it.eventSystem } returns events
            every { it.logger } returns logger
            every { it.sceneManager } returns scenes
        }
        val projects = mockk<com.pafoid.skate.editor.systems.ProjectManager>(relaxed = true).also {
            every { it.currentProject } returns null
            every { it.hasProject() } returns false
            every { it.getProjectDirectory() } returns null
        }
        val undo = com.pafoid.skate.editor.systems.UndoRedoManager(events, logger)
        return listOf(
            com.pafoid.skate.editor.ui.windows.ConsoleWindow(windowEngine),
            com.pafoid.skate.editor.ui.windows.CommandHistoryWindow(windowEngine, undo),
            com.pafoid.skate.editor.ui.windows.ProfilerWindow(strings),
            com.pafoid.skate.editor.ui.windows.AudioInspectorWindow(windowEngine, projects),
            com.pafoid.skate.editor.ui.windows.SceneHierarchyWindow(windowEngine, mockk(relaxed = true)) { false },
            com.pafoid.skate.editor.ui.windows.ProjectWindow(windowEngine, projects),
        )
    }

    @Test
    fun DockableWindows_FitTheNarrowestRightDock() {
        val failures = ArrayList<String>()
        val overflow = HashMap<String, Float>()
        InspectorLayout.windowProbe = { name, x -> overflow[name] = x }
        try {
            for (scale in listOf(1f, 1.5f)) {
                ImGuiStyleManager.uiScale = { scale }
                ImGuiStyleManager.invalidate()
                ImGuiStyleManager.sync("dark")
                val width = narrowestRightDock()
                for (window in windows()) {
                    window.isOpen.set(true)
                    overflow.clear()
                    repeat(5) {
                        ImGui.newFrame()
                        ImGui.setNextWindowPos(0f, 0f)
                        ImGui.setNextWindowSize(width, 900f)
                        window.imgui()
                        ImGui.render()
                    }
                    val x = overflow[window.name]
                    if (x == null) failures += "@${(scale * 100).toInt()}% ${window.name}: never reported (no beginWindow?)"
                    else if (x > 0.5f) failures += "@${(scale * 100).toInt()}% ${window.name}: content is ${x}px wider than the window"
                }
            }
        } finally {
            InspectorLayout.windowProbe = null
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
