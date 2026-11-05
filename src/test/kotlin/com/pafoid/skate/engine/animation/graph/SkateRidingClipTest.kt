package com.pafoid.skate.engine.animation.graph

import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.game.prefabs.Skater
import io.mockk.mockk
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** ANI-10: the real `skate riding` clip on the james rig, through the bundled skater controller. */
class SkateRidingClipTest {
    private val skeleton: Skeleton = AssimpLoader(mockk(relaxed = true), mockk(relaxed = true)).loadModel(JAMES).skeleton!!
    private val clip: Animation = AnimationLoader().loadAnimations(RIDING)[0]

    private fun machine(): AnimationStateMachine {
        val stubs = listOf("idle_0", "walking", "running", "jump", "falling idle").map { Animation(it, emptyList(), 1f, "$it.fbx") }
        val animator = Animator(animations = (stubs + clip).toMutableList()).apply { controllerPath = Skater.CONTROLLER_PATH }
        assertTrue(animator.resolveController(LoggerService(), skeleton))
        return animator.stateMachine!!
    }

    private fun positions(pose: Pose?): Map<String, Vector3f> {
        val out = mutableMapOf<String, Vector3f>()
        fun walk(b: Bone, parent: Matrix4f) {
            val local = if (pose != null && b.index in 0 until pose.boneCount) pose.toMatrix(b.index, Matrix4f()) else Matrix4f(b.bindLocalTransform)
            val g = Matrix4f(parent).mul(local)
            out[b.name] = g.getTranslation(Vector3f())
            b.children.forEach { walk(it, g) }
        }
        walk(skeleton.rootBone, Matrix4f())
        return out
    }

    private fun Map<String, Vector3f>.lowestFoot() = listOf("LeftFoot", "RightFoot", "LeftToeBase", "RightToeBase").minOf { getValue(it).y }

    @Test
    fun `RidingClip_LoadsOnTheSkaterRigAndFillsTheRidingSlots`() {
        assertEquals("skate riding", clip.name)
        assertEquals(1.2f, clip.duration, 1e-3f)
        assertTrue(clip.channels.all { skeleton.getBoneByName(it.targetNodeName) != null }, "clip targets bones james lacks")

        val machine = machine()
        for (state in listOf("ride", "ride_idle")) {
            val motion = machine.motionOf(state)
            assertTrue(motion is FittedMotion && motion.inner is ClipMotion, "$state plays $motion")
            assertEquals("skate riding", ((motion as FittedMotion).inner as ClipMotion).name)
        }
        assertTrue(machine.motionOf("push") is com.pafoid.skate.engine.animation.procedural.ProceduralClipMotion, "push keeps its procedural fallback")
    }

    @Test
    fun `RidingClip_StandsOnTheDeckLeftFootTowardTheNose`() {
        val motion = machine().motionOf("ride")
        val rest = positions(null).lowestFoot()
        for (t in listOf(0f, 0.25f, 0.5f, 0.75f)) {
            val p = positions(Pose(skeleton.boneCount).also { motion.evaluate(AnimParameters(), t, it) })
            assertEquals(rest, p.lowestFoot(), 0.02f, "feet on the deck at t=$t")
            assertTrue(p.getValue("LeftFoot").z > p.getValue("RightFoot").z + 0.2f, "left foot toward the nose (+Z) at t=$t")
            assertTrue(abs(p.getValue("LeftFoot").x) < 0.08f && abs(p.getValue("RightFoot").x) < 0.08f, "feet on the deck line at t=$t")
            val toes = Vector3f(p.getValue("LeftToeBase")).sub(p.getValue("LeftFoot"))
            assertTrue(toes.x < 0f, "toes point to the rider's right (regular stance) at t=$t")
        }
    }

    @Test
    fun `RidingClip_LoopsWithoutAPop`() {
        val motion = machine().motionOf("ride")
        val start = Pose(skeleton.boneCount).also { motion.evaluate(AnimParameters(), 0f, it) }
        val end = Pose(skeleton.boneCount).also { motion.evaluate(AnimParameters(), 0.999f, it) }
        val worst = start.rotations.indices.maxOf { abs(start.rotations[it] - end.rotations[it]) }
        assertTrue(worst < 0.01f, "loop seam rotation delta $worst")
    }

    private companion object {
        const val JAMES = "assets/characters/james.glb"
        const val RIDING = "assets/characters/animations/skate riding.fbx"
    }
}
