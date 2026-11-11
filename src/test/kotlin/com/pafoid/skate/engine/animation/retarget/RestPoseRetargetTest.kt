package com.pafoid.skate.engine.animation.retarget

import com.pafoid.skate.engine.animation.edit.ClipEvaluator
import com.pafoid.skate.engine.animation.edit.ClipRig
import com.pafoid.skate.engine.animation.edit.SkAnimFormat
import com.pafoid.skate.engine.animation.edit.SkClip
import com.pafoid.skate.engine.animation.edit.SkKey
import com.pafoid.skate.engine.animation.edit.SkTrack
import com.pafoid.skate.engine.animation.graph.AnimParameters
import com.pafoid.skate.engine.animation.graph.ClipMotion
import com.pafoid.skate.engine.animation.graph.Pose
import com.pafoid.skate.engine.animation.ik.IkSolvers
import com.pafoid.skate.engine.animation.procedural.TestHumanoid
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import io.mockk.mockk
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.acos

class RestPoseRetargetTest {
    private val tPose = TestHumanoid.skeleton()

    private val segments = listOf(
        HumanoidBone.LEFT_UPPER_ARM to HumanoidBone.LEFT_LOWER_ARM, HumanoidBone.LEFT_LOWER_ARM to HumanoidBone.LEFT_HAND,
        HumanoidBone.RIGHT_UPPER_ARM to HumanoidBone.RIGHT_LOWER_ARM, HumanoidBone.RIGHT_LOWER_ARM to HumanoidBone.RIGHT_HAND,
        HumanoidBone.LEFT_UPPER_LEG to HumanoidBone.LEFT_LOWER_LEG, HumanoidBone.LEFT_LOWER_LEG to HumanoidBone.LEFT_FOOT,
        HumanoidBone.RIGHT_UPPER_LEG to HumanoidBone.RIGHT_LOWER_LEG, HumanoidBone.RIGHT_LOWER_LEG to HumanoidBone.RIGHT_FOOT,
    )

    /**
     * An A-pose rig built from model-space joint positions, glTF/Blender style: each bone's local Y axis runs along its
     * segment, so bind locals carry rotations (unlike [TestHumanoid], whose bones are all axis-aligned).
     */
    private fun aPoseRig(hipsHeight: Float = 0.9f): Skeleton {
        val s = hipsHeight / 1f
        val armDown = Vector3f(1f, -1f, 0f).normalize() // 45 degrees below horizontal
        val joints = linkedMapOf<String, Pair<String?, Vector3f>>()
        fun j(name: String, parent: String?, p: Vector3f) { joints[name] = parent to p }
        j("Hips", null, Vector3f(0f, 1f, 0f).mul(s))
        j("Spine", "Hips", Vector3f(0f, 1.15f, 0f).mul(s)); j("Chest", "Spine", Vector3f(0f, 1.3f, 0f).mul(s))
        j("Neck", "Chest", Vector3f(0f, 1.5f, 0f).mul(s)); j("Head", "Neck", Vector3f(0f, 1.6f, 0f).mul(s))
        for ((side, sign) in listOf("Left" to 1f, "Right" to -1f)) {
            val shoulder = Vector3f(0.18f * sign, 1.45f, 0f).mul(s)
            val dir = Vector3f(armDown.x * sign, armDown.y, 0f)
            j("${side}Arm", "Chest", shoulder)
            j("${side}ForeArm", "${side}Arm", Vector3f(dir).mul(0.28f * s).add(shoulder))
            j("${side}Hand", "${side}ForeArm", Vector3f(dir).mul(0.54f * s).add(shoulder))
            j("${side}UpLeg", "Hips", Vector3f(0.1f * sign, 0.95f, 0f).mul(s))
            j("${side}Leg", "${side}UpLeg", Vector3f(0.1f * sign, 0.5f, 0.02f).mul(s))
            j("${side}Foot", "${side}Leg", Vector3f(0.1f * sign, 0.07f, 0f).mul(s))
        }
        val names = joints.keys.toList()
        val globals = HashMap<String, Matrix4f>()
        val bones = HashMap<String, Bone>()
        for ((i, name) in names.withIndex()) {
            val (parent, p) = joints.getValue(name)
            val child = names.firstOrNull { joints.getValue(it).first == name }
            val dir = child?.let { Vector3f(joints.getValue(it).second).sub(p) }
            val rot = if (dir != null) Quaternionf().rotationTo(Vector3f(0f, 1f, 0f), dir.normalize()) else Quaternionf(globals[parent]!!.getUnnormalizedRotation(Quaternionf()))
            val g = Matrix4f().translationRotate(p, rot)
            globals[name] = g
            val local = if (parent == null) Matrix4f(g) else Matrix4f(globals.getValue(parent)).invert().mul(g)
            bones[name] = Bone(i, name, local).also { b -> parent?.let { bones.getValue(it).addChild(b) } }
        }
        return Skeleton(bones.getValue("Hips"), names.size)
    }

