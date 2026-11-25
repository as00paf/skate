package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.imgui.loadFonts
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import imgui.ImGui
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/** EDT-94: the real Hierarchy window (headless ImGui frames) never shows runtime-only objects (terrain scatter). */
class SceneHierarchyRuntimeOnlyTest {

    @BeforeEach
    fun setup() {
        ImGui.createContext()
        val io = ImGui.getIO()
        io.iniFilename = null
        io.loadFonts(javaClass.getResourceAsStream(Assets.Fonts.FONTS_FILE)!!.readAllBytes())
        io.setDisplaySize(1600f, 1000f)
        io.deltaTime = 1f / 60f
        ImGuiStyleManager.invalidate()
        ImGuiStyleManager.library = ThemeLibrary.load(File(Assets.Themes.DIR))
        ImGuiStyleManager.sync("dark")
    }

    @AfterEach
    fun teardown() {
        ImGuiStyleManager.library = ThemeLibrary(emptyList())
        ImGui.destroyContext()
    }

    private val scene = Scene("Scatter")
    // The parent matches the search too: the "no results" check only looks at expanded rows.
    private val ground = GameObject("Rockfield")
    private val sign = GameObject("Rock sign")
    private val scatterRock = GameObject("Rock").apply { isRuntimeOnly = true; addChild(GameObject("Rock moss")) }
    private val strayRuntime = GameObject("Rock stray").apply { isRuntimeOnly = true }

    private fun window(): SceneHierarchyWindow {
        scene.addChild(ground)
        ground.addChild(scatterRock) // before the saved child: hidden rows must not shift anything
        ground.addChild(sign)
        scene.addChild(strayRuntime)
        val strings = mockk<StringManager>(relaxed = true)
        every { strings.getString(any()) } answers { firstArg() }
        every { strings.getString(any(), *anyVararg()) } answers { firstArg() }
        val engine = mockk<Engine>(relaxed = true) {
            every { stringManager } returns strings
            every { eventSystem } returns EventSystem()
            every { sceneManager.currentScene } returns scene
        }
        return SceneHierarchyWindow(engine, mockk(relaxed = true)).also { it.isOpen.set(true) }
    }

    private fun frames(window: SceneHierarchyWindow, n: Int = 3) = repeat(n) {
        ImGui.newFrame()
        ImGui.setNextWindowPos(20f, 20f)
        ImGui.setNextWindowSize(500f, 700f)
        window.imgui()
        ImGui.render()
    }

    @Test
    fun Hierarchy_NeverShowsRuntimeOnlyObjects_EvenWhenTheSearchMatchesThem() {
        val window = window()

        frames(window)
        assertEquals(listOf(ground), window.drawnRowsLastFrame)

        // "rock" matches the saved sign and every scatter object; the search opens the matching branches.
        window.searchQuery.set("rock")
        frames(window)
        assertEquals(listOf(ground, sign), window.drawnRowsLastFrame)
    }

    @Test
    fun Hierarchy_AParentWithOnlyRuntimeChildrenHasNoExpandArrow() {
        val window = window()
        ground.removeChild(sign)
        window.searchQuery.set("rockfield")

        frames(window)

        assertEquals(listOf(ground), window.drawnRowsLastFrame)
        assertEquals(emptyList<GameObject>(), ground.persistentChildren)
    }
}
