package com.pafoid.skate.editor.animation.controllereditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ANI-25: the Animator window's sizes come from the font and share the window's width. */
class AnimatorLayoutTest {
    private fun width(s: String) = s.length * 10f

    @Test
    fun Panels_AddUpToTheRow_AndFollowTheFontUntilTheyHitTheirShare() {
        for ((available, font) in listOf(640f to 13f, 640f to 26f, 1600f to 13f, 1600f to 26f)) {
            val w = AnimatorLayout.panelWidths(available, font, 8f)
            assertEquals(available, w.left + w.canvas + w.right + 16f, 0.01f)
            assertTrue(w.left <= available * AnimatorLayout.LEFT_SHARE + 0.01f && w.right <= available * AnimatorLayout.RIGHT_SHARE + 0.01f)
            assertTrue(w.canvas >= available * (1f - AnimatorLayout.LEFT_SHARE - AnimatorLayout.RIGHT_SHARE) - 16.01f, "the graph keeps the rest")
        }
        assertEquals(13f * AnimatorLayout.LEFT_EMS, AnimatorLayout.panelWidths(1600f, 13f, 8f).left, "wide window: sized from the font")
    }

    @Test
    fun ButtonLabels_WrapAtSpacesOnlyWhenTooWide() {
        assertEquals("Delete transition", AnimatorLayout.wrapWords("Delete transition", 200f, ::width))
        assertEquals("Delete\ntransition", AnimatorLayout.wrapWords("Delete transition", 120f, ::width))
        assertEquals("+ Make a\nproject\ncopy", AnimatorLayout.wrapWords("+ Make a project copy", 90f, ::width))
        assertEquals("Supercalifragilistic", AnimatorLayout.wrapWords("Supercalifragilistic", 50f, ::width), "a single word stays whole")
        assertEquals(30f, AnimatorLayout.share(30f, 60f))
        assertEquals(60f, AnimatorLayout.share(300f, 60f))
    }
}
