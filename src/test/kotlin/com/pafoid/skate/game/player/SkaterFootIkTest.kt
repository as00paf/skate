package com.pafoid.skate.game.player

import com.pafoid.skate.engine.animation.ik.IkPass
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.GroundStateComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.ecs.systems.PlayerMotionSystem
import com.pafoid.skate.engine.gameplay.ground.GroundInfo
import com.pafoid.skate.engine.gameplay.ik.FootIkConfig
import com.pafoid.skate.engine.gameplay.ik.FootIkLegs
import com.pafoid.skate.engine.gameplay.ik.LegIk
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import com.pafoid.skate.engine.utils.SkeletonMath
import com.pafoid.skate.game.prefabs.Skater
import io.mockk.every
import io.mockk.mockk
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * GPL-38 (ANI-28 F1): the skater's ground foot IK binds to the james rig, whose bones are `LeftUpLeg`, … (the
 * importer strips `mixamorig9_`), and actually moves the feet.
 */
class SkaterFootIkTest {
    private val james = AssimpLoader(mockk(relaxed = true), mockk(relaxed = true)).loadModel("assets/Models/james.glb")
    private val skeleton get() = james.skeleton!!

    private class StandingHandle : IRigidBodyHandle {
        override val mass = 80f
        override val linearDamping = 0f
        override val angularDamping = 0f
        override var friction = 0f
        override var restitution = 0f
        override var collisionLayer = 1
        override var collisionMask = -1
        override var isTrigger = false
        override var isKinematic = false
        override var linearVelocity = Vector3f()
        override var angularVelocity = Vector3f()
        override fun setContinuousCollision(motionThreshold: Float, sweptRadius: Float) {}
        override fun setDamping(linear: Float, angular: Float) {}
        override fun setAngularFactor(factor: Vector3f) {}
        override fun applyCentralForce(force: Vector3f) {}
        override fun applyImpulse(impulse: Vector3f, relPos: Vector3f) {}
        override fun applyTorqueImpulse(torque: Vector3f) {}
        override fun applyForce(force: Vector3f, relPos: Vector3f) {}
        override fun getLocation(out: Vector3f): Vector3f = out.set(0f, 0f, 0f)
        override fun setLocation(location: Vector3f) {}
        override fun getRotation(out: Quaternionf): Quaternionf = out.identity()
        override fun setRotation(rotation: Quaternionf) {}
    }

    @Test
    fun FootIkLegs_DefaultLegsResolveOnTheJamesRig() {
        val legs = FootIkConfig().legs.map { FootIkLegs.resolve(it, skeleton) }
        assertEquals(listOf("LeftUpLeg", "LeftLeg", "LeftFoot"), legs[0]!!.let { listOf(it.rootBone, it.midBone, it.endBone) })
        assertEquals(listOf("RightUpLeg", "RightLeg", "RightFoot"), legs[1]!!.let { listOf(it.rootBone, it.midBone, it.endBone) })
        assertEquals(0.1f, legs[0]!!.lateral)
    }

    @Test
    fun FootIkLegs_OldMixamoPrefixedNamesStillResolveAndMissingBonesDoNot() {
        val old = LegIk("mixamorig9_LeftUpLeg", "mixamorig9_LeftLeg", "mixamorig9_LeftFoot", 0.1f)
        assertEquals("LeftFoot", FootIkLegs.resolve(old, skeleton)!!.endBone)
        val legless = Skeleton(Bone(0, "Hips").also { it.addChild(Bone(1, "Spine")) }, 2)
        assertNull(FootIkLegs.resolve(FootIkConfig().legs[0], legless))
        assertNull(FootIkLegs.boneName(skeleton, "Tail"))
    }

    @Test
    fun FootIk_RunsOnTheSkaterAndPlantsTheFeet() {
        SkateGameplay.register()
        val scene = Scene("Park").apply { isRunning = true }
        val skater = Skater("Skater", james).also(scene::addChild)
        skater.getComponent<RigidBody3D>()!!.rawBody = StandingHandle()
        val controller = skater.getComponent<PlayerController>()!!
        controller.footIk = FootIkConfig(enabled = true)
        (controller.behavior as SkateBehavior).mounted = false
        skater.getComponent<GroundStateComponent>()!!.ground = GroundInfo(found = true, distance = 0f, grounded = true)
        // Ground 5 cm above the clip's feet under both legs
        controller.footTargets.forEachIndexed { i, t ->
            t.position.set(if (i == 0) 0.1f else -0.1f, 0.13f, 0f)
            t.valid = true
        }
        val cameras = mockk<CameraManager>(relaxed = true)
        every { cameras.camera } returns CameraComponent()
        val system = PlayerMotionSystem(cameras).also { it.init(scene) }

        repeat(20) { system.update(1f / 60f) } // past the 0.15 s weight ramp

        val rig = skater.getComponent<Animator>()!!.ik
        assertEquals(listOf("LeftFoot", "RightFoot"), rig.twoBone.map { it.endBone })
        assertTrue(rig.twoBone.all { it.weight == 1f }, "weights ramped in: ${rig.twoBone.map { it.weight }}")

        // The pass the animation system runs: it now finds the bones and moves the left foot onto its target
        val pose = skater.getComponent<SkeletonComponent>()!!.pose
        val locals = Array(pose.localTransforms.size) { Matrix4f(pose.localTransforms[it]) }
        assertEquals(2, IkPass.apply(rig, skeleton, locals, worldToModel = null))
        val globals = Array(locals.size) { Matrix4f() }
        SkeletonMath.computeGlobalTransforms(skeleton.rootBone, locals, globals)
        val foot = globals[skeleton.getBoneByName("LeftFoot")!!.index].getTranslation(Vector3f())
        assertTrue(foot.distance(rig.twoBone[0].target) < 0.02f, "left foot at $foot, target ${rig.twoBone[0].target}")
    }
}
