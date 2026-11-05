package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.commands.objects.EditComponentFieldsCommand
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.Transform
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-10: direct inspector writes become one undoable edit per gesture. */
class InspectorEditTrackerTest {
    private var clock = 0L
    private val tracker = InspectorEditTracker { clock }

    @Test
    fun Drag_OverSeveralFrames_IsOneEditFromFirstToLastValue() {
        val light = PointLightComponent(intensity = 1f)
        tracker.track(light) { light.intensity = 2f }
        assertTrue(tracker.settle(anyItemActive = true).isEmpty(), "still dragging")
        tracker.track(light) { light.intensity = 3f }
        val edits = tracker.settle(anyItemActive = false)

        assertEquals(1, edits.size)
        val change = edits.single().changes.entries.single()
        assertEquals("intensity", change.key.name)
        assertEquals(1f, change.value.before)
        assertEquals(3f, change.value.after)
    }

    @Test
    fun Edit_UndoAndRedo_RestoreValues() {
        val light = PointLightComponent()
        tracker.track(light) { light.color.set(0.5f, 0.25f, 0f); light.intensity = 4f }
        val command = EditComponentFieldsCommand(light, tracker.settle(false).single().changes)

        command.undo()
        assertEquals(Vector3f(1f), light.color)
        assertEquals(1f, light.intensity)
        command.execute()
        assertEquals(Vector3f(0.5f, 0.25f, 0f), light.color)
        assertEquals(4f, light.intensity)
    }

    @Test
    fun VectorEditedInPlace_KeepsItsInstanceOnUndo() {
        val light = PointLightComponent()
        val color = light.color
        tracker.track(light) { color.x = 0f }
        EditComponentFieldsCommand(light, tracker.settle(false).single().changes).undo()
        assertTrue(light.color === color, "code holding the vector still sees it")
        assertEquals(1f, color.x)
    }

    @Test
    fun ValueChangedByACommandDuringTheCall_IsNotRecordedTwice() {
        val light = PointLightComponent()
        tracker.track(light) { light.intensity = 5f; clock++ }
        assertTrue(tracker.settle(false).isEmpty())
    }

    @Test
    fun ChangedBackToTheStartValue_IsNoEdit() {
        val light = PointLightComponent()
        tracker.track(light) { light.intensity = 9f }
        tracker.track(light) { light.intensity = 1f }
        assertTrue(tracker.settle(false).isEmpty())
    }

    @Test
    fun TransformAndAnimatorPlayback_AreNotTracked() {
        val transform = Transform()
        tracker.track(transform) { transform.translation.x = 4f }
        val animator = Animator()
        tracker.track(animator) { animator.currentTime = 1.5f; animator.isPlaying = true }
        assertTrue(tracker.settle(false).isEmpty())
    }
}
