package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.commands.objects.WaterWaveSettings
import com.pafoid.skate.editor.events.WaterAction
import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.loadFonts
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.WaterActionHandler
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
import com.pafoid.skate.engine.environment.WindField
import imgui.ImGui
import imgui.assertion.ImAssertCallback
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-90: water inspector and Environment wind section. */
class WaterInspectorTest {

    // --- gestures, presets, pickers ---

    @Test
    fun waveGesture_KeepsTheStateFromBeforeItsFirstChange() {
        val gestures = WaterWaveGestures()
        val water = WaterComponent()
        val start = WaterWaveSettings.of(water)
        assertNull(gestures.finish(water), "nothing open")

        gestures.changed(water, start)
        water.waves[0].amplitude = 0.2f
        gestures.changed(water, WaterWaveSettings.of(water))
        water.waves[0].amplitude = 0.3f

        val edit = gestures.finish(water)!!
        assertEquals(start, edit.before)
        assertEquals(0.3f, edit.after.waves[0].amplitude)
        assertFalse(gestures.isOpen(water))
    }

    @Test
    fun presetMatch_RecognisesAnUntouchedPreset() {
        val water = WaterComponent()
        assertTrue(water.matches(WaterPreset.LAKE), "the default is the Lake preset")
        water.applyPreset(WaterPreset.RIVER)
        assertTrue(water.matches(WaterPreset.RIVER))
        water.waves[0].steepness = 0.9f
        assertTrue(WaterPreset.entries.none { water.matches(it) }, "edited waves are custom")
    }

    @Test
    fun materialDrop_AcceptsOnlySkmatFiles() {
        assertTrue(isWaterMaterialPath("Assets/Materials/lagoon.skmat"))
        assertTrue(isWaterMaterialPath("engine:Materials/water_pool.SKMAT"))
        assertFalse(isWaterMaterialPath("Assets/Textures/foam.png"))
    }

    @Test
    fun windPreset_FollowsTheWindFieldSpeeds() {
        assertEquals(WindPreset.BREEZE, WindPreset.of(WindField.BREEZE))
        assertEquals(WindPreset.STORM, WindPreset.of(16f))
        assertNull(WindPreset.of(5f), "a custom speed")
        assertEquals(listOf(0.5f, 3f, 8f, 16f), WindPreset.entries.map { it.speed })
    }

    // --- undo ---

    @Test
    fun presetFromTheInspector_IsOneUndoStep_NotRecordedTwiceByTheEditTracker() {
        val events = EventSystem()
        val undo = UndoRedoManager(events, LoggerService().apply { consoleSink = { _, _ -> } })
        WaterActionHandler(events, undo)
        val tracker = InspectorEditTracker()
        val water = WaterComponent()
        val lake = WaterWaveSettings.of(water)

        tracker.track(water) {
            val river = WaterWaveSettings.of(WaterComponent().apply { applyPreset(WaterPreset.RIVER) })
            events.publish(WaterAction.EditWaves(water, WaterWaveSettings.of(water), river))
        }

        assertTrue(tracker.settle(anyItemActive = false).isEmpty(), "the command already covers flow and material")
        assertEquals(1, undo.getUndoCount())
        assertTrue(water.matches(WaterPreset.RIVER))
        undo.undo()
        assertEquals(lake, WaterWaveSettings.of(water))
    }

    @Test
    fun noOpEdit_AddsNoUndoStep() {
        val events = EventSystem()
        val undo = UndoRedoManager(events, LoggerService().apply { consoleSink = { _, _ -> } })
        WaterActionHandler(events, undo)
        val water = WaterComponent()
        events.publish(WaterAction.EditWaves(water, WaterWaveSettings.of(water), WaterWaveSettings.of(water)))
        assertEquals(0, undo.getUndoCount())
    }

    // --- headless render ---

    private val asked = mutableListOf<String>()
    private val asserts = mutableListOf<String>()
    private val strings = mockk<StringManager>(relaxed = true) {
        every { getString(any()) } answers { firstArg<String>().also { asked += it } }
        every { getString(any(), *anyVararg()) } answers { firstArg<String>().also { asked += it } }
        every { hasString(any()) } returns false
    }

    @AfterEach
    fun teardown() {
        MImGui.strings = null
    }

    private fun render(scene: Scene, draw: (Engine) -> Unit) {
        val engine = mockk<Engine>(relaxed = true) {
            every { stringManager } returns strings
            every { eventSystem } returns EventSystem()
            every { logger } returns LoggerService().apply { consoleSink = { _, _ -> } }
            every { sceneManager.currentScene } returns scene
        }
        MImGui.strings = strings
        ImGui.createContext()
        ImGui.setAssertCallback(object : ImAssertCallback() {
            override fun imAssertCallback(assertion: String, line: Int, file: String) {
                asserts += assertion
            }
        })
        try {
            val io = ImGui.getIO()
            io.iniFilename = null
            io.loadFonts(javaClass.getResourceAsStream(Assets.Fonts.FONTS_FILE)!!.readAllBytes())
            io.setDisplaySize(1280f, 900f)
            io.deltaTime = 1f / 60f
            ImGuiStyleManager.invalidate()
            ImGuiStyleManager.sync("dark")
            repeat(2) {
                ImGui.newFrame()
                ImGui.begin("Properties##water_probe")
                draw(engine)
                ImGui.end()
                ImGui.render()
            }
        } finally {
            ImGui.destroyContext()
        }
        assertTrue(asserts.isEmpty(), "ImGui asserts: $asserts")
    }

    @Test
    fun waterInspector_DrawsEverySection_WithFreezeTimeFromTheSceneEnvironment() {
        val scene = Scene("water").apply { addComponent(EnvironmentComponent()) }
        val water = WaterComponent().apply { applyPreset(WaterPreset.OCEAN_SWELL) }
        GameObject("Pool").addComponent(water)

        render(scene) { water.imgui(it) }

        val expected = listOf(
            "section.surface", "waves", "preset", "freezeTime", "wave.title", "wave.direction", "wave.wavelength",
            "wave.amplitude", "wave.steepness", "wave.add", "section.flow", "material", "section.buoyancy",
            "density", "linearDrag", "angularDrag", "sizeX", "depth",
        ).map { "component.WaterComponent.$it" }
        assertTrue(asked.containsAll(expected), "missing: ${expected - asked.toSet()}")
        assertEquals(4, asked.count { it == "component.WaterComponent.wave.wavelength" } / 2, "4 ocean waves, 2 frames")
        assertFalse("component.WaterComponent.freezeTime.none" in asked)
    }

    @Test
    fun waterInspector_WithoutAnEnvironment_ExplainsFreezeTime() {
        val water = WaterComponent()
        render(Scene("dry")) { water.imgui(it) }
        assertTrue("component.WaterComponent.freezeTime.none" in asked)
    }

    @Test
    fun environmentInspector_GathersTheWindRowsUnderTheWindSection() {
        val scene = Scene("env")
        val environment = EnvironmentComponent()
        scene.addComponent(environment)

        render(scene) { environment.imgui(it) }

        val wind = asked.indexOf("component.EnvironmentComponent.wind")
        assertTrue(wind >= 0, "wind section")
        val speed = asked.indexOf("component.EnvironmentComponent.windSpeed")
        assertTrue(speed > wind, "wind speed is drawn in the wind section")
        assertTrue(asked.indexOf("component.EnvironmentComponent.ambientIntensity") in 0 until wind, "other rows come first")
    }
}
