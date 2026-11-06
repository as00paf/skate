package com.pafoid.skate.game

/**
 * The exported game renders at its window's framebuffer size, as editor Play renders at the viewport's size (GUI-12).
 * Runtime UI (the pause menu, the HUD) lays out on the engine framebuffer, so this keeps its layout, scale and pointer
 * mapping the same in both hosts. Before, the game kept a 1920x1080 framebuffer and presented it at that size even
 * when fullscreen on another resolution, so the menu covered only part of the screen and clicks missed it.
 */
object GameView {
    /** The new engine framebuffer size, or null to keep [current] (unchanged, or the window is minimised). */
    fun resize(window: Pair<Int, Int>, current: Pair<Int, Int>): Pair<Int, Int>? =
        if (window.first <= 0 || window.second <= 0 || window == current) null else window
}
