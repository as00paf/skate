package com.pafoid.skate.engine.environment

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/** REN-29 §5.1: the wind function `wind.glsl` mirrors; the table pins it so the GLSL copy can be checked against it. */
class WindFieldTest {
    private val eps = 1e-4f

    @Test
    fun WindField_Table_PinsTheFormula() {
        val breeze = WindField(directionDeg = 45f, speed = 3f, gustStrength = 0.4f, gustScale = 20f)
        val out = Vector3f()
        // (x, z, t) -> gust, sample.x, sample.z
        val table = listOf(
            floatArrayOf(0f, 0f, 0f, 0.119277f, 2.22253f, 2.22253f),
            floatArrayOf(3.5f, -2f, 1.25f, -0.445801f, 1.743046f, 1.743046f),
            floatArrayOf(-10f, 7f, 4f, 0.360155f, 2.426922f, 2.426922f),
        )
        for (row in table) {
            assertEquals(row[3], breeze.gust(row[0], row[1], row[2]), eps)
            breeze.sample(row[0], row[1], row[2], out)
            assertEquals(row[4], out.x, eps)
            assertEquals(0f, out.y)
            assertEquals(row[5], out.z, eps)
        }
        val east = WindField(directionDeg = 90f, speed = 8f, gustStrength = 0.6f, gustScale = 12f)
        assertEquals(-0.130996f, east.gust(5f, 5f, 2.5f), eps)
        east.sample(5f, 5f, 2.5f, out)
        assertEquals(7.371217f, out.x, eps)
        assertEquals(0f, out.z, eps)
    }

    @Test
    fun WindField_IsDeterministic() {
        val a = WindField().sample(12.3f, -4.5f, 6.7f, Vector3f())
        val b = WindField().sample(12.3f, -4.5f, 6.7f, Vector3f())
        assertEquals(a, b)
    }

    @Test
    fun WindField_GustFrontsTravelAlongTheWindAtWindSpeed() {
        val wind = WindField(directionDeg = 90f, speed = 5f, gustScale = 20f)
        val t = 2f
        // After t seconds the same gust value sits speed * t metres downwind (+X)
        for (x in listOf(-7f, 0f, 3f, 11f)) {
            assertEquals(wind.gust(x, 4f, 0f), wind.gust(x + 5f * t, 4f, t), eps)
        }
        assertNotEquals(wind.gust(0f, 4f, 0f), wind.gust(0f, 4f, t))
    }

    @Test
    fun WindField_ZeroSpeed_GivesNoWind() {
        val calm = WindField(speed = 0f, gustStrength = 1f)
        assertEquals(Vector3f(), calm.sample(3f, 4f, 5f, Vector3f(1f, 1f, 1f)))
    }

    @Test
    fun WindField_DirectionIsTheYawTheWindBlowsTowards() {
        val north = WindField(directionDeg = 0f)
        assertEquals(0f, north.dirX, eps); assertEquals(1f, north.dirZ, eps)
        val east = WindField(directionDeg = 90f)
        assertEquals(1f, east.dirX, eps); assertEquals(0f, east.dirZ, eps)
    }

    @Test
    fun WindField_NoGustScale_MeansSteadyWind() {
        val steady = WindField(directionDeg = 0f, speed = 4f, gustStrength = 1f, gustScale = 0f)
        assertEquals(4f, steady.sample(1f, 2f, 3f, Vector3f()).z, eps)
    }
}
