package com.pafoid.skate.engine.animation.procedural

import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import com.pafoid.skate.engine.animation.graph.ConditionOp
import com.pafoid.skate.engine.animation.graph.StateDef
import com.pafoid.skate.engine.animation.graph.TransitionCondition
import com.pafoid.skate.engine.animation.graph.TransitionDef
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.game.prefabs.Skater
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkaterControllerTest {
    private val logger = LoggerService()
    private val skeleton = TestHumanoid.skeleton()
    private val onFootClips = listOf("idle_0", "walking", "running", "jump", "falling idle", "skate riding")

    private fun animator(clips: List<String> = onFootClips) =
        Animator(animations = clips.map { Animation(it, emptyList(), 1f, "$it.fbx") }.toMutableList()).apply {
            controllerPath = Skater.CONTROLLER_PATH
        }

    /** An engine default (`engine:Data/...`, GPL-26) or a plain path, read like the game reads it. */
    private fun resource(path: String) = com.pafoid.skate.engine.gameplay.CharacterConfigs.readText(path)!!

    @Test
    fun `SkaterController_LoadsFromBundledResourceWithProceduralRidingPoses`() {
        val animator = animator()

        assertTrue(animator.resolveController(logger, skeleton))
        val machine = animator.stateMachine!!
        assertEquals("idle", machine.currentState)

        animator.play("ride_idle")
        repeat(2) { machine.update(0.5f) }
        assertEquals("ride_idle", machine.currentState)
    }

    @Test
    fun `SkaterController_HasASlotForEveryAnimationTheSkateConfigRequests`() {
        val asset = AnimationControllerAsset.fromJson(resource(Skater.CONTROLLER_PATH))
        val config = Json.parseToJsonElement(resource(Skater.CONFIG_PATH)).jsonObject
        val requested = listOf(config.getValue("animations"), config.getValue("onFoot").jsonObject.getValue("animations"))
            .flatMap { it.jsonObject.values.map { v -> v.jsonPrimitive.content } }
        val states = asset.states.map { it.name }.toSet()

        assertEquals(emptyList<String>(), requested.filter { it !in states })
        listOf("push", "ollie", "land", "grind", "manual").forEach { assertTrue(it in asset.poses, "missing pose slot $it") }
        assertEquals(emptyList<String>(), asset.issues(onFootClips.toSet()))
    }

    @Test
    fun `SkaterController_OllieLandsThenRides`() {
        val animator = animator()
        animator.resolveController(logger, skeleton)
        val machine = animator.stateMachine!!

        animator.play("ride")
        repeat(2) { machine.update(0.3f) }
        assertEquals("ride", machine.currentState)
        animator.play("ollie")
        repeat(2) { machine.update(0.1f) }
        assertEquals("ollie", machine.currentState)

        animator.play("ride")
        repeat(2) { machine.update(0.1f) }
        assertEquals("land", machine.currentState)
        // Land is not cut short by the any-state "ride" request; it hands over once it has played.
        machine.update(0.1f)
        assertEquals("land", machine.currentState)
        repeat(5) { machine.update(0.1f) }
        assertEquals("ride", machine.currentState)
    }

    @Test
    fun `RealClipReplacesTheProceduralSlotWithTheSameName`() {
        val asset = AnimationControllerAsset(
            "c", "p", listOf(StateDef("p", "p")),
            poses = mapOf("p" to ProceduralClipDef(listOf(PoseKeyDef(0f, HumanoidPoseDef())))),
        )
        val rig = Skeleton(Bone(0, "root"), 1)
        val withoutClip = Animator()
        assertFalse(runCatching { withoutClip.useController(asset, rig) }.isSuccess, "procedural slot needs a humanoid rig")

        val withClip = Animator(animations = mutableListOf(Animation("p", emptyList(), 1f, "p.fbx")))
        withClip.useController(asset, rig)
        assertEquals("p", withClip.stateMachine!!.currentState)
    }

    @Test
    fun `AnyStateTransition_SkipsExceptedSources`() {
        val asset = AnimationControllerAsset(
            "c", "a", listOf(StateDef("a", "x"), StateDef("b", "x")),
            listOf(TransitionDef(null, "b", listOf(TransitionCondition("go", ConditionOp.TRUE)), duration = 0f, exceptFrom = listOf("a"))),
            poses = mapOf("x" to ProceduralClipDef(listOf(PoseKeyDef(0f, HumanoidPoseDef())))),
        )
        val machine = Animator().useController(asset, skeleton)
        machine.parameters.setBool("go", true)
        machine.update(0.1f)
        assertEquals("a", machine.currentState)
    }

    @Test
    fun `PlayWithController_RequestsOneStateAtATime`() {
        val animator = animator()
        animator.resolveController(logger, skeleton)
        val params = animator.parameters!!

        animator.play("push")
        animator.play("ride")
        assertFalse(params.getBool("push"))
        assertTrue(params.getBool("ride"))
        assertEquals("ride", animator.requestedState)
    }

    @Test
    fun `ClipName_IsTheFileNameWithEitherSeparator`() {
        assertEquals("falling idle", AnimationLoader.clipName("C:\\proj\\Assets\\EngineDefaults\\Characters\\animations\\falling idle.fbx"))
        assertEquals("idle_1", AnimationLoader.clipName("assets/Animations/idle_1.fbx"))
    }
}
