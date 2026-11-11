package com.pafoid.skate.engine.animation.graph

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.Animator
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** ANI-21a: the additive controller format (parameters, layout, clips, version, states without a clip). */
class ControllerFormatTest {

    private val skeleton = Skeleton(Bone(0, "root", Matrix4f().translation(0f, 2f, 0f)), 1)
    private val logger = LoggerService()

    private fun skaterJson() = com.pafoid.skate.engine.gameplay.CharacterConfigs.readText(Assets.Configs.SKATER_CONTROLLER)!!

    @Test
    fun NewFields_RoundTrip() {
        val asset = AnimationControllerAsset(
            "c", "idle", listOf(StateDef("idle", ""), StateDef("walk", "walk")),
            listOf(TransitionDef("idle", "walk", listOf(TransitionCondition("speed", ConditionOp.GREATER, 1f)))),
            parameters = listOf(ParameterDef("speed", ParameterType.FLOAT, 0.5f), ParameterDef("jump", ParameterType.TRIGGER)),
            layout = mapOf("idle" to NodePos(0f, 0f), AnimationControllerAsset.ANY_STATE_NODE to NodePos(-200f, -100f)),
            clips = listOf("Animations/walk.skanim"),
        )
        val back = AnimationControllerAsset.fromJson(asset.toJson())
        assertEquals(asset, back)
        assertEquals(1, back.version)
    }

    @Test
    fun OldFiles_LoadUnchanged_AndInferTheirParameters() {
        val skater = AnimationControllerAsset.fromJson(skaterJson())
        assertEquals(1, skater.version, "files without a version read as 1")
        assertTrue(skater.parameters.isEmpty() && skater.layout.isEmpty() && skater.clips.isEmpty())
        val inferred = ControllerParameters.effective(skater)
        assertTrue(inferred.isNotEmpty())
        assertTrue(inferred.all { it.type == ParameterType.BOOL }, "every skater condition is '<state> TRUE'")
        assertEquals(ControllerParameters.usedNames(skater), inferred.map { it.name })
        assertTrue(skater.warnings().isEmpty(), "nothing declared, every state has a clip: no warnings")
    }

    @Test
    fun Warnings_AreSeparateFromErrors() {
        val asset = AnimationControllerAsset(
            "c", "a", listOf(StateDef("a", ""), StateDef("b", "clip")),
            listOf(
                TransitionDef("a", "b", listOf(TransitionCondition("speed", ConditionOp.TRUE))),
                TransitionDef("b", "a", listOf(TransitionCondition("ghost", ConditionOp.GREATER))),
            ),
            parameters = listOf(ParameterDef("speed", ParameterType.FLOAT)),
        )
        assertTrue(asset.issues(setOf("clip")).isEmpty(), "an empty motion is not an error")
        val warnings = asset.warnings()
        assertEquals(3, warnings.size, warnings.joinToString())
        assertTrue(warnings[0].contains("no clip"))
        assertTrue(warnings.any { it.contains("'ghost'") })
        assertTrue(warnings.any { it.contains("'speed' is a float") })
    }

    @Test
    fun Validation_RejectsDuplicateParametersAndBlankStates() {
        assertThrows<IllegalArgumentException> {
            AnimationControllerAsset("c", "a", listOf(StateDef("a", "")), parameters = listOf(ParameterDef("p", ParameterType.BOOL), ParameterDef("p", ParameterType.FLOAT)))
        }
        assertThrows<IllegalArgumentException> { AnimationControllerAsset("c", " ", listOf(StateDef(" ", ""))) }
    }

    @Test
    fun Runtime_SeedsDefaults_AndPlaysTheRestPoseWithoutAClip() {
        val asset = AnimationControllerAsset(
            "c", "idle", listOf(StateDef("idle", ""), StateDef("run", "")),
            listOf(TransitionDef("idle", "run", listOf(TransitionCondition("fast", ConditionOp.TRUE)), duration = 0f)),
            parameters = listOf(ParameterDef("speed", ParameterType.FLOAT, 2.5f), ParameterDef("fast", ParameterType.BOOL, 1f)),
        )
        val machine = AnimationStateMachine(asset, emptyMap(), 1)
        assertEquals(2.5f, machine.parameters.getFloat("speed"))
        assertEquals(1f, machine.pose.rotations[3], "identity rest pose")
        machine.update(0.1f)
        assertEquals("run", machine.currentState, "the bool default 'on' fires the transition")
        assertEquals(RestMotion.DURATION, machine.motionOf("run").duration(machine.parameters))
    }

    @Test
    fun Animator_RestPoseIsTheBindPose(@TempDir dir: File) {
        val file = File(dir, "c.controller.json").apply {
            writeText(AnimationControllerAsset("c", "idle", listOf(StateDef("idle", ""))).toJson())
        }
        val animator = Animator().apply { controllerPath = file.path }
        assertTrue(animator.resolveController(logger, skeleton))
        assertEquals(2f, animator.stateMachine!!.pose.translations[1], "the bone's bind translation")
    }

    @Test
    fun Animator_LoadsTheControllersOwnClips_AndAClipOnTheAnimatorWins(@TempDir dir: File) {
        val asset = AnimationControllerAsset(
            "c", "idle", listOf(StateDef("idle", "idle"), StateDef("flip", "kickflip")),
            clips = listOf("Animations/kickflip.skanim", "Animations/idle.skanim"),
        )
        val file = File(dir, "c.controller.json").apply { writeText(asset.toJson()) }
        val own = Animation("idle", emptyList(), 2f, "idle.glb")
        val animator = Animator(animations = mutableListOf(own)).apply { controllerPath = file.path }
        val loaded = mutableListOf<String>()
        val ok = animator.resolveController(logger, skeleton, loadClip = { path ->
            loaded += path
            Animation(path.substringAfterLast('/').substringBefore('.'), emptyList(), 1f, path)
        })
        assertTrue(ok)
        assertEquals(listOf("Animations/kickflip.skanim", "Animations/idle.skanim"), loaded)
        assertEquals(listOf("kickflip"), animator.controllerClips.map { it.name })
        assertEquals(2f, animator.stateMachine!!.motionOf("idle").duration(AnimParameters()), "the Animator's own idle wins")

        assertFalse(animator.resolveController(logger, skeleton, loadClip = { null }), "a missing listed clip fails like a missing clip")
    }
}
