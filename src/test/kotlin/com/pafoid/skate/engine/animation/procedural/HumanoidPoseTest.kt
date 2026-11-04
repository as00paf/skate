package com.pafoid.skate.engine.animation.procedural

import com.pafoid.skate.engine.animation.graph.AnimParameters
import com.pafoid.skate.engine.animation.graph.Pose
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A Mixamo-style T-pose rig in meters, facing +Z with its left side at +X. Leg length 0.9 m. */
object TestHumanoid {
    fun skeleton(): Skeleton {
        var next = 0
        fun bone(name: String, x: Float, y: Float, z: Float, vararg children: Bone) =
            Bone(next++, "mixamorig:$name", Matrix4f().translation(x, y, z)).also { b -> children.forEach { b.addChild(it) } }

        fun arm(side: String, s: Float) = bone("${side}Shoulder", 0.05f * s, 0.1f, 0f,
            bone("${side}Arm", 0.1f * s, 0f, 0f, bone("${side}ForeArm", 0.25f * s, 0f, 0f, bone("${side}Hand", 0.25f * s, 0f, 0f))))

        fun leg(side: String, s: Float) = bone("${side}UpLeg", 0.1f * s, -0.05f, 0f,
            bone("${side}Leg", 0f, -0.45f, 0f, bone("${side}Foot", 0f, -0.45f, 0f, bone("${side}ToeBase", 0f, -0.05f, 0.12f))))

        val root = bone("Hips", 0f, 1f, 0f,
            bone("Spine", 0f, 0.1f, 0f, bone("Spine1", 0f, 0.12f, 0f,
                bone("Spine2", 0f, 0.12f, 0f, bone("Neck", 0f, 0.15f, 0f, bone("Head", 0f, 0.1f, 0f)), arm("Left", 1f), arm("Right", -1f)))),
            leg("Left", 1f), leg("Right", -1f))
        return Skeleton(root, next)
    }

    /** Model-space positions of every bone for [pose]. */
    fun positions(skeleton: Skeleton, pose: Pose): Map<String, Vector3f> {
        val out = mutableMapOf<String, Vector3f>()
        fun walk(bone: Bone, parent: Matrix4f) {
            val g = Matrix4f(parent).mul(pose.toMatrix(bone.index, Matrix4f()))
            out[bone.name.removePrefix("mixamorig:")] = g.getTranslation(Vector3f())
            bone.children.forEach { walk(it, g) }
        }
        walk(skeleton.rootBone, Matrix4f())
        return out
    }
}

class HumanoidPoseTest {
    private val skeleton = TestHumanoid.skeleton()
    private val builder = HumanoidPoseBuilder(skeleton)
    private val legLength = 0.9f

    private fun posed(def: HumanoidPoseDef, leftFootForward: Boolean = true) =
        TestHumanoid.positions(skeleton, Pose(skeleton.boneCount).also { builder.build(def, leftFootForward, it) })

    @Test
    fun `Stance_LowersHipsAndPlacesFeetOnTheBoardAxis`() {
        val p = posed(HumanoidPoseDef(hipDrop = 0.1f, frontFoot = FootTarget(along = 0.3f), backFoot = FootTarget(along = -0.3f)))

        assertEquals(1f - 0.1f * legLength, p.getValue("Hips").y, 1e-3f)
        // Regular stance: left foot toward the nose (+Z), right foot toward the tail, both on the deck line (x = 0).
        assertEquals(0.3f * legLength, p.getValue("LeftFoot").z, 1e-3f)
        assertEquals(-0.3f * legLength, p.getValue("RightFoot").z, 1e-3f)
        assertEquals(0f, p.getValue("LeftFoot").x, 1e-3f)
        assertEquals(0.05f, p.getValue("LeftFoot").y, 1e-3f)
    }

    @Test
    fun `Stance_TurnsHipsSidewaysAndBendsKneesTowardTheChest`() {
        val p = posed(HumanoidPoseDef(facingDeg = 90f, hipDrop = 0.15f))

        val hipLine = Vector3f(p.getValue("RightUpLeg")).sub(p.getValue("LeftUpLeg")).normalize()
        assertTrue(hipLine.z < -0.99f, "left hip should lead along +Z, hip line $hipLine")
        // Regular riders face their right (-X): knees bend that way.
        assertTrue(p.getValue("LeftLeg").x < -0.05f, "knee ${p.getValue("LeftLeg")}")
        // Toes point across the deck, toward the chest side.
        val toes = Vector3f(p.getValue("LeftToeBase")).sub(p.getValue("LeftFoot"))
        assertTrue(toes.x < -0.1f && kotlin.math.abs(toes.z) < 0.02f, "toes $toes")
    }

    @Test
    fun `Stance_ArmsComeDownFromTPose`() {
        val p = posed(HumanoidPoseDef(armDownDeg = 55f))

        assertTrue(p.getValue("LeftForeArm").y < p.getValue("LeftArm").y - 0.15f)
        assertTrue(p.getValue("RightForeArm").y < p.getValue("RightArm").y - 0.15f)
        // The front (left) arm reaches toward the nose, the back arm toward the tail.
        assertTrue(p.getValue("LeftHand").z > p.getValue("RightHand").z + 0.3f)
    }

    @Test
    fun `Stance_RightFootForwardMirrors`() {
        val p = posed(HumanoidPoseDef(), leftFootForward = false)

        assertTrue(p.getValue("RightFoot").z > 0.2f)
        assertTrue(p.getValue("LeftFoot").z < -0.2f)
        assertTrue(p.getValue("RightLeg").x > 0.05f, "goofy riders face +X")
    }

    @Test
    fun `Builder_RejectsNonHumanoidRig`() {
        assertThrows(IllegalArgumentException::class.java) { HumanoidPoseBuilder(Skeleton(Bone(0, "root"), 1)) }
    }

    @Test
    fun `ProceduralClip_EasesBetweenKeysAndHoldsEnds`() {
        val clip = ProceduralClipDef(listOf(
            PoseKeyDef(0f, HumanoidPoseDef(hipDrop = 0f)),
            PoseKeyDef(1f, HumanoidPoseDef(hipDrop = 0.2f)),
        )).build("crouch", builder)
        val params = AnimParameters()
        fun hipsY(t: Float) = TestHumanoid.positions(skeleton, Pose(skeleton.boneCount).also { clip.evaluate(params, t, it) }).getValue("Hips").y

        assertEquals(1f, clip.duration(params))
        assertEquals(1f, hipsY(0f), 1e-3f)
        assertEquals(1f - 0.2f * legLength, hipsY(1f), 1e-3f)
        assertEquals(1f - 0.1f * legLength, hipsY(0.5f), 1e-3f)
        assertNotEquals(hipsY(0.25f), hipsY(0.75f))
    }

    @Test
    fun `ProceduralClip_RejectsUnorderedKeys`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProceduralClipDef(listOf(PoseKeyDef(1f, HumanoidPoseDef()), PoseKeyDef(0.5f, HumanoidPoseDef())))
        }
    }
}
