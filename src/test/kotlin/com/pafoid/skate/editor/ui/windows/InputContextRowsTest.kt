package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.engine.input.ContextBlocking
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.InputContextStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InputContextRowsTest {

    @Test
    fun InputContextRows_ListTopFirstAndGreyOutEntriesUnderAModalOne() {
        val stack = InputContextStack.withDefaults()
        stack.push(InputContext("skate"), priority = 10, owner = "character:1")
        stack.push(InputContext.UI, priority = 1_000, blocking = ContextBlocking.ALL, owner = "pause-menu")

        val rows = InputContextRows.of(stack)

        assertEquals(listOf("ui", "ui", "skate", "gameplay"), rows.map { it.context.id })
        assertEquals(listOf(false, true, true, true), rows.map { it.silenced })
        assertEquals("pause-menu", rows.first().owner)
        assertEquals(ContextBlocking.CONSUME_BOUND, rows[2].blocking)
    }

    @Test
    fun InputContextRows_BaseStackIsAllLive() {
        val rows = InputContextRows.of(InputContextStack.withDefaults())
        assertEquals(listOf(false, false), rows.map { it.silenced })
        assertEquals(listOf(ContextBlocking.NONE, ContextBlocking.CONSUME_BOUND), rows.map { it.blocking })
    }
}
