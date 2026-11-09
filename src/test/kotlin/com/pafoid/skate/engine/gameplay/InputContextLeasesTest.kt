package com.pafoid.skate.engine.gameplay

import com.pafoid.skate.engine.input.ContextBlocking
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.InputContextStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InputContextLeasesTest {

    private val stack = InputContextStack.withDefaults()
    private val leases = InputContextLeases(stack)
    private val ride = InputContextRequest(InputContext("ride"), priority = 10)
    private val aim = InputContextRequest(InputContext("aim"), priority = 5, blocking = ContextBlocking.NONE)
    private val a = Any()
    private val b = Any()

    /** The entry of the context these tests push (the base ui entry stays on top of game contexts). */
    private fun top() = stack.entries().first { it.context == InputContext("ride") }

    @Test
    fun hold_PushesWithPriorityAndBlocking_AndAnUnchangedListIsANoOp() {
        leases.hold(a, listOf(ride))
        val revision = stack.revision

        leases.hold(a, listOf(ride))

        assertEquals(revision, stack.revision)
        assertEquals(InputContext("ride"), top().context)
        assertEquals(10, top().priority)
        assertEquals(ContextBlocking.CONSUME_BOUND, top().blocking)
    }

    @Test
    fun hold_ReplacesTheOwnersPreviousContexts() {
        leases.hold(a, listOf(ride))
        leases.hold(a, listOf(aim))

        assertFalse(stack.isActive(InputContext("ride")))
        assertTrue(stack.isActive(InputContext("aim")))
        assertEquals(3, stack.entries().size)
    }

    @Test
    fun twoOwners_KeepTheContextUntilBothRelease() {
        leases.hold(a, listOf(ride))
        leases.hold(b, listOf(ride))

        leases.release(a)
        assertTrue(stack.isActive(InputContext("ride")))
        leases.release(b)
        assertFalse(stack.isActive(InputContext("ride")))
        assertEquals(emptySet<Any>(), leases.owners)
    }

    @Test
    fun retainOnlyAndReleaseAll_LeaveTheBaseStack() {
        leases.hold(a, listOf(ride))
        leases.hold(b, listOf(aim))

        leases.retainOnly(setOf(b))
        assertEquals(setOf(b), leases.owners)
        assertFalse(stack.isActive(InputContext("ride")))

        leases.releaseAll()
        assertEquals(listOf(InputContext.UI, InputContext.GAMEPLAY), stack.entries().map { it.context })
    }
}
