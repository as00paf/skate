package com.pafoid.skate.editor.animation.clipeditor

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Time ↔ pixel mapping of the dope sheet and ruler (ANI-14). [scroll] is the time at the left edge, [pxPerSecond]
 * the zoom. Pure, so the window's hit testing is unit-tested.
 */
class TimelineView(var pxPerSecond: Float = 200f, var scroll: Float = 0f) {
    fun timeToX(time: Float, originX: Float) = originX + (time - scroll) * pxPerSecond
    fun xToTime(x: Float, originX: Float) = scroll + (x - originX) / pxPerSecond

    /** Zooms by [factor] keeping the time under [anchorX] in place. */
    fun zoom(factor: Float, anchorX: Float, originX: Float) {
        val anchor = xToTime(anchorX, originX)
        pxPerSecond = (pxPerSecond * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        scroll = anchor - (anchorX - originX) / pxPerSecond
    }

    /** Fits [duration] seconds into [width] pixels. */
    fun fit(duration: Float, width: Float) {
        pxPerSecond = (width / max(duration, 1e-3f) * 0.95f).coerceIn(MIN_ZOOM, MAX_ZOOM)
        scroll = 0f
    }

    /** Keeps the scroll inside the clip (with a small margin on the left). */
    fun clampScroll(duration: Float, width: Float) {
        val visible = width / pxPerSecond
        scroll = scroll.coerceIn(-visible * 0.05f, max(-visible * 0.05f, duration - visible * 0.5f))
    }

    /**
     * Frame numbers for ruler labels between [width] pixels: a step of 1, 2, 5, 10, 20, ... frames so labels are at
     * least [minLabelPx] apart.
     */
    fun rulerFrames(fps: Float, width: Float, minLabelPx: Float = 60f): List<Int> {
        val framePx = pxPerSecond / fps
        var step = 1
        val nice = intArrayOf(1, 2, 5)
        var i = 0
        var scale = 1
        while (step * framePx < minLabelPx) {
            i++
            if (i == nice.size) { i = 0; scale *= 10 }
            step = nice[i] * scale
        }
        val first = (ceil(scroll * fps / step) * step).toInt().coerceAtLeast(0)
        val last = floor((scroll + width / pxPerSecond) * fps).toInt()
        return (first..last step step).toList()
    }

    companion object {
        const val MIN_ZOOM = 5f
        const val MAX_ZOOM = 5000f
    }
}
