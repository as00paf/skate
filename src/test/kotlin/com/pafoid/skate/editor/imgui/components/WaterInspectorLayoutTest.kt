package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.loadFonts
import com.pafoid.skate.editor.imgui.theme.ThemeLibrary
import com.pafoid.skate.editor.settings.AppearanceSettings
import com.pafoid.skate.editor.settings.Density
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.ecs.components.WaterPreset
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import imgui.assertion.ImAssertCallback
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * EDT-90 review: the water and Environment inspectors with the real English and French strings, at 100 % and 150 %
 * and every Density, in a narrow inspector: no ImGui assert, and the rows this task adds (water preset, Freeze Time,
 * waves, material, wind preset) are never wider than the window (the wind presets used to be a row of buttons that
 * clipped).
 */
class WaterInspectorLayoutTest {
    private val asserts = mutableListOf<String>()

    @AfterEach
    fun teardown() {
        ImGuiStyleManager.uiScale = { 1f }
        ImGuiStyleManager.appearance = { AppearanceSettings.THEME_ONLY }
        ImGuiStyleManager.library = ThemeLibrary(emptyList())
        MImGui.strings = null
    }

    private fun strings(locale: String) = StringManager(mockk(relaxed = true), "en").apply {
        loadStrings(Assets.Strings.EDITOR_STRINGS)
        addStrings(Assets.Strings.GAME_STRINGS)
        if (locale != "en") setLocale(locale)
    }

    /** Draws [draw] for a few frames in a [width]-px window; returns how far content overflows it horizontally. */
    private fun overflow(engine: Engine, width: Float, draw: (Engine) -> Unit): Float {
        ImGui.createContext()
        ImGui.setAssertCallback(object : ImAssertCallback() {
            override fun imAssertCallback(assertion: String, line: Int, file: String) {
                asserts += "$assertion ($file:$line)"
            }
        })
        var overflow = 0f
        try {
            val io = ImGui.getIO()
            io.iniFilename = null
            io.loadFonts(javaClass.getResourceAsStream(Assets.Fonts.FONTS_FILE)!!.readAllBytes())
            io.setDisplaySize(1600f, 1200f)
            io.deltaTime = 1f / 60f
            ImGuiStyleManager.invalidate()
            ImGuiStyleManager.sync("dark")
            repeat(3) {
                ImGui.newFrame()
                ImGui.setNextWindowPos(10f, 10f)
                ImGui.setNextWindowSize(width, 1150f)
                ImGui.begin("Properties##layout_probe")
                draw(engine)
                overflow = ImGui.getScrollMaxX()
                ImGui.end()
                ImGui.render()
            }
        } finally {
            ImGui.destroyContext()
        }
        return overflow
    }

    @Test
    fun waterAndWindInspectors_FitANarrowInspector_InEnglishAndFrench_AtEveryDensityAndScale() {
        ImGuiStyleManager.library = ThemeLibrary.load(File(Assets.Themes.DIR))
        val failures = mutableListOf<String>()
        for (locale in listOf("en", "fr")) {
            val strings = strings(locale)
            MImGui.strings = strings
            val scene = Scene("layout").apply { addComponent(EnvironmentComponent()) }
            val water = WaterComponent().apply { applyPreset(WaterPreset.OCEAN_SWELL) }
            GameObject("Pool").addComponent(water)
            val environment = scene.getComponent<EnvironmentComponent>()!!
            val engine = mockk<Engine>(relaxed = true) {
                every { stringManager } returns strings
                every { eventSystem } returns EventSystem()
                every { logger } returns LoggerService().apply { consoleSink = { _, _ -> } }
                every { sceneManager.currentScene } returns scene
            }
            for (density in Density.entries) for (scale in listOf(1f, 1.5f)) {
                ImGuiStyleManager.appearance = { AppearanceSettings(density = density) }
                ImGuiStyleManager.uiScale = { scale }
                val case = "$locale ${density.name} @${(scale * 100).toInt()}%"
                // The Properties window's narrow docked width, scaled with the UI
                val width = 380f * scale
                asserts.clear()
                // Whole inspectors: balanced stacks, no assert
                overflow(engine, width) { water.imgui(it) }
                overflow(engine, width) { environment.imgui(it) }
                if (asserts.isNotEmpty()) failures += "$case: asserts $asserts"
                // The rows this task adds must fit; the shared generic rows are measured by skate-editor
                val waterRows = overflow(engine, width) {
                    water.presetCombo(it)
                    freezeTime(it)
                    water.waveList(it, WaterWaveGestures())
                    water.materialPicker(it)
                }
                val windRow = overflow(engine, width) { environment.windPresetRow(it) }
                if (waterRows > 0.5f) failures += "$case: water rows overflow by $waterRows px"
                if (windRow > 0.5f) failures += "$case: wind preset row overflows by $windRow px"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun overflowProbe_CatchesTheOldButtonRow() {
        ImGuiStyleManager.library = ThemeLibrary.load(File(Assets.Themes.DIR))
        val strings = strings("fr")
        MImGui.strings = strings
        ImGuiStyleManager.appearance = { AppearanceSettings(density = Density.SPACIOUS) }
        ImGuiStyleManager.uiScale = { 1.5f }
        val engine = mockk<Engine>(relaxed = true) { every { stringManager } returns strings }
        // A row of preset buttons like the rejected layout, made wide enough to be sure it overflows
        val wide = overflow(engine, 380f * 1.5f) {
            WindPreset.entries.forEachIndexed { i, preset ->
                if (i > 0) ImGui.sameLine()
                ImGui.button(com.pafoid.skate.editor.imgui.EnumLabels.of(strings, preset) + " " + strings.getString("component.EnvironmentComponent.windPreset"))
            }
        }
        assertTrue(wide > 0.5f, "the probe must see a row wider than the window ($wide px)")
    }
}
