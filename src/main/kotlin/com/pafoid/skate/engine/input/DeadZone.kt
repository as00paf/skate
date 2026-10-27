package com.pafoid.skate.engine.input

import kotlinx.serialization.Serializable
import org.joml.Vector2f
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

enum class DeadZoneMode {
    /** Each axis is filtered independently (square zone). */
    AXIAL,

    /** The stick vector magnitude is filtered (circular zone); direction is preserved. */
    RADIAL
}

/** Pure dead zone and response curve math. Magnitudes are in 0..1. */
object DeadZoneMath {
    private const val MIN_SPAN = 0.01f

    /**
     * Maps [magnitude] through an inner dead zone (below -> 0), an outer dead zone (above `1 - outer` -> 1),
     * rescales the live range to 0..1 and applies `pow(exponent)`.
     */
    fun rescale(magnitude: Float, inner: Float, outer: Float, exponent: Float): Float {
        val lo = inner.coerceIn(0f, 0.98f)
        val hi = (1f - outer.coerceIn(0f, 0.98f)).coerceAtLeast(lo + MIN_SPAN)
        if (magnitude <= lo) return 0f
        if (magnitude >= hi) return 1f
        val t = (magnitude - lo) / (hi - lo)
        return t.pow(exponent.coerceIn(0.1f, 8f))
    }

    fun axial(x: Float, y: Float, inner: Float, outer: Float, exponent: Float, out: Vector2f): Vector2f =
        out.set(signed(x, inner, outer, exponent), signed(y, inner, outer, exponent))

    private fun signed(v: Float, inner: Float, outer: Float, exponent: Float): Float {
        val r = rescale(abs(v), inner, outer, exponent)
        return if (r == 0f) 0f else sign(v) * r
    }

    fun radial(x: Float, y: Float, inner: Float, outer: Float, exponent: Float, out: Vector2f): Vector2f {
        val m = sqrt(x * x + y * y)
        if (m <= 0f) return out.set(0f, 0f)
        val scale = rescale(m, inner, outer, exponent) / m
        return out.set(x * scale, y * scale)
    }
}

@Serializable
data class StickDeadZone(
    var mode: DeadZoneMode = DeadZoneMode.RADIAL,
    /** Inner dead zone radius/threshold, 0..1. */
    var inner: Float = 0.15f,
    /** Outer dead zone: input beyond `1 - outer` reads as full deflection. */
    var outer: Float = 0.02f,
    /** Response curve exponent: 1 = linear, >1 = finer control near the centre. */
    var curve: Float = 1f
) {
    fun apply(x: Float, y: Float, out: Vector2f): Vector2f = when (mode) {
        DeadZoneMode.AXIAL -> DeadZoneMath.axial(x, y, inner, outer, curve, out)
        DeadZoneMode.RADIAL -> DeadZoneMath.radial(x, y, inner, outer, curve, out)
    }

    fun validate() {
        inner = inner.coerceIn(0f, 0.9f)
        outer = outer.coerceIn(0f, 0.5f)
        curve = curve.coerceIn(0.1f, 8f)
    }
}

@Serializable
data class TriggerDeadZone(
    var inner: Float = 0.05f,
    var outer: Float = 0.02f,
    var curve: Float = 1f,
    /** Processed value at or above which the trigger counts as pressed. */
    var pressThreshold: Float = 0.5f
) {
    fun apply(raw: Float): Float = DeadZoneMath.rescale(raw, inner, outer, curve)

    fun isPressed(raw: Float): Boolean = apply(raw) >= pressThreshold && apply(raw) > 0f

    fun validate() {
        inner = inner.coerceIn(0f, 0.9f)
        outer = outer.coerceIn(0f, 0.5f)
        curve = curve.coerceIn(0.1f, 8f)
        pressThreshold = pressThreshold.coerceIn(0.05f, 1f)
    }
}

@Serializable
data class DeadZoneSettings(
    var leftStick: StickDeadZone = StickDeadZone(inner = 0.15f),
    var rightStick: StickDeadZone = StickDeadZone(inner = 0.1f),
    var leftTrigger: TriggerDeadZone = TriggerDeadZone(),
    var rightTrigger: TriggerDeadZone = TriggerDeadZone()
) {
    fun validate() {
        leftStick.validate(); rightStick.validate()
        leftTrigger.validate(); rightTrigger.validate()
    }
}
