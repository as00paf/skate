package com.pafoid.skate.editor.imgui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-67: the `?` background must contain the icon glyph, and the button must stay inside its header column. */
class HelpButtonLayoutTest {

    private fun iconHeight(fontSize: Float) = fontSize * ICON_FONT_SIZE / BASE_FONT_SIZE

    @Test
    fun Size_DefaultTheme_IsAFullFrameThatHoldsTheIcon() {
        // Dark/light theme: 13 px text, frame padding (5, 3); the question-circle glyph is 14 px wide.
        val size = HelpButtonLayout.size(iconWidth = 14f, fontSize = 13f, framePaddingX = 5f, framePaddingY = 3f)
        assertEquals(19f, size.height) // the frame height, not the 13 px text line smallButton used
        assertEquals(24f, size.width)
        assertTrue(size.height >= iconHeight(13f) + 2 * HelpButtonLayout.MIN_ICON_MARGIN_Y)
    }

    @Test
    fun Size_WithoutVerticalPadding_StillHoldsTheTallerIconGlyph() {
        // smallButton's case (no vertical padding): the text line is shorter than the merged icon font.
        val size = HelpButtonLayout.size(iconWidth = 14f, fontSize = 13f, framePaddingX = 0f, framePaddingY = 0f)
        assertEquals(iconHeight(13f) + 2 * HelpButtonLayout.MIN_ICON_MARGIN_Y, size.height, 1e-4f)
        assertEquals(size.height, size.width, 1e-4f) // never narrower than tall
    }

    @Test
    fun Size_ScalesWithDpiAndFontScale() {
        listOf(1f, 1.25f, 1.5f, 2f, 3f).forEach { scale ->
            val font = 13f * scale
            val size = HelpButtonLayout.size(14f * scale, font, 5f * scale, 3f * scale)
            assertTrue(size.height >= iconHeight(font) + 2 * HelpButtonLayout.MIN_ICON_MARGIN_Y, "scale $scale: $size")
            assertTrue(size.width >= 14f * scale + 10f * scale, "scale $scale: $size")
            assertTrue(size.width >= size.height, "scale $scale: $size")
        }
    }

    @Test
    fun Size_HighContrastPadding_UsesTheThemeFrame() {
        val size = HelpButtonLayout.size(iconWidth = 14f, fontSize = 13f, framePaddingX = 6f, framePaddingY = 4f)
        assertEquals(21f, size.height)
        assertEquals(26f, size.width)
    }

    @Test
    fun ColumnWidth_IsTheButtonPlusOneItemSpacing() {
        assertEquals(32f, HelpButtonLayout.columnWidth(buttonWidth = 24f, itemSpacingX = 8f))
    }

    @Test
    fun ButtonX_IsFlushRightInItsCell() {
        // Column of 32 px starting at x = 400: the 24 px button ends exactly at the cell's right edge.
        val x = HelpButtonLayout.buttonX(cellStartX = 400f, cellAvailX = 32f, buttonWidth = 24f)
        assertEquals(408f, x)
        assertEquals(432f, x + 24f)
    }

    @Test
    fun ButtonX_NarrowCell_NeverMovesLeftOfTheCell() {
        assertEquals(400f, HelpButtonLayout.buttonX(cellStartX = 400f, cellAvailX = 10f, buttonWidth = 24f))
        assertEquals(400f, HelpButtonLayout.buttonX(cellStartX = 400f, cellAvailX = 0f, buttonWidth = 24f))
    }

    @Test
    fun TrailingWidth_CountsEachButtonWithPaddingAndSpacing() {
        // Hierarchy: search box, then [+] and [link] buttons (14 px icons, padding 5, spacing 8): 2 × (14 + 10 + 8).
        assertEquals(64f, HelpButtonLayout.trailingWidth(listOf(14f, 14f), framePaddingX = 5f, itemSpacingX = 8f))
        assertEquals(0f, HelpButtonLayout.trailingWidth(emptyList(), framePaddingX = 5f, itemSpacingX = 8f))
    }
}
