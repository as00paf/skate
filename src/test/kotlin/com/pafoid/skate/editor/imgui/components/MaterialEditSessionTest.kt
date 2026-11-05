package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.commands.objects.MaterialState
import com.pafoid.skate.engine.assets.data.models.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaterialEditSessionTest {

    private val session = MaterialEditSession()

    @Test
    fun Preview_AppliesLiveAndOneGestureBecomesOneEdit() {
        val m = Material()
        val start = MaterialState.of(m)
        session.preview(m, start.copy(metallic = 0.3f))
        assertEquals(0.3f, m.metallicFactor)
        session.preview(m, session.current(m).copy(metallic = 0.6f))
        assertEquals(0.6f, m.metallicFactor)
        assertTrue(session.settle(anyItemActive = true).isEmpty(), "Still dragging")
        assertTrue(session.isEditing(m))

        val edits = session.settle(anyItemActive = false)
        assertEquals(1, edits.size)
        assertSame(m, edits[0].material)
        assertEquals(start, edits[0].before)
        assertEquals(0.6f, edits[0].after.metallic)
        assertFalse(session.isEditing(m))
        assertTrue(session.settle(anyItemActive = false).isEmpty())
    }

    @Test
    fun Preview_IsClampedAndBackToStartIsNoEdit() {
        val m = Material()
        val start = MaterialState.of(m)
        session.preview(m, start.copy(roughness = 3f))
        assertEquals(1f, m.roughnessFactor)
        session.preview(m, start)
        assertTrue(session.settle(anyItemActive = false).isEmpty())
    }

    @Test
    fun Materials_AreTrackedByIdentityWhileTheirValuesChange() {
        // Two equal data-class materials must not share a gesture
        val a = Material()
        val b = Material()
        session.preview(a, MaterialState.of(a).copy(metallic = 1f))
        session.preview(b, MaterialState.of(b).copy(roughness = 0f))
        val edits = session.settle(anyItemActive = false)
        assertEquals(2, edits.size)
        assertEquals(1f, a.metallicFactor)
        assertEquals(0.5f, a.roughnessFactor)
        assertEquals(0f, b.roughnessFactor)
    }
}
