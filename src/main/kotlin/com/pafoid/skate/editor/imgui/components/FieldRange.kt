package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.engine.ecs.components.Range
import java.lang.reflect.Field

/** A field's [Range] metadata, free of ImGui so it can be tested; [NONE] leaves a drag unbounded at [defaultStep]. */
class FieldRange(val min: Double, val max: Double, val step: Double) {
    val bounded get() = min > -Double.MAX_VALUE || max < Double.MAX_VALUE

    fun clamp(v: Float): Float = v.coerceIn(min.coerceAtLeast(-Float.MAX_VALUE.toDouble()).toFloat(), max.coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat())
    fun clamp(v: Int): Int = v.coerceIn(min.coerceAtLeast(Int.MIN_VALUE.toDouble()).toInt(), max.coerceAtMost(Int.MAX_VALUE.toDouble()).toInt())
    fun clamp(v: Long): Long = v.coerceIn(min.coerceAtLeast(Long.MIN_VALUE.toDouble()).toLong(), max.coerceAtMost(Long.MAX_VALUE.toDouble()).toLong())
    fun clamp(v: Double): Double = v.coerceIn(min, max)

    fun floatSpeed(default: Float) = if (step > 0.0) step.toFloat() else default

    companion object {
        val NONE = FieldRange(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0)

        fun of(field: Field?): FieldRange =
            field?.getAnnotation(Range::class.java)?.let { FieldRange(it.min, it.max, it.step) } ?: NONE
    }
}
