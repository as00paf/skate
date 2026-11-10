package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath.ROTATION
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath.SCALE
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath.TRANSLATION
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClipEditingTest {
    private fun key(t: Float, x: Float) = SkKey(t, listOf(x, 0f, 0f))
    private val clip = SkClip(
        name = "walk", fps = 10f, duration = 2f,
        tracks = listOf(
            SkTrack("Hips", TRANSLATION, keys = listOf(key(0f, 0f), key(0.5f, 1f), key(1f, 2f))),
            SkTrack("Spine", TRANSLATION, keys = listOf(key(0f, 5f), key(1.5f, 6f))),
        ),
        events = listOf(SkEvent(1f, "step")),
    )
    private fun times(c: SkClip, bone: String) = c.track(bone, TRANSLATION)!!.keys.map { it.time }

    @Test
    fun MoveKeys_ShiftsAcrossTracksAndStaysInsideTheClip() {
        val sel = setOf(KeyRef("Hips", TRANSLATION, 0.5f), KeyRef("Spine", TRANSLATION, 1.5f))
        val (moved, newSel) = ClipEditing.moveKeys(clip, sel, 0.2f)
        assertEquals(listOf(0f, 0.7f, 1f), times(moved, "Hips"))
        assertEquals(listOf(0f, 1.7f), times(moved, "Spine"))
        assertEquals(setOf(KeyRef("Hips", TRANSLATION, 0.7f), KeyRef("Spine", TRANSLATION, 1.7f)), newSel)

        val (clamped, _) = ClipEditing.moveKeys(clip, sel, 5f)
        assertEquals(listOf(0f, 2f), times(clamped, "Spine"), "the group stops at the clip's end")
        assertEquals(listOf(0f, 1f), times(clamped, "Hips"), "and keeps its spacing (0.5 -> 1.0, replacing the key there)")
    }

    @Test
    fun MoveKeys_OntoAnotherKey_ReplacesIt() {
        val (moved, _) = ClipEditing.moveKeys(clip, setOf(KeyRef("Hips", TRANSLATION, 0.5f)), 0.5f)
        assertEquals(listOf(0f, 1f), times(moved, "Hips"))
        assertEquals(1f, moved.track("Hips", TRANSLATION)!!.keys[1].value[0], "the moved key wins")
    }

    @Test
    fun ScaleKeys_AroundAPivot() {
        val sel = clip.track("Hips", TRANSLATION)!!.keys.map { KeyRef("Hips", TRANSLATION, it.time) }.toSet()
        val (scaled, newSel) = ClipEditing.scaleKeys(clip, sel, 0f, 1.5f)
        assertEquals(listOf(0f, 0.75f, 1.5f), times(scaled, "Hips"))
        assertEquals(3, newSel.size)
        val (squeezed, _) = ClipEditing.scaleKeys(clip, sel, 0f, 0.00001f)
        assertEquals(1, squeezed.track("Hips", TRANSLATION)!!.keys.size, "keys landing on the same time merge")
    }

    @Test
    fun RetimeClip_StretchesKeysEventsAndTangents() {
        val cubic = clip.copy(tracks = listOf(SkTrack("Hips", TRANSLATION, keys = listOf(
            SkKey(0f, listOf(0f, 0f, 0f), TangentMode.FREE, listOf(2f, 0f, 0f), listOf(2f, 0f, 0f)),
            key(1f, 2f),
        ))))
        val slow = ClipEditing.retimeClip(cubic, 4f)
        assertEquals(4f, slow.duration)
        assertEquals(listOf(0f, 2f), times(slow, "Hips"))
        assertEquals(2f, slow.events.single().time)
        assertEquals(1f, slow.track("Hips", TRANSLATION)!!.keys[0].outTangent!![0], "half speed halves the slopes")
    }

    @Test
    fun SetDuration_TrimsKeysAndEventsPastTheEnd() {
        val trimmed = ClipEditing.setDuration(clip, 0.8f)
        assertEquals(listOf(0f, 0.5f), times(trimmed, "Hips"))
        assertEquals(listOf(0f), times(trimmed, "Spine"))
        assertTrue(trimmed.events.isEmpty())
        assertEquals(3, ClipEditing.setDuration(clip, 3f).track("Hips", TRANSLATION)!!.keys.size, "extending keeps every key")
    }

    @Test
    fun KeyBones_KeysTheSampledPose_AndTheRestPoseForNewBones() {
        val (keyed, refs) = ClipEditing.keyBones(clip, listOf("Hips", "Arm"), 0.25f)
        val hips = keyed.track("Hips", TRANSLATION)!!
        assertEquals(4, hips.keys.size)
        assertEquals(0.5f, hips.keys[1].value[0], 1e-5f, "the key holds the value the clip played there")
        assertEquals(listOf(0f, 0f, 0f, 1f), keyed.track("Arm", ROTATION)!!.keys.single().value)
        assertEquals(setOf(KeyRef("Hips", TRANSLATION, 0.25f), KeyRef("Arm", ROTATION, 0.25f)), refs)
    }

    @Test
    fun CopyPaste_KeepsSpacingAndCreatesMissingTracks() {
        val sel = setOf(KeyRef("Hips", TRANSLATION, 0.5f), KeyRef("Hips", TRANSLATION, 1f))
        val copied = ClipEditing.copy(clip, sel)
        val (pasted, refs) = ClipEditing.paste(clip, copied, 1.2f)
        assertEquals(listOf(0f, 0.5f, 1f, 1.2f, 1.7f), times(pasted, "Hips").map { Math.round(it * 10) / 10f })
        assertEquals(2, refs.size)
        val (late, lateRefs) = ClipEditing.paste(clip, copied, 1.8f)
        assertEquals(1, lateRefs.size, "keys past the end are dropped")
        assertEquals(4, late.track("Hips", TRANSLATION)!!.keys.size)
        val onScale = ClipEditing.paste(clip, CopiedKeys(listOf(KeyRef("Head", SCALE, 0f) to SkKey(0f, listOf(2f, 2f, 2f)))), 0.3f).first
        assertEquals(0.3f, onScale.track("Head", SCALE)!!.keys.single().time)
    }

    @Test
    fun DeleteKeys_AndBoxSelect() {
        val box = ClipEditing.keysIn(clip, listOf("Hips" to TRANSLATION), 1.1f, 0.4f)
        assertEquals(setOf(KeyRef("Hips", TRANSLATION, 0.5f), KeyRef("Hips", TRANSLATION, 1f)), box)
        val deleted = ClipEditing.deleteKeys(clip, box)
        assertEquals(listOf(0f), times(deleted, "Hips"))
        assertEquals(2, deleted.track("Spine", TRANSLATION)!!.keys.size)
    }

    @Test
    fun SetKeyValue_NormalizesRotations() {
        val rot = clip.withTrack(SkTrack("Hips", ROTATION, keys = listOf(SkKey(0f, listOf(0f, 0f, 0f, 1f)))))
        val set = ClipEditing.setKeyValue(rot, KeyRef("Hips", ROTATION, 0f), floatArrayOf(0f, 0f, 0f, 2f))
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f), set.track("Hips", ROTATION)!!.keys[0].value.toFloatArray())
        assertEquals(0.3f, ClipEditing.snap(0.27f, 10f), 1e-6f)
    }
}
