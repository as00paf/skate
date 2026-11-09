package com.pafoid.skate.game.input

import com.pafoid.skate.engine.input.InputHistory
import kotlinx.serialization.Serializable
import org.joml.Vector2f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Path of a stick gesture, by how far it turned around the centre. */
enum class FlickShape { LINE, QUARTER_CIRCLE, HALF_CIRCLE }

/**
 * A recognized stick gesture.
 *
 * @param sector where the stick ended, 0 = up (+Y) then clockwise, of [FlickSettings.sectors]
 * @param direction unit vector toward where the stick ended
 * @param speed travel of the stick over the gesture divided by its duration (stick units per second)
 * @param sweepDeg signed turn around the centre in degrees, + counter-clockwise
 */
data class Flick(val sector: Int, val direction: Vector2f, val speed: Float, val shape: FlickShape, val sweepDeg: Float)

/** Tuning of [FlickRecognizer]; serializable so control schemes (GPL-21) can keep it in their config. */
@Serializable
data class FlickSettings(
    /** Seconds of history a gesture may span. */
    val windowSec: Float = 0.25f,
    /** The stick must end at least this far from the centre (0..1). */
    val endRadius: Float = 0.8f,
    /** Samples closer to the centre than this do not count toward the turn (their angle is noise) and break it. */
    val deadZone: Float = 0.25f,
    /** Minimum path length (stick units) over the window. */
    val minTravel: Float = 0.6f,
    /** Minimum path length per second. */
    val minSpeed: Float = 3f,
    val sectors: Int = 8,
    /** Turns of at least this many degrees are a quarter circle. */
    val quarterCircleDeg: Float = 60f,
    /** Turns of at least this many degrees are a half circle. */
    val halfCircleDeg: Float = 135f,
    /**
     * A turn of more than this between two samples is a move straight across (keys jumping from K to I+J, or a stick
     * snapped over), not a circle: the turn starts over there.
     */
    val maxStepDeg: Float = 100f,
)

/**
 * Turns the recent samples of a 2D source (a stick tracked in [InputHistory]) into a [Flick] (INP-10 §7, for the
 * flick-it control scheme of GPL-21). A flick is a fast stick move that ends near the edge; its shape is how far the
 * stick turned around the centre on the way. Pure; keeps its sample buffers between calls (no per-call allocation
 * once they are large enough). Not thread safe.
 */
class FlickRecognizer(var settings: FlickSettings = FlickSettings()) {

    private var t = FloatArray(64)
    private var x = FloatArray(64)
    private var y = FloatArray(64)
    private var count = 0

    /** The gesture of [source] over the last [FlickSettings.windowSec], or null when there is none. */
    fun recognize(history: InputHistory, source: String): Flick? {
        count = 0
        history.forEachSample(source, settings.windowSec) { time, sx, sy -> add(time, sx, sy) }
        return recognizeBuffered()
    }

    /** Same as [recognize] for explicit samples (oldest first): [times] in seconds, [points] as (x, y). */
    fun recognize(times: List<Float>, points: List<Vector2f>): Flick? {
        require(times.size == points.size) { "one time per point" }
        count = 0
        val end = times.lastOrNull() ?: return null
        times.indices.filter { end - times[it] <= settings.windowSec }.forEach { add(times[it], points[it].x, points[it].y) }
        return recognizeBuffered()
    }

    private fun add(time: Float, sx: Float, sy: Float) {
        if (count == t.size) {
            t = t.copyOf(count * 2); x = x.copyOf(count * 2); y = y.copyOf(count * 2)
        }
        t[count] = time; x[count] = sx; y[count] = sy
        count++
    }

    private fun recognizeBuffered(): Flick? {
        val s = settings
        if (count < 2) return null
        val last = count - 1
        val endRadius = length(x[last], y[last])
        if (endRadius < s.endRadius) return null
        val duration = t[last] - t[0]
        if (duration <= 0f) return null

        var travel = 0f
        var sweep = 0.0
        var previousAngle = Double.NaN
        for (i in 0 until count) {
            if (i > 0) travel += length(x[i] - x[i - 1], y[i] - y[i - 1])
            if (length(x[i], y[i]) < s.deadZone) {
                // Through the centre the angle jumps; the turn starts over on the other side.
                previousAngle = Double.NaN
                continue
            }
            val angle = atan2(y[i].toDouble(), x[i].toDouble())
            if (!previousAngle.isNaN()) {
                val step = wrap(angle - previousAngle)
                if (abs(Math.toDegrees(step)) > s.maxStepDeg) sweep = 0.0 else sweep += step
            }
            previousAngle = angle
        }
        val speed = travel / duration
        if (travel < s.minTravel || speed < s.minSpeed) return null

        val sweepDeg = Math.toDegrees(sweep).toFloat()
        val shape = when {
            abs(sweepDeg) >= s.halfCircleDeg -> FlickShape.HALF_CIRCLE
            abs(sweepDeg) >= s.quarterCircleDeg -> FlickShape.QUARTER_CIRCLE
            else -> FlickShape.LINE
        }
        return Flick(sector(x[last], y[last]), Vector2f(x[last], y[last]).div(endRadius), speed, shape, sweepDeg)
    }

    /** Sector of a direction: 0 = +Y (up), counted clockwise. */
    fun sector(dx: Float, dy: Float): Int {
        val n = settings.sectors.coerceAtLeast(1)
        val clockwiseFromUp = atan2(dx.toDouble(), dy.toDouble())
        return (clockwiseFromUp / (2 * PI / n)).roundToInt().mod(n)
    }

    private fun length(a: Float, b: Float) = sqrt(a * a + b * b)

    private fun wrap(a: Double): Double {
        var v = a
        while (v > PI) v -= 2 * PI
        while (v <= -PI) v += 2 * PI
        return v
    }
}
