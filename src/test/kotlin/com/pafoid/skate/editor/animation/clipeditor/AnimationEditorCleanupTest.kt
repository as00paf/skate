package com.pafoid.skate.editor.animation.clipeditor

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.animation.ClipEditCommand
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.animation.edit.ClipEvaluator
import com.pafoid.skate.engine.animation.edit.ClipRig
import com.pafoid.skate.engine.animation.edit.CurveTangent
import com.pafoid.skate.engine.animation.edit.HandleSide
import com.pafoid.skate.engine.animation.edit.KeyEditing
import com.pafoid.skate.engine.animation.edit.KeyRef
import com.pafoid.skate.engine.animation.edit.ReductionTolerance
import com.pafoid.skate.engine.animation.edit.SkAnimFormat
import com.pafoid.skate.engine.animation.edit.SkClip
import com.pafoid.skate.engine.animation.edit.SkKey
import com.pafoid.skate.engine.animation.edit.SkTrack
import com.pafoid.skate.engine.animation.edit.TangentMode
import com.pafoid.skate.engine.animation.ik.IkSolvers
import com.pafoid.skate.engine.animation.procedural.TestHumanoid
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath.ROTATION
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath.TRANSLATION
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import io.mockk.mockk
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.math.sin

/**
 * ANI-15 acceptance: the curve editor and the cleanup tools work on a mocap-like clip (dense, jittery keys on a
 * humanoid rig) through the editor's controller, and every tool is one undo step.
 */
class AnimationEditorCleanupTest {
    @TempDir lateinit var dir: File

    private val undo = UndoRedoManager(mockk(relaxed = true), mockk(relaxed = true))
    private val executed = ArrayList<Command>()
    private val skeleton = TestHumanoid.skeleton()
    private val rig = ClipRig(skeleton)

    private fun rot(x: Float, y: Float, z: Float) = Quaternionf().rotationXYZ(Math.toRadians(x.toDouble()).toFloat(), Math.toRadians(y.toDouble()).toFloat(), Math.toRadians(z.toDouble()).toFloat())
    private fun row(q: Quaternionf) = listOf(q.x, q.y, q.z, q.w)
    /** Deterministic jitter, like pose estimation noise. */
    private fun jitter(i: Int, scale: Float) = sin(i * 12.9898f) * scale

    /** 2 s at 30 fps: hips glide forward, the left leg swings, every key has a little noise. */
    private val mocap: SkClip = run {
        val frames = (0..60).map { it / 30f }
        SkClip(
            name = "mocap", fps = 30f, duration = 2f,
            tracks = listOf(
                SkTrack("mixamorig:Hips", TRANSLATION, InterpolationType.LINEAR, frames.mapIndexed { i, t -> SkKey(t, listOf(jitter(i, 0.004f), -0.05f * t, 0.3f * t)) }),
                SkTrack("mixamorig:Hips", ROTATION, InterpolationType.LINEAR, frames.mapIndexed { i, t -> SkKey(t, row(rot(0f, 10f * t + jitter(i, 0.8f), 0f))) }),
                SkTrack("mixamorig:LeftUpLeg", ROTATION, InterpolationType.LINEAR, frames.mapIndexed { i, t -> SkKey(t, row(rot(-15f + jitter(i, 1f), 0f, 0f))) }),
                SkTrack("mixamorig:LeftLeg", ROTATION, InterpolationType.LINEAR, frames.map { t -> SkKey(t, row(rot(20f, 0f, 0f))) }),
            ),
        )
    }

    private fun opened(clip: SkClip = mocap, skel: Skeleton? = skeleton): AnimationEditorController {
        val c = AnimationEditorController(execute = { executed += it; undo.executeCommand(it) }, rigFor = { skel })
        val file = File(dir, "${clip.name}.skanim").apply { writeText(SkAnimFormat.encode(clip)) }
        assertTrue(c.open(file), c.error)
        return c
    }

    private fun foot(c: SkClip, t: Float) =
        IkSolvers.globalOf(rig.parents, ClipEvaluator(c, rig).locals(t), rig.indexOf("mixamorig:LeftFoot")).getTranslation(Vector3f())