    private fun rotTrack(bone: String, vararg keys: Pair<Float, Quaternionf>) =
        SkTrack(bone, AnimationPath.ROTATION, keys = keys.map { SkKey(it.first, listOf(it.second.x, it.second.y, it.second.z, it.second.w)) })

    private fun deg(d: Float) = Math.toRadians(d.toDouble()).toFloat()

    /** Arms lower and raise, knees bend, the spine twists. */
    private val sourceClip = SkAnimFormat.toAnimation(SkClip(
        name = "wave", duration = 1f,
        tracks = listOf(
            rotTrack("mixamorig:LeftArm", 0f to Quaternionf(), 0.5f to Quaternionf().rotationZ(deg(-60f)), 1f to Quaternionf().rotationZ(deg(20f))),
            rotTrack("mixamorig:RightForeArm", 0f to Quaternionf(), 1f to Quaternionf().rotationY(deg(70f))),
            rotTrack("mixamorig:LeftLeg", 0f to Quaternionf(), 1f to Quaternionf().rotationX(deg(50f))),
            rotTrack("mixamorig:Spine", 0f to Quaternionf(), 1f to Quaternionf().rotationY(deg(30f))),
            SkTrack("mixamorig:Hips", AnimationPath.TRANSLATION, keys = listOf(SkKey(0f, listOf(0f, 0f, 0f)), SkKey(1f, listOf(0f, 0f, 1f)))),
        ),
    ), "wave.skanim")

    private fun globals(skeleton: Skeleton, clip: Animation, time: Float): Pair<ClipRig, Array<Matrix4f>> {
        val rig = ClipRig(skeleton)
        val locals = ClipEvaluator(SkAnimFormat.fromAnimation(clip), rig).locals(time)
        return rig to Array(rig.boneCount) { IkSolvers.globalOf(rig.parents, locals, it) }
    }

    private fun direction(rig: ClipRig, g: Array<Matrix4f>, from: HumanoidBone, to: HumanoidBone): Vector3f {
        fun idx(slot: HumanoidBone) = rig.names.indexOfFirst { HumanoidBone.fromName(it) == slot }
        return g[idx(to)].getTranslation(Vector3f()).sub(g[idx(from)].getTranslation(Vector3f())).normalize()
    }

    private fun angleDeg(a: Vector3f, b: Vector3f) = Math.toDegrees(acos(a.dot(b).coerceIn(-1f, 1f).toDouble())).toFloat()

    private fun worstSegmentError(target: Skeleton, result: Animation, time: Float): Float {
        val (srig, sg) = globals(tPose, sourceClip, time)
        val (trig, tg) = globals(target, result, time)
        return segments.maxOf { (a, b) -> angleDeg(direction(srig, sg, a, b), direction(trig, tg, a, b)) }
    }

    @Test
    fun `RestPose_TPoseSourceDrivesAnAPoseRigWithOtherBoneAxes`() {
        val target = aPoseRig()
        val result = Retargeter.retarget(sourceClip, tPose, target)
        for (t in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val err = worstSegmentError(target, result, t)
            assertTrue(err < 1f, "segment directions differ by $err deg at t=$t")
        }
        // At t=0 the source is in its T-pose: the A-pose target's arms come up to horizontal.
        val (rig, g) = globals(target, result, 0f)
        assertEquals(0f, direction(rig, g, HumanoidBone.LEFT_UPPER_ARM, HumanoidBone.LEFT_LOWER_ARM).y, 0.01f)
    }

