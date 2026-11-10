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
    fun Row_KeepsTheButtonClearOfTheClipTop() {
        // EDT-72: the header starts right at the clip top (no window padding): the row moves down to clear it.
        val row = HelpButtonLayout.row(startX = 100f, startY = 50f, contentMaxX = 500f, clipTop = 50f, buttonWidth = 24f, itemSpacingX = 8f)
        assertEquals(50f + HelpButtonLayout.MIN_CLIP_CLEARANCE, row.top)
        // With the theme's 8 px window padding the row stays where the cursor is.
        assertEquals(58f, HelpButtonLayout.row(100f, 58f, 500f, 50f, 24f, 8f).top)
    }

    @Test
    fun Row_IsOnWholePixelsAt150Percent() {
        // 150 %: a 19.5 px line height leaves fractional cursor and clip positions.
        val row = HelpButtonLayout.row(startX = 100.5f, startY = 102.5f, contentMaxX = 592.25f, clipTop = 101f, buttonWidth = 29f, itemSpacingX = 8f)
        assertEquals(103f, row.top)
        assertEquals(563f, row.buttonX)
        assertTrue(row.buttonX + 29f <= 592.25f)
        assertTrue(row.top >= 101f + HelpButtonLayout.MIN_CLIP_CLEARANCE)
    }

    @Test
    fun Row_ToolsGetTheWidthLeftOfTheButton() {
        val row = HelpButtonLayout.row(startX = 400f, startY = 60f, contentMaxX = 800f, clipTop = 50f, buttonWidth = 24f, itemSpacingX = 8f)
        assertEquals(776f, row.buttonX)
        assertEquals(368f, row.toolsWidth) // 776 - 8 - 400
        // Narrower than the button: the button stays at the start, the tools get nothing.
        val narrow = HelpButtonLayout.row(400f, 60f, 410f, 50f, 24f, 8f)
        assertEquals(400f, narrow.buttonX)
        assertEquals(0f, narrow.toolsWidth)
    }

    @Test
    fun NextLine_IsBelowTheTallerOfToolsAndButton() {
        assertEquals(80f, HelpButtonLayout.nextLineY(rowTop = 52f, buttonHeight = 24f, toolsNextLineY = 70f, itemSpacingY = 4f))
        assertEquals(90f, HelpButtonLayout.nextLineY(rowTop = 52f, buttonHeight = 24f, toolsNextLineY = 90f, itemSpacingY = 4f))
    }

    @Test
    fun TrailingWidth_CountsEachButtonWithPaddingAndSpacing() {
        // Hierarchy: search box, then [+] and [link] buttons (14 px icons, padding 5, spacing 8): 2 × (14 + 10 + 8).
        assertEquals(64f, HelpButtonLayout.trailingWidth(listOf(14f, 14f), framePaddingX = 5f, itemSpacingX = 8f))
        assertEquals(0f, HelpButtonLayout.trailingWidth(emptyList(), framePaddingX = 5f, itemSpacingX = 8f))
    }
}
