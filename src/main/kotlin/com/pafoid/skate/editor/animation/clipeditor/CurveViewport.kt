package com.pafoid.skate.editor.animation.clipeditor

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Value ↔ pixel mapping of the curve editor (ANI-15); time uses the shared [TimelineView]. [center] is the value at
 * the middle of the plot, [pxPerUnit] the vertical zoom (pixels per metre, degree or scale unit). Pure, so the
 * window's hit testing is unit-tested.
 */
class CurveViewport(var center: Float = 0f, var pxPerUnit: Float = 50f) {
    /** Screen y of [value] in a plot whose top is [top] and height [height] (larger values are higher). */
    fun valueToY(value: Float, top: Float, height: Float) = top + height / 2f - (value - center) * pxPerUnit

    fun yToValue(y: Float, top: Float, height: Float) = center + (top + height / 2f - y) / pxPerUnit

    /** Frames [lo, hi] in [height] pixels with a margin; a flat curve gets a ±1 unit window. */
    fun fit(lo: Float, hi: Float, height: Float) {
        val span = if (hi - lo < 1e-3f) 2f else hi - lo
        center = (lo + hi) / 2f
        pxPerUnit = (height * 0.8f / span).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /** Zooms by [factor] keeping the value under [anchorY] in place. */
    fun zoom(factor: Float, anchorY: Float, top: Float, height: Float) {
        val anchor = yToValue(anchorY, top, height)
        pxPerUnit = (pxPerUnit * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        center = anchor - (top + height / 2f - anchorY) / pxPerUnit
    }

    /** Moves the view by [dy] pixels (positive drags the curves down). */
    fun pan(dy: Float) { center += dy / pxPerUnit }

    /** Values for horizontal grid lines: a 1, 2, 5 × 10ⁿ step so lines are at least [minPx] apart. */
    fun gridValues(height: Float, minPx: Float = 30f): List<Float> {
        val raw = minPx / pxPerUnit
        val pow10 = 10.0.pow(floor(log10(raw.toDouble()))).toFloat()
        val step = listOf(1f, 2f, 5f, 10f).map { it * pow10 }.first { it >= raw }
        val half = height / 2f / pxPerUnit
        val first = ceil((center - half) / step).toInt()
        val last = floor((center + half) / step).toInt()
        return (first..last).map { it * step }.map { if (abs(it) < step * 1e-3f) 0f else it }
    }

    companion object {
        const val MIN_ZOOM = 1e-3f
        const val MAX_ZOOM = 1e5f
    }
}
