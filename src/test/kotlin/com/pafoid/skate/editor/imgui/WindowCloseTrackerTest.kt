package com.pafoid.skate.editor.imgui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** EDT-40: windows learn they were closed by any path (X, View menu, Hide All), exactly once. */
class WindowCloseTrackerTest {
    private class Probe(name: String) : EditorWindow(name) {
        var closed = 0
        override fun imgui() {}
        override fun onClosed() { closed++ }
    }

    @Test
    fun OpenThenClosed_NotifiesOnce() {
        val a = Probe("a")
        val tracker = WindowCloseTracker()
        tracker.update(listOf(a))
        a.isOpen.set(true); tracker.update(listOf(a))
        a.isOpen.set(false); tracker.update(listOf(a)); tracker.update(listOf(a))
        assertEquals(1, a.closed)
    }

    @Test
    fun NeverOpened_IsNotNotified() {
        val a = Probe("a")
        val tracker = WindowCloseTracker()
        repeat(3) { tracker.update(listOf(a)) }
        assertEquals(0, a.closed)
    }
}
