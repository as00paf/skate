package com.pafoid.skate.editor.ui.windows.assetBrowser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BudgetedThumbnailsTest {
    private var renders = 0
    private val thumbs = BudgetedThumbnails<String>(perFrame = 1)

    private fun get(key: String, ready: Boolean = true) = thumbs.get(key, { ready }) { renders++; "thumb:$key" }

    @Test
    fun RendersAtMostOnePerFrame() {
        thumbs.beginFrame()
        assertEquals("thumb:a", get("a"))
        assertNull(get("b"), "budget spent for this frame")

        thumbs.beginFrame()
        assertEquals("thumb:b", get("b"))
        assertEquals(2, renders)
    }

    @Test
    fun RendersOnlyOnce_AndCachedOnesCostNothing() {
        thumbs.beginFrame()
        get("a")
        thumbs.beginFrame()
        assertEquals("thumb:a", get("a"))
        assertEquals("thumb:b", get("b"), "a cached hit leaves the budget")
        assertEquals(2, renders)
    }

    @Test
    fun WaitsUntilInputsAreLoaded() {
        thumbs.beginFrame()
        assertNull(get("a", ready = false))
        assertEquals("thumb:b", get("b"), "an unready tile does not spend the budget")
        assertEquals(1, renders)
    }
}
