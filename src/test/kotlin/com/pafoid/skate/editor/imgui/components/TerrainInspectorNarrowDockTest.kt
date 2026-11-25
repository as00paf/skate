package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.DefaultDockLayout
import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.imgui.InspectorLayout
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.ToolWindowBarView
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.loadFonts
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import com.pafoid.skate.editor.settings.AppearanceSettings
import com.pafoid.skate.editor.terrain.TerrainTool
import com.pafoid.skate.editor.terrain.TerrainToolState
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.TerrainStreaming
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.terrain.StampFootprint
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.engine.terrain.TerrainStamp
import imgui.ImGui
import imgui.assertion.ImAssertCallback
import imgui.flag.ImGuiWindowFlags
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * EDT-92 review (EDT-100 rules): the terrain inspector with its Asset, Tools and Stamps sections open, for every tool,
 * with a selected rectangle and circle stamp, with and without an asset and with the spacing note, fits the narrowest
 * right dock at 100 % and 150 % UI scale in English and French. NarrowDockGuardTest only sees the default inspector
 * with every section closed. Checks what the guard checks: no horizontal overflow, no value field past the edge.
 */
class TerrainInspectorNarrowDockTest {
    private val fields = ArrayList<InspectorLayout.Field>()
    private val asserts = ArrayList<String>()

    @AfterEach
    fun teardown() {
        InspectorLayout.probe = null
        MImGui.strings = null
        ImGuiStyleManager.uiScale = { 1f }
        ImGuiStyleManager.appearance = { AppearanceSettings.THEME_ONLY }
        ImGuiStyleManager.library = ThemeLibrary(emptyList())
        TerrainToolState.shared.reset()
    }

    private fun strings(locale: String) = StringManager(mockk(relaxed = true), "en").apply {
        loadStrings(Assets.Strings.EDITOR_STRINGS)
        addStrings(Assets.Strings.GAME_STRINGS)
        setLocale(locale)
    }

    private fun narrowestRightDock(): Float =
        (1024f - 2f * ToolWindowBarView.width()) * (1f - DefaultDockLayout.LEFT_RATIO) * DefaultDockLayout.RIGHT_RATIO

    private fun terrain(): TerrainComponent {
        val t = TerrainComponent(sizeMetres = 32f, sampleSpacing = 1f, chunkQuads = 8, recipe = TerrainPreset.ROLLING_HILLS.recipe(),
            streaming = TerrainStreaming.OFF, seedName = "terrain/narrow")
        GameObject("A rather long terrain object name").addComponent(Transform()).addComponent(t)
        t.runtime.ensureBuilt(t.key(1L), parallel = false)
        t.stamps += TerrainStamp(x = 2f)
        t.stamps += TerrainStamp(footprint = StampFootprint.CIRCLE, radius = 3f)
        return t
    }

    /** Draws [t] at [width] with the sections open; returns what does not fit. */
    private fun check(t: TerrainComponent, strings: StringManager, engine: Engine, width: Float, case: String): List<String> {
        val problems = ArrayList<String>()
        var scrollMaxX = 0f
        repeat(4) {
            fields.clear()
            ImGui.newFrame()
            ImGui.setNextWindowPos(0f, 0f)
            ImGui.setNextWindowSize(width, 1600f)
            ImGui.begin("Properties###terrain_narrow_probe", ImGuiWindowFlags.NoSavedSettings)
            for (key in SECTIONS) ImGui.getStateStorage().setInt(ImGui.getID(strings.label(key)), 1)
            t.imgui(engine)
            scrollMaxX = ImGui.getScrollMaxX()
            ImGui.end()
            ImGui.render()
        }
        if (scrollMaxX > 0.5f) problems += "$case: content is ${scrollMaxX}px wider than the window"
        for (f in fields) if (f.maxX > f.areaMaxX + 0.5f) problems += "$case.${f.id}: ends at ${f.maxX}, past the edge ${f.areaMaxX}"
        return problems
    }

    @Test
    fun TerrainInspector_AllSectionsOpen_FitsTheNarrowestRightDock() {
        val problems = ArrayList<String>()
        var probed = 0
        for (locale in listOf("en", "fr")) for (scale in listOf(1f, 1.5f)) {
            val strings = strings(locale)
            val engine = mockk<Engine>(relaxed = true).also {
                every { it.stringManager } returns strings
                every { it.eventSystem } returns EventSystem()
            }
            ImGui.createContext()
            ImGui.setAssertCallback(object : ImAssertCallback() {
                override fun imAssertCallback(assertion: String, line: Int, file: String) { asserts += "$assertion ($file:$line)" }
            })
            try {
                val io = ImGui.getIO()
                io.iniFilename = null
                io.loadFonts(javaClass.getResourceAsStream(Assets.Fonts.FONTS_FILE)!!.readAllBytes())
                io.setDisplaySize(1600f, 1700f)
                io.deltaTime = 1f / 60f
                ImGuiStyleManager.library = ThemeLibrary.load(File(Assets.Themes.DIR))
                ImGuiStyleManager.uiScale = { scale }
                ImGuiStyleManager.invalidate()
                ImGuiStyleManager.sync("dark")
                MImGui.strings = strings
                InspectorLayout.probe = { fields += it }
                val width = narrowestRightDock()
                val state = TerrainToolState.shared

                val t = terrain()
                for (tool in TerrainTool.entries) for (stamp in listOf(0, 1)) {
                    state.tool = tool
                    state.selectedStamp = stamp
                    problems += check(t, strings, engine, width, "@$locale ${(scale * 100).toInt()}% $tool stamp $stamp")
                    probed += fields.size
                }
                t.asset = "0123456789abcdef"
                problems += check(t, strings, engine, width, "@$locale ${(scale * 100).toInt()}% with asset")

                // The spacing note (a 16 km terrain at 0.5 m is capped)
                val big = TerrainComponent(sizeMetres = 16000f, sampleSpacing = 0.5f, streaming = TerrainStreaming.OFF)
                GameObject("Big").addComponent(Transform()).addComponent(big)
                problems += check(big, strings, engine, width, "@$locale ${(scale * 100).toInt()}% spacing note")
            } finally {
                ImGui.destroyContext()
            }
        }
        assertTrue(asserts.isEmpty(), "ImGui asserts: $asserts")
        assertTrue(probed > 100, "the probe saw the value rows: $probed")
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private companion object {
        val SECTIONS = listOf(
            "component.TerrainComponent.section.asset", "component.TerrainComponent.section.tools", "component.TerrainComponent.section.stamps",
        )
    }
}
