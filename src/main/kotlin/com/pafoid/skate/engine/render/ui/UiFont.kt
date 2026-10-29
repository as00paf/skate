package com.pafoid.skate.engine.render.ui

/** A font the runtime UI can measure and record text with. Sizes are line heights in pixels. */
interface UiFont {
    fun lineHeight(size: Float): Float = size

    /** Width in pixels of [text] at [size]. */
    fun measure(text: String, size: Float): Float

    /** Records [text] with its top-left at ([x], [y]); returns the advance (width) used. */
    fun draw(
        list: UiDrawList, text: String, x: Float, y: Float, size: Float,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f,
    ): Float
}
