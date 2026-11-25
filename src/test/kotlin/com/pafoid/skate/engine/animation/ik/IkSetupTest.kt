package com.pafoid.skate.engine.animation.ik

import com.pafoid.skate.engine.animation.procedural.TestHumanoid
import com.pafoid.skate.engine.animation.retarget.HumanoidBone
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.IkChainDef
import com.pafoid.skate.engine.ecs.components.IkChainKind
import com.pafoid.skate.engine.ecs.components.IkSetup
import com.pafoid.skate.engine.ecs.components.IkTarget
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ANI-28 slice b: IK chains as data, resolved by humanoid slot, bound to an IkRig with targets, goals and ramps. */
class IkSetupTest {
    private val skeleton = TestHumanoid.skeleton()
    private val leftLeg = IkChainDef(
        "Left foot", IkChainKind.LIMB, listOf(HumanoidBone.LEFT_UPPER_LEG, HumanoidBone.LEFT_LOWER_LEG, HumanoidBone.LEFT_FOOT),
        target = "LeftFootTarget", rampSeconds = 0.1f,
    )
    private val look = IkChainDef("Look", IkChainKind.LOOK, listOf(HumanoidBone.HEAD), target = "Focus", rampSeconds = 0f)

    private fun foot(rig: IkRig): Vector3f {
        val locals = Array(skeleton.boneCount) { Matrix4f() }
        fun walk(b: Bone) { locals[b.index].set(b.bindLocalTransform); b.children.forEach(::walk) }
        walk(skeleton.rootBone)
        IkPass.apply(rig, skeleton, locals)
        return IkSolvers.globalOf(IkPass.parentsOf(skeleton), locals, skeleton.getBoneByName("mixamorig:LeftFoot")!!.index).getTranslation(Vector3f())
    }

    @Test
    fun Resolve_BySlotOrByNameOverride() {
        assertEquals(ResolvedChain.Bones(listOf("mixamorig:LeftUpLeg", "mixamorig:LeftLeg", "mixamorig:LeftFoot")), IkChains.resolve(leftLeg, skeleton))
        val custom = Skeleton(Bone(0, "thigh", Matrix4f()).also { t -> t.addChild(Bone(1, "shin", Matrix4f()).also { it.addChild(Bone(2, "paw", Matrix4f())) }) }, 3)
        assertTrue(IkChains.resolve(leftLeg, custom) is ResolvedChain.Problem, "no humanoid names")
        assertEquals(ResolvedChain.Bones(listOf("thigh", "shin", "paw")), IkChains.resolve(leftLeg.copy(boneNames = listOf("thigh", "shin", "paw")), custom))
    }

    @Test
    fun Resolve_ReportsWhatIsWrong() {
        fun reason(def: IkChainDef) = (IkChains.resolve(def, skeleton) as ResolvedChain.Problem).reason
        assertTrue(reason(leftLeg.copy(slots = listOf(HumanoidBone.LEFT_UPPER_LEG, HumanoidBone.LEFT_FOOT))).contains("needs 3"))
        assertTrue(reason(leftLeg.copy(slots = listOf(HumanoidBone.LEFT_UPPER_LEG, HumanoidBone.LEFT_LOWER_LEG, HumanoidBone.RIGHT_FOOT))).contains("not a parent-child chain"))
        assertTrue(reason(leftLeg.copy(boneNames = listOf("a", "b", "c"))).contains("no bone named 'a'"))
        assertTrue(reason(look.copy(slots = emptyList())).contains("no bones"))
    }

    @Test
    fun Binder_ReachesTheTargetObjectAndRampsIn() {
        val setup = IkSetup(mutableListOf(leftLeg))
        val rig = IkRig()
        val goal = Vector3f(0.25f, 0.2f, 0.3f)
        val targets = mapOf("LeftFootTarget" to Matrix4f().translation(goal))
        val binder = IkSetupBinder()
        binder.update(setup, skeleton, rig, 0.05f) { targets[it] }
        val c = rig.twoBone.single()
        assertEquals(0.5f, c.weight, 1e-5f, "half way through a 0.1 s ramp")
        binder.update(setup, skeleton, rig, 0.05f) { targets[it] }
        assertEquals(1f, c.weight, 1e-5f)
        assertTrue(foot(rig).distance(goal) < 1e-3f, "foot at ${foot(rig)}")
        assertTrue(setup.problems.isEmpty())
    }

