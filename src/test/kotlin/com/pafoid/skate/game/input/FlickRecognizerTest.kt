package com.pafoid.skate.game.input

import com.pafoid.skate.engine.input.InputHistory
import org.joml.Vector2f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

class FlickRecognizerTest {

    private val recognizer = FlickRecognizer()
    private val dt = 1f / 60f

    /** [n] samples from [from] to [to], one per frame. */
    private fun line(from: Vector2f, to: Vector2f, n: Int) = List(n) { i -> Vector2f(from).lerp(to, i / (n - 1f)) }

    /** [n] samples on the rim from [fromDeg] to [toDeg] (math angles: 0 = right, 90 = up). */
    private fun arc(fromDeg: Float, toDeg: Float, n: Int) = List(n) { i ->
        val a = Math.toRadians((fromDeg + (toDeg - fromDeg) * i / (n - 1f)).toDouble())
        Vector2f(cos(a).toFloat(), sin(a).toFloat())
    }

    private fun times(n: Int) = List(n) { it * dt }

    private fun recognize(points: List<Vector2f>) = recognizer.recognize(times(points.size), points)

    @Test
    fun fastStraightFlickUp_IsALineInSectorZero() {
        val flick = requireNotNull(recognize(line(Vector2f(), Vector2f(0f, 1f), 6)))

        assertEquals(0, flick.sector)
        assertEquals(FlickShape.LINE, flick.shape)
        assertEquals(1f, flick.direction.y, 1e-4f)
        assertTrue(flick.speed > recognizer.settings.minSpeed)
    }

    @Test
    fun sectorsAreCountedClockwiseFromUp() {
        assertEquals(listOf(0, 1, 2, 4, 6, 7), listOf(0f to 1f, 1f to 1f, 1f to 0f, 0f to -1f, -1f to 0f, -1f to 1f).map { (x, y) -> recognizer.sector(x, y) })
    }

    @Test
    fun slowMoveOrNotReachingTheEdge_IsNoFlick() {
        // 0.25 s window: the last 15 frames of a 1 s push cover too little travel.
        assertNull(recognize(line(Vector2f(), Vector2f(0f, 1f), 60)))
        assertNull(recognize(line(Vector2f(), Vector2f(0f, 0.5f), 6)))
        assertNull(recognize(listOf(Vector2f(0f, 1f))))
    }

    @Test
    fun quarterCircle_FromDownToRight_TurnsCounterClockwise() {
        val flick = requireNotNull(recognize(arc(-90f, 0f, 8)))

        assertEquals(FlickShape.QUARTER_CIRCLE, flick.shape)
        assertEquals(90f, flick.sweepDeg, 1f)
        assertEquals(2, flick.sector)
    }

    @Test
    fun halfCircle_FromLeftUnderToRight_AndClockwiseIsNegative() {
        val under = requireNotNull(recognize(arc(180f, 360f, 12)))
        assertEquals(FlickShape.HALF_CIRCLE, under.shape)
        assertEquals(180f, under.sweepDeg, 1f)

        val over = requireNotNull(recognize(arc(180f, 0f, 12)))
        assertEquals(FlickShape.HALF_CIRCLE, over.shape)
        assertEquals(-180f, over.sweepDeg, 1f)
    }

    @Test
    fun samplesNearTheCentreDoNotAddToTheTurn() {
        // Through the centre from down to up: a line, though the raw angle flips by 180 degrees.
        val flick = requireNotNull(recognize(line(Vector2f(0f, -1f), Vector2f(0f, 1f), 9)))

        assertEquals(FlickShape.LINE, flick.shape)
    }

    @Test
    fun readsATrackedSourceOfInputHistory() {
        val history = InputHistory()
        val points = arc(-90f, 0f, 8)
        var i = 0
        history.track("stick", { points[i].x }, { points[i].y })
        points.indices.forEach { i = it; history.sample(it * dt) }

        val flick = requireNotNull(recognizer.recognize(history, "stick"))

        assertEquals(FlickShape.QUARTER_CIRCLE, flick.shape)
        assertNull(recognizer.recognize(history, "unknown"))
    }
}
