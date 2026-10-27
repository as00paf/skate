package com.pafoid.skate.engine.render.ui

import com.pafoid.skate.engine.assets.data.Texture

/** Reference usage: a 9-slice panel whose text is clipped to the panel interior. */
fun UiDrawList.drawClippedPanelWithText(
    panel: Texture, insets: NineSliceInsets, font: GridFont, text: String,
    x: Int, y: Int, w: Int, h: Int, cellW: Float, cellH: Float,
) {
    drawNineSlice(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), panel, insets)
    pushScissor(x + insets.left, y + insets.top, w - insets.left - insets.right, h - insets.top - insets.bottom)
    drawText(font, text, (x + insets.left).toFloat(), (y + insets.top).toFloat(), cellW, cellH)
    popScissor()
}
