package com.pafoid.skate.engine.input

/** Collects typed characters and key repeats between frames, free of GLFW so it can be unit tested. */
class TextInputBuffer(private val maxPending: Int = 256) {
    private val typed = StringBuilder()
    private val repeated = LinkedHashSet<Int>()

    /** Appends a Unicode code point; control characters and invalid code points are ignored. */
    @Synchronized
    fun appendCodePoint(codePoint: Int) {
        if (!Character.isValidCodePoint(codePoint) || Character.isISOControl(codePoint)) return
        if (typed.length >= maxPending) return
        typed.appendCodePoint(codePoint)
    }

    @Synchronized
    fun keyRepeated(key: Int) {
        repeated.add(key)
    }

    /** Returns text typed since the last call and clears it. */
    @Synchronized
    fun consumeText(): String {
        val s = typed.toString()
        typed.setLength(0)
        return s
    }

    /** True if [key] auto-repeated since the last [endFrame] (use for backspace/arrows in text fields). */
    @Synchronized
    fun wasRepeated(key: Int): Boolean = key in repeated

    @Synchronized
    fun endFrame() = repeated.clear()
}
