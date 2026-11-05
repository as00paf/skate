package com.pafoid.skate.editor.imgui

/**
 * A short message in the status bar (EDT-42), e.g. where a screenshot was saved. Safe to set from any thread; the
 * status bar shows the latest one for [DURATION_MS].
 */
object EditorToast {
    const val DURATION_MS = 6000L

    private class Entry(val text: String, val at: Long)

    @Volatile
    private var latest: Entry? = null

    fun show(text: String, now: Long = System.currentTimeMillis()) {
        latest = Entry(text, now)
    }

    /** The message to show at [now], or null once it has expired. */
    fun current(now: Long = System.currentTimeMillis()): String? = latest?.takeIf { now - it.at < DURATION_MS }?.text
}
