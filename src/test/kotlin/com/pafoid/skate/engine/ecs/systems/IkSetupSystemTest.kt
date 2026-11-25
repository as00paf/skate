package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.animation.procedural.TestHumanoid
import com.pafoid.skate.engine.animation.retarget.HumanoidBone
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.IkChainDef
import com.pafoid.skate.engine.ecs.components.IkChainKind
import com.pafoid.skate.engine.ecs.components.IkSetup
import com.pafoid.skate.engine.ecs.components.IkTarget
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.removeComponent
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ANI-28 slice b: the system turns a character's IkSetup into Animator IK constraints, finding targets by name. */
class IkSetupSystemTest {
    private val chain = IkChainDef("Left hand", IkChainKind.LIMB, listOf(HumanoidBone.LEFT_UPPER_ARM, HumanoidBone.LEFT_LOWER_ARM, HumanoidBone.LEFT_HAND), target = "Grip", rampSeconds = 0f)

    private fun character(name: String, setup: IkSetup) = GameObject(name)
        .addComponent(Transform())
        .addComponent(SkeletonComponent(SkeletonPose(TestHumanoid.skeleton())))
        .addComponent(Animator())
        .addComponent(setup)

    private fun target(name: String, at: Vector3f) = GameObject(name).addComponent(Transform()).addComponent(IkTarget()).also {
        it.getComponent<Transform>()!!.worldMatrix.translation(at)
    }

    @Test
    fun TargetsAreFoundInTheCharacterFirst_ThenTheScene() {
        val scene = Scene("s")
        val setupA = IkSetup(mutableListOf(chain))
        val setupB = IkSetup(mutableListOf(chain))
        val a = character("A", setupA).also { it.addChild(target("Grip", Vector3f(1f, 1f, 0f))) }
        val b = character("B", setupB)
        scene.addChild(a); scene.addChild(b)
        scene.addChild(target("Grip", Vector3f(5f, 0f, 0f)))
        val system = IkSetupSystem().also { it.init(scene) }

        system.update(1f / 60f)
        assertTrue(a.getComponent<Animator>()!!.ik.twoBone.isEmpty(), "nothing while the scene is not playing")
        scene.isRunning = true
        system.update(1f / 60f)
        val ikA = a.getComponent<Animator>()!!.ik.twoBone.single()
        val ikB = b.getComponent<Animator>()!!.ik.twoBone.single()
        assertEquals(Vector3f(1f, 1f, 0f), ikA.target, "A reaches its own Grip")
        assertEquals(Vector3f(5f, 0f, 0f), ikB.target, "B has none: the scene's Grip")
        assertEquals(1f, ikA.weight)

        a.removeComponent(setupA)
        system.update(1f / 60f)
        assertTrue(a.getComponent<Animator>()!!.ik.twoBone.isEmpty(), "a removed setup takes its constraints with it")
    }
}
