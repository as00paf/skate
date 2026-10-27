package com.pafoid.skate.engine.ui

import kotlin.math.abs
import kotlin.math.roundToInt

enum class UiDirection { UP, DOWN, LEFT, RIGHT }

class UiFocusable(val id: Int, val rect: UiRectF)

/** Pure focus-graph navigation over resolved rects. */
object UiFocusNavigator {
    /**
     * Next focus target when moving [dir] from [current]: the nearest candidate whose center lies strictly in that
     * direction, with off-axis distance weighted double so movement prefers staying in the same row/column.
     * With no current focus the top-left-most candidate is returned.
     */
    fun navigate(current: UiFocusable?, candidates: List<UiFocusable>, dir: UiDirection): Int? {
        if (candidates.isEmpty()) return null
        if (current == null) return candidates.minWithOrNull(compareBy({ it.rect.centerY }, { it.rect.centerX }))?.id
        val cx = current.rect.centerX
        val cy = current.rect.centerY
        var best: UiFocusable? = null
        var bestScore = Float.MAX_VALUE
        for (c in candidates) {
            if (c.id == current.id) continue
            val dx = c.rect.centerX - cx
            val dy = c.rect.centerY - cy
            val (primary, off) = when (dir) {
                UiDirection.UP -> -dy to abs(dx)
                UiDirection.DOWN -> dy to abs(dx)
                UiDirection.LEFT -> -dx to abs(dy)
                UiDirection.RIGHT -> dx to abs(dy)
            }
            if (primary <= 0f) continue
            val score = primary + 2f * off
            if (score < bestScore) {
                bestScore = score
                best = c
            }
        }
        return best?.id
    }

    /** Tab-style cycling in reading order (top-to-bottom, then left-to-right), wrapping around. */
    fun cycle(currentId: Int?, candidates: List<UiFocusable>, forward: Boolean): Int? {
        if (candidates.isEmpty()) return null
        val ordered = candidates.sortedWith(compareBy({ it.rect.y }, { it.rect.x }))
        val index = ordered.indexOfFirst { it.id == currentId }
        if (index < 0) return (if (forward) ordered.first() else ordered.last()).id
        val step = if (forward) 1 else -1
        return ordered[(index + step + ordered.size) % ordered.size].id
    }
}

object UiWidgetMath {
    fun fraction(value: Float, min: Float, max: Float): Float =
        if (max <= min) 0f else ((value - min) / (max - min)).coerceIn(0f, 1f)

    /** Value for a 0..1 [fraction] of the track, snapped to [step] (when positive) and clamped. */
    fun valueFromFraction(fraction: Float, min: Float, max: Float, step: Float): Float {
        val raw = min + fraction.coerceIn(0f, 1f) * (max - min)
        return snap(raw, min, max, step)
    }

    fun snap(value: Float, min: Float, max: Float, step: Float): Float {
        val snapped = if (step > 0f) min + ((value - min) / step).roundToInt() * step else value
        return snapped.coerceIn(min, max)
    }

    /** Fraction of the pointer along a track starting at [start] with length [length]. */
    fun trackFraction(pointer: Float, start: Float, length: Float): Float =
        if (length <= 0f) 0f else ((pointer - start) / length).coerceIn(0f, 1f)

    /** Indices of the list items at least partly visible at [scroll] (canvas units). */
    fun visibleItems(itemCount: Int, itemHeight: Float, viewportHeight: Float, scroll: Float): IntRange {
        if (itemCount <= 0 || itemHeight <= 0f) return IntRange.EMPTY
        val first = (scroll / itemHeight).toInt().coerceIn(0, itemCount - 1)
        val last = (((scroll + viewportHeight) / itemHeight).toInt()).coerceIn(first, itemCount - 1)
        return first..last
    }

    /** Scroll offset that brings item [index] fully into view, clamped to the content. */
    fun scrollToItem(index: Int, itemCount: Int, itemHeight: Float, viewportHeight: Float, scroll: Float): Float {
        val maxScroll = (itemCount * itemHeight - viewportHeight).coerceAtLeast(0f)
        val top = index * itemHeight
        val bottom = top + itemHeight
        val target = when {
            top < scroll -> top
            bottom > scroll + viewportHeight -> bottom - viewportHeight
            else -> scroll
        }
        return target.coerceIn(0f, maxScroll)
    }
}
