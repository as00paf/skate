package com.pafoid.skate.editor.ui.windows

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-52F: the Search Anywhere palette's own commands (Test Input) and when the palette closes by itself. */
class PaletteCommandsTest {

    private val english = mapOf(
        "search.command.test_input" to "Test Input...",
        "search.command.test_input.keywords" to "test input keyboard mouse gamepad controller devices",
    )
    private val french = mapOf(
        "search.command.test_input" to "Tester les entrées...",
        "search.command.test_input.keywords" to "tester test entrées input clavier souris manette gamepad périphériques",
    )

    private fun match(query: String, table: Map<String, String> = english) =
        PaletteCommands.matching(query) { table[it] ?: it }

    @Test
    fun TestInput_IsFoundByItsLabelAndKeywords_InEveryWord() {
        assertEquals(listOf(PaletteCommands.TEST_INPUT), match("test input"))
        assertEquals(listOf(PaletteCommands.TEST_INPUT), match("  GamePad "))
        assertEquals(listOf(PaletteCommands.TEST_INPUT), match("manette", french))
        assertEquals(listOf(PaletteCommands.TEST_INPUT), match("input", french))
        assertEquals(emptyList<PaletteCommand>(), match("test scene"))
        assertEquals(emptyList<PaletteCommand>(), match("   "))
    }

    @Test
    fun TestInput_OpensTheInputTest_AndIsAnAction() {
        assertSame(PaletteCommands.TEST_INPUT, PaletteCommands.byId(PaletteCommands.TEST_INPUT.id))
        assertNull(PaletteCommands.byId("gameobject:42"))
        assertEquals(PaletteCommands.TEST_INPUT_WINDOW, PaletteCommands.TEST_INPUT.window)
    }

    @Test
    fun Palette_ClosesWhenItLosesFocus_NotOnItsFirstFrameOrOverItsPopups() {
        assertFalse(PalettePopup.closesOnFocusLoss(framesOpen = 0, focused = false, popupOpen = false))
        assertFalse(PalettePopup.closesOnFocusLoss(framesOpen = 5, focused = true, popupOpen = false))
        assertFalse(PalettePopup.closesOnFocusLoss(framesOpen = 5, focused = false, popupOpen = true))
        assertTrue(PalettePopup.closesOnFocusLoss(framesOpen = 5, focused = false, popupOpen = false))
    }
}
