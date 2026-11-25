package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.imgui.MImGui
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
import com.pafoid.skate.engine.core.LoggerService
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
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * EDT-92: the terrain inspector draws headless with its Asset, Tools and Stamps sections open, for every tool, with
 * and without an asset and a selected stamp, with balanced ImGui stacks and only string keys (no literal text).
 */
class TerrainInspectorRenderTest {
    private val asserts = mutableListOf<String>()
    private val requested = mutableSetOf<String>()

    private val strings = mockk<StringManager>(relaxed = true) {
        every { getString(any()) } answers { firstArg<String>().also { requested += it }.let { "!!$it!!" } }
        every { getString(any(), *anyVararg()) } answers { firstArg<String>().also { requested += it }.let { "!!$it!!" } }
        every { hasString(any()) } returns false
    }
    private val engine = mockk<Engine>(relaxed = true) {
        every { stringManager } returns strings
        every { eventSystem } returns EventSystem()
        every { logger } returns LoggerService().apply { consoleSink = { _, _ -> } }
    }

    @AfterEach
    fun teardown() {
        ImGuiStyleManager.uiScale = { 1f }
        ImGuiStyleManager.appearance = { AppearanceSettings.THEME_ONLY }
        ImGuiStyleManager.library = ThemeLibrary(emptyList())
        MImGui.strings = null
        TerrainToolState.shared.reset()
    }

    private fun terrain(): TerrainComponent {
        val t = TerrainComponent(sizeMetres = 32f, sampleSpacing = 1f, chunkQuads = 8, recipe = TerrainPreset.ROLLING_HILLS.recipe(),
            streaming = TerrainStreaming.OFF, seedName = "terrain/inspector")
        GameObject("Hills").addComponent(Transform()).addComponent(t)
        t.runtime.ensureBuilt(t.key(1L), parallel = false)
        t.stamps += TerrainStamp(x = 2f)
        t.stamps += TerrainStamp(footprint = StampFootprint.CIRCLE, radius = 3f)
        return t
    }

    private fun draw(t: TerrainComponent, frames: Int = 3) {
        ImGui.createContext()
        ImGui.setAssertCallback(object : ImAssertCallback() {
            override fun imAssertCallback(assertion: String, line: Int, file: String) { asserts += "$assertion ($file:$line)" }
        })
        val io = ImGui.getIO()
        io.iniFilename = null
        io.loadFonts(javaClass.getResourceAsStream(Assets.Fonts.FONTS_FILE)!!.readAllBytes())
        io.setDisplaySize(1600f, 1000f)
        io.deltaTime = 1f / 60f
        ImGuiStyleManager.invalidate()
        ImGuiStyleManager.sync("dark")
        try {
            repeat(frames) {
                ImGui.newFrame()
                ImGui.setNextWindowPos(20f, 20f)
                ImGui.setNextWindowSize(460f, 960f)
                ImGui.begin("Properties##terrain_probe")
                // Open the collapsing headers the inspector draws (stable IDs: text###key)
                for (key in SECTIONS) ImGui.getStateStorage().setInt(ImGui.getID(strings.label(key)), 1)
                t.imgui(engine)
                ImGui.end()
                ImGui.render()
            }
        } finally {
            ImGui.destroyContext()
        }
    }

    @Test
    fun TerrainInspector_EveryTool_DrawsWithBalancedStacks_AndOnlyKeys() {
        ImGuiStyleManager.library = ThemeLibrary.load(File(Assets.Themes.DIR))
        MImGui.strings = strings
        val t = terrain()
        val state = TerrainToolState.shared
        for (tool in TerrainTool.entries) {
            state.tool = tool
            state.selectedStamp = if (tool == TerrainTool.STAMP) 1 else 0
            draw(t)
            assertTrue(asserts.isEmpty(), "ImGui asserts with $tool: $asserts")
        }
        assertSame(t, state.terrain, "the inspector hands its terrain to the viewport tool")

        // Without an asset the section offers to create one; the sections, every tool and the stamp fields drew
        val expected = SECTIONS + TerrainTool.entries.map { toolKey(it) } + listOf(
            "component.TerrainComponent.asset.none", "component.TerrainComponent.asset.create",
            "component.TerrainComponent.preset", "component.TerrainComponent.applyPreset", "component.TerrainComponent.reroll",
            "component.TerrainComponent.brush.radius", "component.TerrainComponent.brush.strength", "component.TerrainComponent.brush.biome",
            "component.TerrainComponent.showBiomes", "component.TerrainComponent.stamp.row", "component.TerrainComponent.stamp.add",
            "component.TerrainComponent.stamp.remove", "component.TerrainComponent.stamp.radius", "component.TerrainComponent.stamp.width",
            "component.TerrainComponent.stamp.falloff", "component.TerrainComponent.tool.hint.brush", "component.TerrainComponent.tool.hint.stamp",
        )
        val missing = expected.filter { it !in requested }
        assertEquals(emptyList<String>(), missing, "keys the inspector should have drawn")

        // With an asset it names it instead
        t.asset = "0123456789abcdef"
        requested.clear()
        draw(t, frames = 1)
        assertTrue("component.TerrainComponent.asset.linked" in requested)
        assertTrue(asserts.isEmpty(), "$asserts")
    }

    @Test
    fun TerrainInspector_NotesACappedSpacing() {
        ImGuiStyleManager.library = ThemeLibrary.load(File(Assets.Themes.DIR))
        MImGui.strings = strings
        // 16 km at 0.5 m would exceed the resident sample budget: the spacing is capped
        val t = TerrainComponent(sizeMetres = 16000f, sampleSpacing = 0.5f, streaming = TerrainStreaming.OFF)
        GameObject("Big").addComponent(Transform()).addComponent(t)
        assertTrue(t.spacingCapped())
        draw(t, frames = 1)
        assertTrue("component.TerrainComponent.spacingCapped" in requested)
        assertTrue(asserts.isEmpty(), "$asserts")
    }

    private companion object {
        val SECTIONS = listOf(
            "component.TerrainComponent.section.asset", "component.TerrainComponent.section.tools", "component.TerrainComponent.section.stamps",
        )
    }
}
