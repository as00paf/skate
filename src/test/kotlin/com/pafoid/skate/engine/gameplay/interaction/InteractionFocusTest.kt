package com.pafoid.skate.engine.gameplay.interaction

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** GPL-24d §5.3: range, frontal preference and the item requirement, pure. */
class InteractionFocusTest {
    private val origin = Vector3f()
    private val forward = Vector3f(0f, 0f, 1f)

    @Test
    fun Focus_EnteringRangeGivesFocusAndLeavingClearsIt() {
        assertEquals(-1, InteractionFocus.choose(origin, forward, listOf(FocusCandidate(Vector3f(0f, 0f, 2f), 1.5f))))
        assertEquals(0, InteractionFocus.choose(origin, forward, listOf(FocusCandidate(Vector3f(0f, 0f, 1.4f), 1.5f))))
        assertEquals(-1, InteractionFocus.choose(origin, forward, emptyList()))
    }

    @Test
    fun Focus_TheMostFrontalTargetWinsAndTheNearerBreaksATie() {
        val behind = FocusCandidate(Vector3f(0f, 0f, -0.5f), 2f)
        val side = FocusCandidate(Vector3f(1f, 0f, 0.2f), 2f)
        val ahead = FocusCandidate(Vector3f(0f, 0f, 1.8f), 2f)
        assertEquals(2, InteractionFocus.choose(origin, forward, listOf(behind, side, ahead)), "ahead wins though farther")
        val nearAhead = FocusCandidate(Vector3f(0f, 0f, 0.8f), 2f)
        assertEquals(1, InteractionFocus.choose(origin, forward, listOf(ahead, nearAhead)), "same direction: the closer")
        assertEquals(0, InteractionFocus.choose(origin, forward, listOf(behind)), "a single target in reach is focused even behind")
    }

    @Test
    fun Focus_CandidatesNotOfferedAreSkipped() {
        val locked = FocusCandidate(Vector3f(0f, 0f, 1f), 2f, offered = false)
        val open = FocusCandidate(Vector3f(1f, 0f, 0f), 2f)
        assertEquals(1, InteractionFocus.choose(origin, forward, listOf(locked, open)))
    }
}
