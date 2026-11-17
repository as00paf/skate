package com.pafoid.skate.game.player

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.animation.graph.AnimParameters
import com.pafoid.skate.engine.animation.graph.ParameterType
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.systems.CharacterAnimationDriver
import com.pafoid.skate.engine.gameplay.CharacterInput
import com.pafoid.skate.engine.gameplay.LocomotionBehavior
import com.pafoid.skate.engine.gameplay.LocomotionConfig
import org.joml.Vector2f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GPL-31: the character publishes the animator parameters it writes, for the Animator window's suggestions. */
class SkateAnimationParametersTest {

    @Test
    fun AnimationParameters_SkaterPublishesTheFloatsItWrites() {
        val names = SkateBehavior(SkateControllerConfig()).animationParameters.map { it.name }
        assertEquals(listOf("speed", "verticalSpeed", "airTime", "landImpact", "manualBalance", "turn"), names)
        assertTrue(SkateBehavior.ANIMATION_PARAMETERS.all { it.type == ParameterType.FLOAT })
    }

    @Test
    fun AnimationParameters_EveryPublishedParameterIsWritten() {
        val behavior = SkateBehavior(SkateControllerConfig()).also { it.mounted = true }
        behavior.update(CharacterInput(move = Vector2f(0.5f, 0f), horizontalSpeed = 3f), 0.016f)
        val params = AnimParameters()
        val sentinel = -12345f
        behavior.animationParameters.forEach { params.setFloat(it.name, sentinel) }
        behavior.writeAnimationParameters(params)
        val unwritten = behavior.animationParameters.filter { params.getFloat(it.name) == sentinel }
        assertTrue(unwritten.isEmpty(), "published but never written: $unwritten")
    }

    @Test
    fun AnimationParameters_PlainLocomotionPublishesNone() {
        assertTrue(LocomotionBehavior(LocomotionConfig()).animationParameters.isEmpty())
    }

    @Test
    fun AnimationParameters_DriverResolvesTheCharacterOfAnAnimator() {
        SkateGameplay.register()
        val animator = Animator()
        GameObject("Skater").addComponent(animator).addComponent(PlayerController(SkateBehavior.ID, ""))
        assertEquals(SkateBehavior.ANIMATION_PARAMETERS, CharacterAnimationDriver.writtenParameters(animator))
    }

    @Test
    fun AnimationParameters_DriverIsEmptyWithoutACharacter() {
        val animator = Animator()
        GameObject("Prop").addComponent(animator)
        assertTrue(CharacterAnimationDriver.writtenParameters(animator).isEmpty())
        assertTrue(CharacterAnimationDriver.writtenParameters(null).isEmpty())
    }
}
