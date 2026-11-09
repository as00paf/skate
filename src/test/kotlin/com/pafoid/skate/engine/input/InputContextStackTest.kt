package com.pafoid.skate.engine.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InputContextStackTest {

    private val vehicle = InputContext("vehicle")
    private val menu = InputContext("menu")

    @Test
    fun ContextStack_HigherPriorityIsOnTopAndLaterPushWinsTies() {
        val s = InputContextStack()
        s.push(InputContext.GAMEPLAY, priority = 0)
        s.push(menu, priority = 10)
        s.push(vehicle, priority = 0)
        assertEquals(listOf(menu, vehicle, InputContext.GAMEPLAY), s.entries().map { it.context })
    }

    @Test
    fun ContextStack_PopRemovesOnlyThatHandleAndDoublePopIsNoOp() {
        val s = InputContextStack()
        val a = s.push(vehicle, owner = "a")
        val b = s.push(vehicle, owner = "b")
        assertTrue(s.pop(a))
        assertTrue(s.isActive(vehicle), "still pushed by b")
        assertFalse(s.pop(a))
        assertTrue(s.pop(b))
        assertFalse(s.isActive(vehicle))
    }

    @Test
    fun ContextStack_RevisionChangesOnPushAndPopOnly() {
        val s = InputContextStack()
        val r0 = s.revision
        val h = s.push(vehicle)
        assertEquals(r0 + 1, s.revision)
        s.pop(h); s.pop(h)
        assertEquals(r0 + 2, s.revision)
    }

    @Test
    fun ContextStack_ConsumeBoundHidesOnlyBoundSourcesBelow() {
        val s = InputContextStack()
        s.push(InputContext.GAMEPLAY)
        s.push(vehicle, priority = 5, blocking = ContextBlocking.CONSUME_BOUND)
        val r = s.resolve { if (it == vehicle) setOf("pad:0") else setOf("pad:0", "key:32") }
        assertEquals(emptySet<String>(), r[vehicle])
        assertEquals(setOf("pad:0"), r[InputContext.GAMEPLAY])
    }

    @Test
    fun ContextStack_NoneBlockingPassesEverythingThrough() {
        val r = InputContextStack.withDefaults().resolve { setOf("pad:0") }
        assertEquals(emptySet<String>(), r[InputContext.GAMEPLAY], "ui must not hide gameplay sources")
        assertEquals(listOf(InputContext.UI, InputContext.GAMEPLAY), r.keys.toList())
    }

    @Test
    fun ContextStack_BaseUiSitsAboveGameContexts() {
        val s = InputContextStack.withDefaults()
        s.push(vehicle, priority = 10)

        assertEquals(listOf(InputContext.UI, vehicle, InputContext.GAMEPLAY), s.entries().map { it.context })
        val r = s.resolve { if (it == vehicle) setOf("pad:0") else emptySet() }
        assertEquals(emptySet<String>(), r[InputContext.UI], "a game context never hides ui sources")
        assertEquals(setOf("pad:0"), r[InputContext.GAMEPLAY])
    }

    @Test
    fun ContextStack_AllBlockingSilencesLowerContexts() {
        val s = InputContextStack.withDefaults()
        // A modal goes above the base ui entry to silence everything, ui included.
        val h = s.push(menu, priority = InputContextStack.UI_PRIORITY + 100, blocking = ContextBlocking.ALL)
        assertTrue(s.isActive(menu))
        assertFalse(s.isActive(InputContext.GAMEPLAY))
        assertEquals(setOf(menu), s.resolve { emptySet() }.keys)
        s.pop(h)
        assertTrue(s.isActive(InputContext.GAMEPLAY))
    }

    @Test
    fun ContextStack_DuplicateContextUsesItsTopmostEntry() {
        val s = InputContextStack()
        s.push(InputContext.GAMEPLAY, priority = 0)
        s.push(vehicle, priority = 5)
        s.push(InputContext.GAMEPLAY, priority = 10)
        val r = s.resolve { if (it == vehicle) setOf("pad:0") else emptySet() }
        assertEquals(emptySet<String>(), r[InputContext.GAMEPLAY])
    }

    @Test
    fun InputContext_IsOpenAndBuiltInsSortFirst() {
        assertEquals("gameplay", InputContext.GAMEPLAY.id)
        assertEquals(InputContext("ui"), InputContext.UI)
        assertTrue(InputContext.EDITOR.displayOrder < vehicle.displayOrder)
        assertEquals(0, InputContext.GAMEPLAY.displayOrder)
    }
}
