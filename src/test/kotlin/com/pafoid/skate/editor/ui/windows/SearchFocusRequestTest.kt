package com.pafoid.skate.editor.ui.windows

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchFocusRequestTest {

    @Test
    fun firstFrameAfterOpen_RequestsFocusOnce() {
        val f = SearchFocusRequest()
        assertTrue(f.beginFrame())
        assertTrue(f.consume())
        assertFalse(f.consume(), "same frame: only once")
    }

    @Test
    fun laterFrames_DoNotStealFocusBack() {
        val f = SearchFocusRequest()
        f.beginFrame(); f.consume()
        repeat(100) {
            assertFalse(f.beginFrame())
            assertFalse(f.consume(), "frame $it")
        }
    }

    @Test
    fun reopenAfterClose_RequestsFocusAgain() {
        val f = SearchFocusRequest()
        f.beginFrame(); f.consume()
        f.onClosed()
        assertTrue(f.beginFrame())
        assertTrue(f.consume())
    }

    @Test
    fun closeBeforeInputRendered_DropsPendingRequest() {
        val f = SearchFocusRequest()
        f.beginFrame()
        f.onClosed()
        assertFalse(f.consume())
    }

    @Test
    fun explicitRequest_FocusesOnNextRenderOnly() {
        val f = SearchFocusRequest()
        f.beginFrame(); f.consume()
        f.request()
        assertFalse(f.beginFrame(), "not a new open")
        assertTrue(f.consume())
        assertFalse(f.consume())
    }
}
