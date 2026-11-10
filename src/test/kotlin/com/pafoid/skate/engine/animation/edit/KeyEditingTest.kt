package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.animation.edit.EditTestData.assertVec
import com.pafoid.skate.engine.animation.edit.EditTestData.vecTrack
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KeyEditingTest {
    private val track = vecTrack("Hips", 0f to floatArrayOf(0f, 0f, 0f), 1f to floatArrayOf(1f, 0f, 0f), 2f to floatArrayOf(2f, 0f, 0f))

    @Test
    fun `KeyEditing_InsertKeepsKeysSortedAndReplacesSameTime`() {
        val inserted = KeyEditing.insertKey(track, 0.5f, floatArrayOf(9f, 0f, 0f))
        assertEquals(listOf(0f, 0.5f, 1f, 2f), inserted.keys.map { it.time })
        val replaced = KeyEditing.insertKey(inserted, 1.00001f, floatArrayOf(7f, 0f, 0f))
        assertEquals(4, replaced.keys.size)
        assertEquals(7f, replaced.keys[2].value[0])
        assertEquals(3, track.keys.size, "input is not mutated")
    }

    @Test
    fun `KeyEditing_InsertRejectsWrongSizeAndNormalisesRotations`() {
        assertThrows<IllegalArgumentException> { KeyEditing.insertKey(track, 0.5f, floatArrayOf(1f)) }
        val rot = SkTrack("Spine", AnimationPath.ROTATION)
        val key = KeyEditing.insertKey(rot, 0f, floatArrayOf(0f, 0f, 0f, 2f)).keys.single()
        assertEquals(listOf(0f, 0f, 0f, 1f), key.value)
    }

    @Test
    fun `KeyEditing_MoveKeysOverwritesKeysTheyLandOnAndClampsAtZero`() {
        val moved = KeyEditing.moveKeys(track, setOf(0), 1f)
        assertEquals(listOf(1f, 2f), moved.keys.map { it.time })
        assertEquals(0f, moved.keys[0].value[0], "the moved key wins")
        assertEquals(listOf(0f, 2f), KeyEditing.moveKeys(track, setOf(1), -5f).keys.map { it.time })
    }

    @Test
    fun `KeyEditing_ScaleKeysAroundPivot`() {
        assertEquals(listOf(0f, 2f, 4f), KeyEditing.scaleKeys(track, setOf(0, 1, 2), 0f, 2f).keys.map { it.time })
        assertEquals(listOf(0f, 1.5f, 2f), KeyEditing.scaleKeys(track, setOf(1), 2f, 0.5f).keys.map { it.time })
    }

    @Test
    fun `KeyEditing_RemoveAndSetValueAndSample`() {
        assertEquals(listOf(0f, 2f), KeyEditing.removeKeys(track, setOf(1)).keys.map { it.time })
        val edited = KeyEditing.setValue(track, 1, floatArrayOf(3f, 0f, 0f))
        assertVec(floatArrayOf(1.5f, 0f, 0f), KeyEditing.sample(edited, 0.5f))
    }

    @Test
    fun `KeyEditing_EventsStaySortedAndInsideTheClip`() {
        var clip = SkClip(name = "c", duration = 1f)
        clip = KeyEditing.addEvent(clip, SkEvent(0.8f, "land"))
        clip = KeyEditing.addEvent(clip, SkEvent(-1f, "start"))
        assertEquals(listOf("start" to 0f, "land" to 0.8f), clip.events.map { it.name to it.time })
        clip = KeyEditing.moveEvent(clip, 0, 5f)
        assertEquals(listOf("land" to 0.8f, "start" to 1f), clip.events.map { it.name to it.time })
        assertEquals(listOf("start"), KeyEditing.removeEvent(clip, 0).events.map { it.name })
    }
}
