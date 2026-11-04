package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.render.renderer.passes.PostChain.Target
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-17 / REN-18: post effects must never sample the texture of the target they draw into. */
class PostChainTest {

    @Test
    fun noStepReadsItsOwnTarget() {
        for (n in 1..PostEffect.entries.size) {
            for (step in PostChain.plan(n)) assertNotEquals(step.source, step.destination, "n=$n $step")
        }
    }

    @Test
    fun effectsNeverWriteTheSceneTarget() {
        // SSAO samples the scene depth, and the debug pass depth-tests against it, so the scene FBO is never a destination.
        for (n in 1..PostEffect.entries.size) {
            assertTrue(PostChain.plan(n).none { it.destination == Target.SCENE }, "n=$n")
        }
    }

    @Test
    fun firstEffectReadsTheSceneAndEachNextReadsThePreviousOutput() {
        val steps = PostChain.plan(4)
        assertEquals(Target.SCENE, steps[0].source)
        for (i in 1 until steps.size) assertEquals(steps[i - 1].destination, steps[i].source)
        assertEquals(
            listOf(Target.PING, Target.PONG, Target.PING, Target.PONG),
            steps.map { it.destination },
        )
    }

    @Test
    fun resultTargetIsTheLastDestination() {
        assertEquals(Target.SCENE, PostChain.resultTarget(0))
        for (n in 1..PostEffect.entries.size) assertEquals(PostChain.plan(n).last().destination, PostChain.resultTarget(n))
    }

    @Test
    fun noEffectsMeansNoSteps() {
        assertTrue(PostChain.plan(0).isEmpty())
    }
}
