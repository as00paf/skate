package com.pafoid.skate.editor.ui.windows

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-77: the seed row of Project Settings › Random fits the control column at any dialog width. */
class SeedRowLayoutTest {

    private val button = 90f
    private val spacing = 8f

    /** Rightmost pixel the row draws on its widest line. */
    private fun rowWidth(l: SeedRowLayout.Layout): Float =
        if (l.buttonOnSameLine) l.fieldWidth + spacing + button else maxOf(l.fieldWidth, button)

    @Test
    fun SeedRow_WideColumn_FieldFillsWhatTheButtonLeaves() {
        val l = SeedRowLayout.compute(available = 500f, buttonWidth = button, spacing = spacing)
        assertTrue(l.buttonOnSameLine)
        assertEquals(500f - button - spacing, l.fieldWidth)
        assertEquals(500f, rowWidth(l))
    }

    @Test
    fun SeedRow_NarrowColumn_WrapsTheButtonBelowAndFieldTakesTheColumn() {
        val l = SeedRowLayout.compute(available = 150f, buttonWidth = button, spacing = spacing)
        assertFalse(l.buttonOnSameLine)
        assertEquals(150f, l.fieldWidth)
    }

    @Test
    fun SeedRow_AtTheThreshold_KeepsTheButtonBeside() {
        val available = SeedRowLayout.MIN_FIELD_WIDTH + button + spacing
        val l = SeedRowLayout.compute(available, button, spacing)
        assertTrue(l.buttonOnSameLine)
        assertEquals(SeedRowLayout.MIN_FIELD_WIDTH, l.fieldWidth)
        assertFalse(SeedRowLayout.compute(available - 1f, button, spacing).buttonOnSameLine)
    }

    @Test
    fun SeedRow_AnyColumnWideEnoughForTheButton_NeverOverflows() {
        var available = button
        while (available <= 2000f) {
            val l = SeedRowLayout.compute(available, button, spacing)
            assertTrue(rowWidth(l) <= available, "row is ${rowWidth(l)} px in a $available px column")
            assertTrue(l.fieldWidth >= 1f, "field width ${l.fieldWidth} at $available px")
            available += 7.5f
        }
    }

    @Test
    fun SeedRow_ZeroOrNegativeColumn_KeepsAPositiveFieldWidth() {
        // ImGui reads 0 as the default width and negatives as right-aligned, both wider than the column.
        assertEquals(1f, SeedRowLayout.compute(0f, button, spacing).fieldWidth)
        assertEquals(1f, SeedRowLayout.compute(-20f, button, spacing).fieldWidth)
    }
}