    private fun lastAction() = (executed.last() as ClipEditCommand).action

    /** Undoes the last command and checks the clip is back to [before]. */
    private fun assertUndoes(c: AnimationEditorController, before: SkClip) {
        undo.undo()
        assertEquals(before, c.clip, "undo restores the clip")
        undo.redo()
    }

    @Test
    fun CurveTracks_FollowTheSelection() {
        val c = opened()
        assertEquals(4, c.curveTracks().size, "nothing selected: every track")
        c.selectBone("mixamorig:Hips")
        assertEquals(listOf("mixamorig:Hips" to TRANSLATION, "mixamorig:Hips" to ROTATION), c.curveTracks())
        c.curveTrack = "mixamorig:Hips" to ROTATION
        assertEquals(ROTATION, c.shownCurveTrack()!!.path)
        c.curveTrack = "mixamorig:LeftLeg" to ROTATION
        assertEquals(TRANSLATION, c.shownCurveTrack()!!.path, "a track outside the offer falls back to the first")
    }

    @Test
    fun CurveDrag_ChangesValueAndTimeInOneUndoStep() {
        val c = opened()
        val before = c.clip!!
        c.selectBone("mixamorig:Hips")
        c.curveTrack = "mixamorig:Hips" to TRANSLATION
        c.selectKey(KeyRef("mixamorig:Hips", TRANSLATION, 1f))
        c.beginCurveDrag(component = 2)
        c.dragCurveKeys(0.02f, 0.1f)
        c.dragCurveKeys(0.1f, 0.5f)
        c.endDrag()
        val key = c.clip!!.track("mixamorig:Hips", TRANSLATION)!!.keys.first { kotlin.math.abs(it.time - 1.1f) < 1e-4f }
        assertEquals(0.3f + 0.5f, key.value[2], 1e-4f, "Z moved up by 0.5 m")
        assertEquals(setOf(KeyRef("mixamorig:Hips", TRANSLATION, key.time)), c.selection)
        assertEquals("curve_keys", lastAction())
        assertEquals(1, executed.size, "one command for the gesture")
        assertUndoes(c, before)
    }

    @Test
    fun TangentsAndHandles_AreUndoable() {
        val c = opened()
        val before = c.clip!!
        c.selectKey(KeyRef("mixamorig:Hips", TRANSLATION, 1f))
        c.setTangent(CurveTangent.FLAT)
        val flat = c.clip!!.track("mixamorig:Hips", TRANSLATION)!!
        assertEquals(InterpolationType.CUBIC_SPLINE, flat.interpolation)
        assertEquals(TangentMode.FLAT, flat.keys[30].tangentMode)
        assertEquals("set_tangent", lastAction())

        c.curveTrack = "mixamorig:Hips" to TRANSLATION
        c.beginHandleDrag(30, component = 2, side = HandleSide.OUT)
        c.dragHandle(0.9f)
        c.endDrag()
        val free = c.clip!!.track("mixamorig:Hips", TRANSLATION)!!.keys[30]
        assertEquals(TangentMode.FREE, free.tangentMode)
        assertEquals(0.9f, free.outTangent!![2], 1e-6f)
        assertEquals("set_tangent", lastAction())

        undo.undo(); undo.undo()
        assertEquals(before, c.clip)
    }

    @Test
    fun ResampleReduceSmooth_CleanTheMocapClip() {
        val c = opened()
        c.resample(15f)
        assertEquals(15f, c.clip!!.fps)
        assertEquals(31, c.clip!!.track("mixamorig:Hips", TRANSLATION)!!.keys.size)
        undo.undo()

        val before = c.clip!!
        c.reduceKeys(ReductionTolerance(translationMetres = 0.01f, rotationDeg = 2f))
        val reduced = c.clip!!
        assertTrue(reduced.track("mixamorig:LeftLeg", ROTATION)!!.keys.size <= 2, "a still bone keeps at most its end keys")
        assertTrue(reduced.tracks.sumOf { it.keys.size } < before.tracks.sumOf { it.keys.size } / 3, "most keys go")
        assertUndoes(c, before)
        undo.undo()

        // Smoothing over a range, limited to the selected bone: keys outside the range and other bones stay.
        c.selectBone("mixamorig:LeftUpLeg")
        c.setRange(0.5f, 1.5f)
        c.smooth(com.pafoid.skate.editor.animation.clipeditor.SmoothFilter.GAUSSIAN, 2f)
        val smooth = c.clip!!
        val leg = { clip: SkClip -> clip.track("mixamorig:LeftUpLeg", ROTATION)!!.keys }
        assertEquals(leg(before)[5], leg(smooth)[5], "before the range")
        assertTrue(leg(before)[30] != leg(smooth)[30], "inside the range")
        assertEquals(before.track("mixamorig:Hips", ROTATION), smooth.track("mixamorig:Hips", ROTATION), "other bones untouched")
        assertUndoes(c, before)
    }

