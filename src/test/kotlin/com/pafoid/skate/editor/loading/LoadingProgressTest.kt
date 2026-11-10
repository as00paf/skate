package com.pafoid.skate.editor.loading

import com.pafoid.skate.editor.events.LoadingEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** EDT-56: the progress of weighted steps and what the loading screen shows. */
class LoadingProgressTest {
    private val steps = listOf(LoadingStep("a", 3f), LoadingStep("b", 1f))

    @Test
    fun Overall_WeighsEachStep() {
        assertEquals(0f, LoadingProgress.overall(steps, 0))
        assertEquals(0.375f, LoadingProgress.overall(steps, 0, 0.5f), 1e-6f)
        assertEquals(0.75f, LoadingProgress.overall(steps, 1), 1e-6f)
        assertEquals(1f, LoadingProgress.overall(steps, 2))
    }

    @Test
    fun Overall_ClampsTheStepFractionAndHandlesNoSteps() {
        assertEquals(0.75f, LoadingProgress.overall(steps, 0, 7f), 1e-6f)
        assertEquals(1f, LoadingProgress.overall(emptyList(), 0))
    }

    @Test
    fun Step_RejectsZeroWeight() {
        assertThrows<IllegalArgumentException> { LoadingStep("x", 0f) }
    }

    @Test
    fun ScreenState_ShowsOnlyAfterTheDelay() {
        val state = LoadingScreenState(showDelayMs = 200)
        state.apply(LoadingEvent.Started("t", "P"), nowMs = 1000)
        assertTrue(state.isActive)
        assertFalse(state.isVisible(1150))
        assertTrue(state.isVisible(1200))
        assertEquals(0.5f, state.elapsedSeconds(1500), 1e-6f)
    }

    @Test
    fun ScreenState_FollowsProgressWithoutGoingBack() {
        val state = LoadingScreenState()
        state.apply(LoadingEvent.Started("t"), 0)
        state.apply(LoadingEvent.Progressed(0.6f, "step.b", "file.png"), 0)
        state.apply(LoadingEvent.Progressed(0.2f, "step.a"), 0)
        assertEquals(0.6f, state.fraction)
        assertEquals("step.a", state.stepKey)
        assertEquals("", state.stepDetail)
    }

    @Test
    fun ScreenState_FinishedHidesAndANewOperationStartsFromZero() {
        val state = LoadingScreenState(showDelayMs = 0)
        state.apply(LoadingEvent.Started("t1", "A"), 0)
        state.apply(LoadingEvent.Progressed(0.9f, "s"), 0)
        state.apply(LoadingEvent.Finished, 10)
        assertFalse(state.isVisible(10))
        assertNull(state.titleKey)

        state.apply(LoadingEvent.Progressed(0.5f, "late"), 20) // a report after the end is ignored
        assertFalse(state.isActive)
        state.apply(LoadingEvent.Started("t2", "B"), 30)
        assertEquals(0f, state.fraction)
        assertNull(state.stepKey)
        assertEquals("B", state.detail)
    }
}
