package com.pafoid.skate.editor.ui.windows.assetBrowser

/**
 * Thumbnails that cost a GPU render each (prefab models): rendered once per key, at most [perFrame] per frame and only
 * once their inputs are loaded, so opening the Prefabs tab spreads the work over frames instead of freezing (AST-23).
 * Main thread only.
 */
class BudgetedThumbnails<T : Any>(private val perFrame: Int = 1) {
    private val done = HashMap<String, T>()
    private var budget = perFrame

    val size: Int get() = done.size

    /** Call at the start of each frame the tab is drawn. */
    fun beginFrame() {
        budget = perFrame
    }

    /** The thumbnail for [key]; renders it with [render] when [ready] and the frame has budget left, else null. */
    fun get(key: String, ready: () -> Boolean, render: () -> T): T? {
        done[key]?.let { return it }
        if (budget <= 0 || !ready()) return null
        budget--
        return render().also { done[key] = it }
    }
}
