package com.pafoid.skate.engine.animation.retarget

import com.pafoid.skate.engine.animation.procedural.TestHumanoid
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ANI-19: remapping skinned clothing joints onto the body skeleton by bone name, and refusing what cannot fit. */
class JointRemapTest {
    private val body = listOf("Hips", "Spine", "LeftArm", "RightArm")
    /** The same rig exported with its bones in another order. */
    private val shirt = listOf("Spine", "RightArm", "Hips", "LeftArm")

    private fun remapped(r: JointRemapResult): List<IntArray> {
        assertTrue(r is JointRemapResult.Remapped, "expected a remap, got $r")
        return (r as JointRemapResult.Remapped).joints
    }

    @Test
    fun Table_MatchesByName() {
        assertArrayEquals(intArrayOf(1, 3, 0, 2), JointRemap.table(shirt, body))
        assertArrayEquals(intArrayOf(-1, 0), JointRemap.table(listOf("Tail", "Hips"), body))
    }

    @Test
    fun Remap_RewritesWeightedJointsToBodyIndices() {
        // Two vertices, 4 slots each: shirt indices 0 (Spine), 1 (RightArm), 2 (Hips), 3 (LeftArm).
        val joints = intArrayOf(0, 1, 0, 0, /**/ 3, 2, 0, 0)
        val weights = floatArrayOf(0.6f, 0.4f, 0f, 0f, /**/ 0.5f, 0.5f, 0f, 0f)
        val out = remapped(JointRemap.remap(joints, weights, shirt, body)).single()
        assertArrayEquals(intArrayOf(1, 3, 0, 0, /**/ 2, 0, 0, 0), out, "unweighted slots stay as they are")
        assertArrayEquals(intArrayOf(0, 1, 0, 0, 3, 2, 0, 0), joints, "the input is not modified")
    }

    @Test
    fun Remap_RefusesClothingWithBonesTheBodyDoesNotHave() {
        val cape = listOf("Hips", "CapeRoot", "CapeTip")
        val joints = intArrayOf(0, 1, 2, 0)
        val weights = floatArrayOf(0.2f, 0.5f, 0.3f, 0f)
        val r = JointRemap.remap(joints, weights, cape, body)
        assertEquals(JointRemapResult.Refused(listOf("CapeRoot", "CapeTip")), r)
        assertTrue((r as JointRemapResult.Refused).reason.contains("CapeRoot, CapeTip"), r.reason)
    }

    @Test
    fun Remap_IgnoresUnweightedExtraBones() {
        // The clothing skeleton has a bone the body lacks, but no vertex is weighted to it.
        val names = listOf("Hips", "Spine", "Unused_End")
        val out = remapped(JointRemap.remap(intArrayOf(1, 2, 0, 0), floatArrayOf(1f, 0f, 0f, 0f), names, body)).single()
        assertArrayEquals(intArrayOf(1, 2, 0, 0), out)
    }

    @Test
    fun Remap_MatchesMixamoPrefixesOnlyWhenUnique() {
        val prefixed = listOf("mixamorig:LeftArm", "mixamorig:Hips")
        val out = remapped(JointRemap.remap(intArrayOf(0, 1, 0, 0), floatArrayOf(0.5f, 0.5f, 0f, 0f), prefixed, body)).single()
        assertArrayEquals(intArrayOf(2, 0, 0, 0), out)
        val one = intArrayOf(0, 0, 0, 0)
        val w = floatArrayOf(1f, 0f, 0f, 0f)
        // Two body bones normalize to the same name: no guess, the item is refused.
        assertEquals(JointRemapResult.Refused(listOf("Hips")), JointRemap.remap(one, w, listOf("Hips"), listOf("mixamorig:Hips", "HIPS")))
        // An exact name wins over the loose ones.
        assertArrayEquals(intArrayOf(1, 0, 0, 0), remapped(JointRemap.remap(one, w, listOf("Hips"), listOf("mixamorig:Hips", "Hips"))).single())
    }

    @Test
    fun Remap_RefusesMalformedData() {
        val outOfRange = JointRemap.remap(intArrayOf(7, 0, 0, 0), floatArrayOf(1f, 0f, 0f, 0f), shirt, body)
        assertTrue(outOfRange is JointRemapResult.Refused && outOfRange.problems.single().contains("joint 7"), "$outOfRange")
        val sizes = JointRemap.remap(intArrayOf(0, 0, 0, 0), floatArrayOf(1f, 0f), shirt, body)
        assertTrue(sizes is JointRemapResult.Refused && sizes.problems.single().contains("4 joints for 2 weights"), "$sizes")
    }

    @Test
    fun RemapParts_OnRealSkeletons_RefusesTheWholeItemIfOnePartCannotFit() {
        val target = TestHumanoid.skeleton()
        // A clothing file on the same rig with its bones numbered backwards.
        val targetNames = JointRemap.names(target)
        val reversed = Skeleton(rebuild(target.rootBone, targetNames.size), targetNames.size)
        val sourceNames = JointRemap.names(reversed)
        assertEquals(targetNames.reversed(), sourceNames)

        val spine = sourceNames.indexOf("mixamorig:Spine1")
        val leftHand = sourceNames.indexOf("mixamorig:LeftHand")
        val torso = MeshPart(joints = intArrayOf(spine, leftHand, 0, 0), weights = floatArrayOf(0.7f, 0.3f, 0f, 0f))
        val sleeve = MeshPart(joints = intArrayOf(leftHand, 0, 0, 0), weights = floatArrayOf(1f, 0f, 0f, 0f))
        val ok = remapped(JointRemap.remap(listOf(torso, sleeve), reversed, target))
        assertArrayEquals(intArrayOf(targetNames.indexOf("mixamorig:Spine1"), targetNames.indexOf("mixamorig:LeftHand"), 0, 0), ok[0])
        assertEquals(targetNames.indexOf("mixamorig:LeftHand"), JointRemap.apply(sleeve, ok[1]).joints[0])

        // The same shirt on a body without arms: refused, naming every missing bone across parts.
        val armless = Skeleton(Bone(0, "mixamorig:Hips", Matrix4f()).also { it.addChild(Bone(1, "mixamorig:Spine1", Matrix4f())) }, 2)
        assertEquals(JointRemapResult.Refused(listOf("mixamorig:LeftHand")), JointRemap.remap(listOf(torso, sleeve), reversed, armless))
    }

    /** A copy of the hierarchy under [root] with indices reversed (count - 1 - index). */
    private fun rebuild(root: Bone, count: Int): Bone {
        val copy = Bone(count - 1 - root.index, root.name, Matrix4f(root.bindLocalTransform))
        root.children.forEach { copy.addChild(rebuild(it, count)) }
        return copy
    }
}
