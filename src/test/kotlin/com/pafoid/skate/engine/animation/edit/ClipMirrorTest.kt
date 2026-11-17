package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.animation.edit.EditTestData.assertSameRotation
import com.pafoid.skate.engine.animation.edit.EditTestData.frames
import com.pafoid.skate.engine.animation.edit.EditTestData.rotTrack
import com.pafoid.skate.engine.animation.edit.EditTestData.yaw
import com.pafoid.skate.engine.animation.ik.IkSolvers
import com.pafoid.skate.engine.animation.procedural.TestHumanoid
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ANI-15: mirroring a clip left ↔ right. */
class ClipMirrorTest {
    private val skeleton = TestHumanoid.skeleton()
    private val rig = ClipRig(skeleton)
    private val times = frames(10f, 1f)

    private fun roll(deg: Float) = Quaternionf().rotationZ(Math.toRadians(deg.toDouble()).toFloat())
    private fun bend(deg: Float) = Quaternionf().rotationXYZ(Math.toRadians(deg.toDouble()).toFloat(), 0.3f, 0.1f)

    /** Walks forward and to the left, turning left, with only the left leg moving. */
    private val clip = SkClip(
        name = "kick", fps = 10f, duration = 1f,
        tracks = listOf(
            SkTrack("mixamorig:Hips", AnimationPath.TRANSLATION, InterpolationType.LINEAR, times.map { SkKey(it, listOf(0.3f * it, 0f, 0.5f * it)) }),
            rotTrack("mixamorig:Hips", *times.map { it to yaw(25f * it) }.toTypedArray()),
            rotTrack("mixamorig:LeftUpLeg", *times.map { it to bend(-40f * it) }.toTypedArray()),
            rotTrack("mixamorig:LeftLeg", *times.map { it to roll(15f * it) }.toTypedArray()),
            rotTrack("Prop", 0f to yaw(10f)),
        ),
    )

    private fun position(c: SkClip, bone: String, t: Float, r: ClipRig = rig): Vector3f =
        IkSolvers.globalOf(r.parents, ClipEvaluator(c, r).locals(t), r.indexOf(bone)).getTranslation(Vector3f())

    private fun reflect(v: Vector3f) = Vector3f(-v.x, v.y, v.z)

    @Test
    fun `Mirror_EveryJointIsTheReflectionOfItsTwin`() {
        val mirrored = ClipMirror.apply(clip, skeleton)
        for (t in listOf(0f, 0.35f, 1f)) for (name in rig.names) {
            val twin = name.replace("Left", "#").replace("Right", "Left").replace("#", "Right")
            val expected = reflect(position(clip, twin, t))
            val actual = position(mirrored, name, t)
            assertTrue(actual.distance(expected) < 1e-4f, "$name at t=$t: expected $expected, got $actual")
        }
        SkAnimFormat.validate(mirrored)
    }

    @Test
    fun `Mirror_SwapsTracksAndFlipsTheTurn`() {
        val mirrored = ClipMirror.apply(clip, skeleton)
        assertNull(mirrored.track("mixamorig:LeftUpLeg", AnimationPath.ROTATION), "the left leg is now still")
        assertNotNull(mirrored.track("mixamorig:RightUpLeg", AnimationPath.ROTATION))
        val hips = mirrored.track("mixamorig:Hips", AnimationPath.ROTATION)!!
        assertSameRotation(QuatMath.toRow(yaw(-25f)), hips.keys.last().value.toFloatArray())
        assertEquals(-0.3f, mirrored.track("mixamorig:Hips", AnimationPath.TRANSLATION)!!.keys.last().value[0], 1e-5f)
        assertEquals(clip.track("Prop", AnimationPath.ROTATION), mirrored.track("Prop", AnimationPath.ROTATION), "bones outside the rig are kept")
        assertEquals(times.size, hips.keys.size, "key times follow the source track")
    }

    @Test
    fun `Mirror_TwiceGivesBackTheClip`() {
        val back = ClipMirror.apply(ClipMirror.apply(clip, skeleton), skeleton)
        for (t in listOf(0.2f, 0.7f)) for (name in rig.names) {
            assertTrue(position(back, name, t).distance(position(clip, name, t)) < 1e-4f, "$name at t=$t")
        }
    }

    @Test
    fun `Mirror_HandlesSidesWhoseBindAxesAreNotMirrored`() {
        // Like many exported rigs, the right thigh's bind frame is rolled 180° instead of reflected.
        val hips = Bone(0, "Hips", Matrix4f().translation(0f, 1f, 0f))
        val left = Bone(1, "LeftUpLeg", Matrix4f().translation(0.1f, 0f, 0f)).also { hips.addChild(it) }
        val right = Bone(2, "RightUpLeg", Matrix4f().translation(-0.1f, 0f, 0f).rotateY(Math.PI.toFloat())).also { hips.addChild(it) }
        left.addChild(Bone(3, "LeftLeg", Matrix4f().translation(0f, -0.45f, 0f)))
        right.addChild(Bone(4, "RightLeg", Matrix4f().translation(0f, -0.45f, 0f)))
        val odd = Skeleton(hips, 5)
        val oddRig = ClipRig(odd)
        val kick = SkClip(name = "k", fps = 10f, duration = 1f, tracks = listOf(rotTrack("LeftUpLeg", 0f to Quaternionf(), 1f to bend(-50f))))

        val mirrored = ClipMirror.apply(kick, odd)
        val expected = reflect(position(kick, "LeftLeg", 1f, oddRig))
        val actual = position(mirrored, "RightLeg", 1f, oddRig)
        assertTrue(actual.distance(expected) < 1e-4f, "expected $expected, got $actual")
        assertTrue(position(mirrored, "RightLeg", 0f, oddRig).distance(position(kick, "RightLeg", 0f, oddRig)) < 1e-4f, "rest pose unchanged")
    }

    @Test
    fun `Mirror_KeepsTheLocalMotionOfASideBoneWithoutATwin`() {
        // Only the left hand has a fingertip end bone (as Mixamo exports do).
        val hips = Bone(0, "Hips", Matrix4f().translation(0f, 1f, 0f))
        val left = Bone(1, "LeftHand", Matrix4f().translation(0.5f, 0f, 0f)).also { hips.addChild(it) }
        hips.addChild(Bone(2, "RightHand", Matrix4f().translation(-0.5f, 0f, 0f)))
        left.addChild(Bone(3, "LeftHandTip", Matrix4f().translation(0.1f, 0f, 0f)))
        val oneSided = Skeleton(hips, 4)
        val tip = rotTrack("LeftHandTip", 0f to Quaternionf(), 1f to yaw(30f))
        val wave = SkClip(name = "w", fps = 10f, duration = 1f, tracks = listOf(rotTrack("LeftHand", 0f to Quaternionf(), 1f to roll(20f)), tip))

        val mirrored = ClipMirror.apply(wave, oneSided)
        assertEquals(tip, mirrored.track("LeftHandTip", AnimationPath.ROTATION), "kept as is")
        assertNotNull(mirrored.track("RightHand", AnimationPath.ROTATION))
        val oneSidedRig = ClipRig(oneSided)
        assertTrue(position(mirrored, "LeftHandTip", 1f, oneSidedRig).distance(position(mirrored, "LeftHand", 1f, oneSidedRig)) < 0.11f, "still on its hand")
    }
}
