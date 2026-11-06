package com.pafoid.skate.editor.settings

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.GizmoSystem
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-57: the new editor settings persist with safe defaults and drive tooltips, UI scale, snapping and the camera. */
class EditorSettingsEdt57Test {
    private val serializer = Serializer()

    @AfterEach
    fun resetTooltips() {
        Tooltips.enabled = { true }
    }

    @Test
    fun Settings_RoundTripTheNewFields() {
        val saved = EditorSettings(
            showTooltips = false, fontSize = 1.25f, loadLastProjectOnStartup = false,
            gizmoSnap = GizmoSnapSettings(translate = 0.25f, rotateDegrees = 45f, scale = 0.5f),
            camera = EditorCameraSettings(moveSpeed = 12f, lookSensitivity = 2f),
        )
        assertEquals(saved, serializer.decode<EditorSettings>(serializer.encode(saved)))
    }

    @Test
    fun Settings_AnOlderConfigLoadsWithTheDefaults() {
        val old = serializer.decode<EditorSettings>("""{"language":"fr","theme":"dark"}""")
        assertTrue(old.showTooltips)
        assertEquals(GizmoSnapSettings(), old.gizmoSnap)
        assertEquals(EditorCameraSettings(), old.camera)
        assertEquals("fr", old.language)
    }

    @Test
    fun Settings_OutOfRangeValuesAreClamped() {
        assertEquals(GizmoSnapSettings.MIN_STEP, GizmoSnapSettings(translate = -3f).sanitized().translate)
        assertEquals(GizmoSnapSettings.MAX_ROTATE_DEGREES, GizmoSnapSettings(rotateDegrees = 999f).sanitized().rotateDegrees)
        assertEquals(EditorCameraSettings.MIN_MOVE_SPEED, EditorCameraSettings(moveSpeed = 0f).sanitized().moveSpeed)
        assertEquals(EditorCameraSettings.MAX_LOOK_SENSITIVITY, EditorCameraSettings(lookSensitivity = 50f).sanitized().lookSensitivity)
    }

    @Test
    fun Tooltips_OffHidesHelpButKeepsFieldExplanationsAndData() {
        Tooltips.enabled = { false }
        assertFalse(Tooltips.shows(Tooltips.Kind.HELP))
        assertTrue(Tooltips.shows(Tooltips.Kind.ALWAYS))
        Tooltips.enabled = { true }
        assertTrue(Tooltips.shows(Tooltips.Kind.HELP))
    }

    @Test
    fun UiScale_MultipliesTheThemeFontScale() {
        assertEquals(1.5f, ImGuiStyleManager.fontScale(themeScale = 1.2f, uiScale = 1.25f), 1e-6f)
    }

    @Test
    fun Snap_UsesTheSavedStepsWhileOn() {
        var settings = EditorSettings(gizmoSnap = GizmoSnapSettings(translate = 2f, rotateDegrees = 30f, scale = 0.2f))
        val manager = mockk<EditorSettingsManager> { every { editorSettings } answers { settings } }
        val gizmos = GizmoSystem(mockk<Engine>(relaxed = true), manager, mockk(relaxed = true), EditorCamera(mockk(relaxed = true), EditorInputState()))

        assertEquals(0f, gizmos.translateSnap)
        gizmos.toggleSnap()
        assertEquals(2f, gizmos.translateSnap)
        assertEquals(30f, gizmos.rotateSnapDegrees)
        assertEquals(0.2f, gizmos.scaleSnap)
        // A change in Editor Settings applies while snapping stays on.
        settings = settings.copy(gizmoSnap = GizmoSnapSettings(translate = 1f))
        assertEquals(1f, gizmos.translateSnap)
        gizmos.toggleSnap()
        assertFalse(gizmos.snapEnabled)
        assertEquals(0f, gizmos.rotateSnapDegrees)
    }

    @Test
    fun Camera_FliesAtTheSavedSpeed() {
        val camera = EditorCamera(mockk<Engine>(relaxed = true), EditorInputState()) { EditorCameraSettings(moveSpeed = 12f) }
        camera.transform.translation.set(0f, 0f, 0f)
        repeat(60) { camera.flyStep(0f, 1f, 0f, 1f / 60f) }
        assertEquals(12f, camera.transform.translation.length(), 1e-3f)
    }
}
