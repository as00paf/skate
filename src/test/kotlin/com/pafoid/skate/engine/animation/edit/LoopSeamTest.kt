package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.animation.edit.EditTestData.assertSameRotation
import com.pafoid.skate.engine.animation.edit.EditTestData.assertVec
import com.pafoid.skate.engine.animation.edit.EditTestData.frames
import com.pafoid.skate.engine.animation.edit.EditTestData.rotTrack
import com.pafoid.skate.engine.animation.edit.EditTestData.scalarTrack
import com.pafoid.skate.engine.animation.edit.EditTestData.yaw
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class LoopSeamTest {
    private val times = frames(30f, 1f)
    private val clip = SkClip(
        name = "walk", fps = 30f, duration = 1f,
        tracks = listOf(
            scalarTrack(times, { it * 0.3f }),
            rotTrack("Spine", *times.map { it to yaw(it * 40f) }.toTypedArray()),
        ),
    )

    @Test
    fun `LoopSeam_EndMatchesStartAndEarlyKeysAreUntouched`() {
        val looped = LoopSeam.apply(clip, 0.25f)
        val pos = looped.tracks[0]
        assertVec(KeyEditing.sample(pos, 0f), KeyEditing.sample(pos, 1f), 1e-5f, "translation seam")
        val rot = looped.tracks[1]
        assertSameRotation(KeyEditing.sample(rot, 0f), KeyEditing.sample(rot, 1f), 0.05f, "rotation seam")

        for (i in clip.tracks.indices) {
            val before = clip.tracks[i].keys.filter { it.time < 0.749f }
            val after = looped.tracks[i].keys.filter { it.time < 0.749f }
            assertEquals(before, after, "keys before the blend window are untouched (track $i)")
            assertVec(KeyEditing.sample(clip.tracks[i], 0.75f), KeyEditing.sample(looped.tracks[i], 0.75f), 1e-5f, "window start anchored (track $i)")
        }
    }

    @Test
    fun `LoopSeam_BlendIsGradualAndCanSkipPaths`() {
        val looped = LoopSeam.apply(clip, 0.5f).tracks[0]
        val values = looped.keys.filter { it.time >= 0.5f }.map { it.value[0] }
        val steps = values.zipWithNext { a, b -> abs(b - a) }
        assertTrue(steps.max() < 0.05f, "no pop inside the window: $steps")

        val onlyRot = LoopSeam.apply(clip, 0.25f, setOf(AnimationPath.ROTATION))
        assertEquals(clip.tracks[0], onlyRot.tracks[0])
    }
}
