package com.pafoid.skate.engine.animation.graph

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AnimationGraphTest {

    private fun constMotion(x: Float, dur: Float = 1f) =
        ClipMotion("c$x", dur) { _, out -> out.translations[0] = x }

    private fun machine(
        transitions: List<TransitionDef>,
        events: List<AnimEventDef> = emptyList(),
        loop: Boolean = true,
    ): AnimationStateMachine {
        val asset = AnimationControllerAsset(
            "c", "push",
            listOf(
                StateDef("push", "push", events = events, loop = loop),
                StateDef("ride", "ride"),
                StateDef("trick", "trick"),
            ),
            transitions
        )
        val motions = mapOf("push" to constMotion(0f), "ride" to constMotion(10f), "trick" to constMotion(100f))
        return AnimationStateMachine(asset, motions, 1)
    }

    @Test
    fun `PoseBlend_Endpoints_And_Midpoint`() {
        val a = Pose(1); val b = Pose(1)
        b.translations[0] = 10f
        val out = Pose(1)
        Pose.blend(a, b, 0f, out); assertEquals(0f, out.translations[0], 1e-5f)
        Pose.blend(a, b, 1f, out); assertEquals(10f, out.translations[0], 1e-5f)
        Pose.blend(a, b, 0.5f, out); assertEquals(5f, out.translations[0], 1e-5f)
    }

    @Test
    fun `PoseBlend_Rotation_TakesShortestPath`() {
        val a = Pose(1); val b = Pose(1)
        // b is -identity (same rotation, opposite sign): shortest path must stay at identity
        b.rotations[3] = -1f
        val out = Pose(1)
        Pose.blend(a, b, 0.5f, out)
        assertEquals(1f, kotlin.math.abs(out.rotations[3]), 1e-4f)
    }

    @Test
    fun `BlendTree1D_InterpolatesBetweenNeighbours_And_Clamps`() {
        val tree = BlendTree1D("speed", listOf(10f to constMotion(10f), 0f to constMotion(0f), 20f to constMotion(20f)))
        val p = AnimParameters()
        val out = Pose(1)
        p.setFloat("speed", 5f); tree.evaluate(p, 0f, out); assertEquals(5f, out.translations[0], 1e-4f)
        p.setFloat("speed", 15f); tree.evaluate(p, 0f, out); assertEquals(15f, out.translations[0], 1e-4f)
        p.setFloat("speed", -3f); tree.evaluate(p, 0f, out); assertEquals(0f, out.translations[0], 1e-4f)
        p.setFloat("speed", 99f); tree.evaluate(p, 0f, out); assertEquals(20f, out.translations[0], 1e-4f)
    }

    @Test
    fun `BlendTree1D_BlendsDuration`() {
        val tree = BlendTree1D("s", listOf(0f to constMotion(0f, 2f), 1f to constMotion(1f, 1f)))
        val p = AnimParameters().also { it.setFloat("s", 0.5f) }
        assertEquals(1.5f, tree.duration(p), 1e-4f)
    }

    @Test
    fun `BlendTree2D_ExactChildAndCentre`() {
        val tree = BlendTree2D("x", "y", listOf(
            Triple(0f, 0f, constMotion(0f)), Triple(1f, 0f, constMotion(10f)),
            Triple(0f, 1f, constMotion(20f)), Triple(1f, 1f, constMotion(30f)),
        ))
        val p = AnimParameters()
        val out = Pose(1)
        p.setFloat("x", 1f); p.setFloat("y", 0f); tree.evaluate(p, 0f, out)
        assertEquals(10f, out.translations[0], 1e-4f)
        p.setFloat("x", 0.5f); p.setFloat("y", 0.5f); tree.evaluate(p, 0f, out)
        assertEquals(15f, out.translations[0], 1e-3f)
        assertEquals(1f, tree.weights(0.3f, 0.7f).sum(), 1e-5f)
    }

    @Test
    fun `StateMachine_ConditionStartsCrossfade_ThenCompletes`() {
        val m = machine(listOf(TransitionDef("push", "ride", listOf(TransitionCondition("riding", ConditionOp.TRUE)), 0.5f)))
        m.update(0.1f)
        assertEquals("push", m.currentState)
        m.parameters.setBool("riding", true)
        m.update(0.1f)
        assertTrue(m.isTransitioning)
        assertEquals("ride", m.nextState)
        m.update(0.25f)
        // fade is 0.25/0.5 through: halfway between push(0) and ride(10)
        assertEquals(5f, m.pose.translations[0], 1e-3f)
        m.update(0.3f)
        assertFalse(m.isTransitioning)
        assertEquals("ride", m.currentState)
        assertEquals(10f, m.pose.translations[0], 1e-3f)
    }

    @Test
    fun `StateMachine_BlendIsMonotonic_NoPops`() {
        val m = machine(listOf(TransitionDef("push", "ride", listOf(TransitionCondition("go", ConditionOp.TRUE)), 1f)))
        m.parameters.setBool("go", true)
        var last = -1f
        repeat(12) {
            m.update(0.1f)
            assertTrue(m.pose.translations[0] >= last - 1e-4f)
            last = m.pose.translations[0]
        }
        assertEquals(10f, last, 1e-3f)
    }

    @Test
    fun `StateMachine_InterruptedFade_StartsFromVisiblePose`() {
        val m = machine(listOf(
            TransitionDef("push", "ride", listOf(TransitionCondition("a", ConditionOp.TRUE)), 1f),
            TransitionDef("ride", "trick", listOf(TransitionCondition("b", ConditionOp.TRUE)), 1f),
        ))
        m.parameters.setBool("a", true)
        m.update(0.01f) // starts push->ride
        m.update(0.5f)  // halfway: ~5
        val before = m.pose.translations[0]
        m.parameters.setBool("b", true)
        m.update(0.0f)  // interrupt; evaluation must not jump
        assertEquals(before, m.pose.translations[0], 0.2f)
        assertEquals("trick", m.nextState)
    }

    @Test
    fun `StateMachine_TriggerIsConsumed`() {
        val m = machine(listOf(TransitionDef(null, "trick", listOf(TransitionCondition("jump", ConditionOp.TRIGGER)), 0f)))
        m.parameters.setTrigger("jump")
        m.update(0.1f)
        assertEquals("trick", m.currentState)
        assertFalse(m.parameters.hasTrigger("jump"))
    }

    @Test
    fun `StateMachine_ExitTimeTransitionWaitsForSourceToFinish`() {
        val m = machine(listOf(TransitionDef("push", "ride", emptyList(), 0f, hasExitTime = true, exitTime = 0.9f)))
        m.update(0.5f)
        assertEquals("push", m.currentState)
        m.update(0.5f)
        assertEquals("ride", m.currentState)
    }

    @Test
    fun `StateMachine_FiresEventsOncePerCrossing_AndAcrossLoops`() {
        val m = machine(emptyList(), listOf(AnimEventDef(0.5f, "footstep")))
        m.update(0.4f); assertTrue(m.firedEvents.isEmpty())
        m.update(0.2f); assertEquals(listOf(FiredEvent("push", "footstep")), m.firedEvents)
        m.update(0.1f); assertTrue(m.firedEvents.isEmpty())
        m.update(1.0f); assertEquals(listOf(FiredEvent("push", "footstep")), m.firedEvents) // next cycle
    }

    @Test
    fun `StateMachine_NonLoopingClampsAtEnd`() {
        val m = machine(emptyList(), loop = false)
        m.update(5f)
        assertEquals(1f, m.normalizedTime, 1e-5f)
    }

    @Test
    fun `Controller_RejectsUnknownReferences`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnimationControllerAsset("c", "a", listOf(StateDef("a", "m")), listOf(TransitionDef("a", "missing")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnimationControllerAsset("c", "zzz", listOf(StateDef("a", "m")))
        }
    }
}