    @Test
    fun FootLock_PlantsTheFootOverTheRange() {
        val c = opened()
        assertNotNull(c.legChain(left = true))
        c.setRange(0.5f, 1.5f)
        c.footLock(left = true, blendFrames = 3f)
        val planted = foot(c.clip!!, 0.5f)
        for (t in listOf(0.7f, 1f, 1.5f)) assertTrue(foot(c.clip!!, t).distance(planted) < 0.005f, "foot slid at $t")
        assertTrue(foot(mocap, 1.5f).distance(foot(mocap, 0.5f)) > 0.2f, "precondition: it slid before")
        assertEquals("foot_lock", lastAction())
        assertUndoes(c, mocap)

        val noLegs = opened(skel = Skeleton(Bone(0, "Root", Matrix4f()), 1))
        assertNull(noLegs.legChain(left = true))
        noLegs.footLock(left = true, blendFrames = 0f)
        assertEquals(mocap, noLegs.clip, "nothing to lock without legs")
    }

    @Test
    fun MirrorTrimAndLoopSeam_AreOneUndoStepEach() {
        val c = opened()
        c.mirror()
        assertNull(c.clip!!.track("mixamorig:LeftUpLeg", ROTATION))
        assertNotNull(c.clip!!.track("mixamorig:RightUpLeg", ROTATION))
        assertEquals(0.6f, c.clip!!.track("mixamorig:Hips", TRANSLATION)!!.keys.last().value[2], 1e-3f, "still walks forward")
        assertUndoes(c, mocap)
        undo.undo()

        c.seek(1f)
        c.setRange(0.5f, 1.5f)
        c.trimToRange()
        assertEquals(1f, c.clip!!.duration, 1e-5f)
        assertEquals(0.5f, c.time, 1e-5f, "the playhead stays on the same pose")
        assertNull(c.range)
        assertEquals("trim_range", lastAction())
        assertUndoes(c, mocap)
        undo.undo()

        c.loopSeam(blendFrames = 10f)
        val looped = c.clip!!
        val hips = looped.track("mixamorig:Hips", TRANSLATION)!!
        assertEquals(KeyEditing.sample(hips, 0f)[2], KeyEditing.sample(hips, 2f)[2], 1e-4f, "the end meets the start")
        assertEquals(mocap.track("mixamorig:Hips", TRANSLATION)!!.keys[10], hips.keys[10], "keys before the seam are kept")
        assertUndoes(c, mocap)
    }

    @Test
    fun Range_SnapsAndFollowsThePlayheadAndSelection() {
        val c = opened()
        assertEquals(0f..2f, c.toolRange())
        c.seek(0.5f); c.setRangeStartAtPlayhead()
        c.seek(1.2f); c.setRangeEndAtPlayhead()
        assertEquals(0.5f..1.2f, c.range)
        c.selectKeys(setOf(KeyRef("mixamorig:Hips", TRANSLATION, 0.2f), KeyRef("mixamorig:Hips", TRANSLATION, 0.4f)))
        c.setRangeFromSelection()
        assertEquals(0.2f, c.range!!.start, 1e-5f); assertEquals(0.4f, c.range!!.endInclusive, 1e-5f)
        c.setRange(0f, 2f)
        assertNull(c.range, "the whole clip is no range")
        c.setRange(0.3f, 0.6f); c.clearRange()
        assertNull(c.range)
    }
}