    @Test
    fun Binder_GoalWinsAndTargetLossBlendsOutWithoutJumping() {
        val setup = IkSetup(mutableListOf(leftLeg.copy(goal = "board", alignRotation = true)))
        val rig = IkRig()
        val binder = IkSetupBinder()
        val objectTarget = mapOf("LeftFootTarget" to Matrix4f().translation(0.3f, 0.1f, 0f))
        val turn = Quaternionf().rotationY(0.5f)
        setup.setGoal("board", Vector3f(0.1f, 0.2f, 0.4f), turn, weight = 0.5f)
        repeat(3) { binder.update(setup, skeleton, rig, 0.1f) { objectTarget[it] } }
        val c = rig.twoBone.single()
        assertEquals(Vector3f(0.1f, 0.2f, 0.4f), c.target, "the goal wins over the target object")
        assertEquals(0.5f, c.weight, 1e-5f, "goal weight multiplies")
        assertEquals(turn, c.endRotation)

        setup.clearGoal("board")
        binder.update(setup, skeleton, rig, 0.1f) { objectTarget[it] }
        assertEquals(Vector3f(0.3f, 0.1f, 0f), c.target, "back to the target object")

        binder.update(setup, skeleton, rig, 0.05f) { null }
        assertEquals(Vector3f(0.3f, 0.1f, 0f), c.target, "a lost target keeps the last position")
        assertEquals(0.5f, c.weight, 1e-5f, "while blending out")
    }

    @Test
    fun Binder_LeavesCodeConstraintsAndFollowsEdits() {
        val rig = IkRig()
        val fromCode = rig.addTwoBone(TwoBoneIkConstraint("a", "b", "c", weight = 1f))
        val setup = IkSetup(mutableListOf(leftLeg, look))
        val binder = IkSetupBinder()
        val targets = mapOf("LeftFootTarget" to Matrix4f(), "Focus" to Matrix4f().translation(0f, 2f, 5f))
        binder.update(setup, skeleton, rig, 1f) { targets[it] }
        assertEquals(2, rig.twoBone.size)
        assertEquals(1, rig.lookAt.size)
        assertEquals(Vector3f(0f, 2f, 5f), rig.lookAt.single().target)

        // Editing one chain rebuilds the setup's constraints; the ramped weight of the others carries over.
        setup.chains[1] = look.copy(maxAngleDegrees = 30f)
        binder.update(setup, skeleton, rig, 0f) { targets[it] }
        assertEquals(30f, rig.lookAt.single().maxAngleDegrees)
        assertEquals(1f, rig.twoBone.last().weight, "unchanged chain keeps its weight")
        assertTrue(fromCode in rig.twoBone)

        setup.enabled = false
        binder.update(setup, skeleton, rig, 1f) { targets[it] }
        assertEquals(0f, rig.twoBone.last().weight, "a disabled setup blends out")

        binder.detach(rig)
        assertEquals(listOf(fromCode), rig.twoBone)
        assertTrue(rig.lookAt.isEmpty())
    }

    @Test
    fun Binder_ListsChainsThatDoNotFitTheRig() {
        val setup = IkSetup(mutableListOf(leftLeg, leftLeg.copy(name = "Tail", slots = emptyList(), boneNames = listOf("Tail1", "Tail2", "Tail3"))))
        val rig = IkRig()
        IkSetupBinder().update(setup, skeleton, rig, 0f) { null }
        assertEquals(1, rig.twoBone.size, "the valid chain is still set up")
        assertEquals(listOf("'Tail': no bone named 'Tail1'"), setup.problems)
    }

    @Test
    fun Components_SaveAndLoad() {
        val serializer = Serializer()
        val setup = IkSetup(mutableListOf(leftLeg.copy(pole = "Knee", alignRotation = true), look))
        setup.setGoal("board", Vector3f(1f, 2f, 3f))
        val back = serializer.json.decodeFromString(Component.serializer(), serializer.json.encodeToString(Component.serializer(), setup)) as IkSetup
        assertEquals(setup.chains, back.chains)
        assertTrue(back.goals.isEmpty(), "goals are runtime only")
        val target = IkTarget("front foot", Vector3f(0f, 1f, 0f))
        val targetBack = serializer.json.decodeFromString(Component.serializer(), serializer.json.encodeToString(Component.serializer(), target)) as IkTarget
        assertEquals(target, targetBack)
        assertNull(IkSetup().goals["x"])
    }
}