    @Test
    fun `RestPose_NameOnlyRetargetIsWrongOnTheSameRigs`() {
        val target = aPoseRig()
        val naive = Retargeter.retarget(sourceClip, target)
        val (srig, sg) = globals(tPose, sourceClip, 0.5f)
        val (trig, tg) = globals(target, naive, 0.5f)
        val worst = segments.maxOf { (a, b) -> angleDeg(direction(srig, sg, a, b), direction(trig, tg, a, b)) }
        assertTrue(worst > 30f, "precondition: renaming channels alone is off by $worst deg")
    }

    @Test
    fun `RestPose_SameRigReproducesTheClip`() {
        val result = Retargeter.retarget(sourceClip, tPose, TestHumanoid.skeleton())
        for (t in listOf(0f, 0.4f, 1f)) {
            val (_, a) = globals(tPose, sourceClip, t)
            val (_, b) = globals(tPose, result, t)
            for (i in a.indices) {
                val d = a[i].getTranslation(Vector3f()).distance(b[i].getTranslation(Vector3f()))
                assertTrue(d < 1e-4f, "bone $i moved $d at t=$t")
            }
        }
    }

    @Test
    fun `RestPose_RootMotionScalesWithHipsHeight`() {
        val target = aPoseRig(hipsHeight = 0.5f)
        val result = Retargeter.retarget(sourceClip, tPose, target)
        val (rig, g) = globals(target, result, 1f)
        val hips = g[rig.indexOf("Hips")].getTranslation(Vector3f())
        assertEquals(0.5f, hips.z, 1e-3f, "1 m of source travel at half the hips height")
        assertEquals(0.5f, hips.y, 1e-3f)
        val fixed = Retargeter.retarget(sourceClip, tPose, target, translationScale = 1f)
        assertEquals(1f, globals(target, fixed, 1f).let { (r, gg) -> gg[r.indexOf("Hips")].getTranslation(Vector3f()).z }, 1e-3f)
    }

    @Test
    fun `RestPose_TPoseClipRetargetsCleanlyOntoTheSkater`() {
        val james = AssimpLoader(mockk(relaxed = true), mockk(relaxed = true)).loadModel(JAMES).skeleton!!
        val result = Retargeter.retarget(sourceClip, tPose, james)
        assertTrue(result.channels.all { james.getBoneByName(it.targetNodeName) != null })
        for (t in listOf(0f, 0.5f, 1f)) {
            val err = worstSegmentError(james, result, t)
            assertTrue(err < 2f, "skater segment directions differ by $err deg at t=$t")
        }

        // The runtime path (Animation.update via ClipMotion) plays the same pose as the pure evaluator.
        val runtime = AssimpLoader(mockk(relaxed = true), mockk(relaxed = true)).loadModel(JAMES).skeleton!!
        val pose = Pose(runtime.boneCount).also { ClipMotion.of(result, runtime).evaluate(AnimParameters(), 0.5f, it) }
        val (rig, expected) = globals(james, result, 0.5f)
        val played = Array(rig.boneCount) { Matrix4f() }
        fun depth(i: Int): Int { var d = 0; var p = rig.parents[i]; while (p >= 0) { d++; p = rig.parents[p] }; return d }
        for (b in rig.names.indices.sortedBy(::depth)) {
            val local = pose.toMatrix(b, Matrix4f())
            if (rig.parents[b] >= 0) played[rig.parents[b]].mul(local, played[b]) else played[b].set(local)
        }
        for (i in expected.indices) {
            val d = expected[i].getTranslation(Vector3f()).distance(played[i].getTranslation(Vector3f()))
            assertTrue(d < 1e-3f, "${rig.names[i]} differs by $d between runtime and evaluator")
        }
    }

    private companion object {
        const val JAMES = "assets/Models/james.glb"
    }
}
