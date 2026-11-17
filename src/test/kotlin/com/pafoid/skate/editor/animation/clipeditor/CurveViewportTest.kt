package com.pafoid.skate.editor.animation.clipeditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ANI-15: value ↔ pixel mapping of the curve editor. */
class CurveViewportTest {
    @Test
    fun CurveViewport_MapsValuesBothWaysWithLargerValuesHigher() {
        val v = CurveViewport(center = 10f, pxPerUnit = 4f)
        assertEquals(150f, v.valueToY(10f, top = 100f, height = 100f))
        assertTrue(v.valueToY(20f, 100f, 100f) < v.valueToY(0f, 100f, 100f))
        assertEquals(17.5f, v.yToValue(v.valueToY(17.5f, 100f, 100f), 100f, 100f), 1e-4f)
    }

    @Test
    fun CurveViewport_FitsARangeAndZoomsAroundTheMouse() {
        val v = CurveViewport()
        v.fit(-90f, 90f, height = 200f)
        assertEquals(0f, v.center)
        assertTrue(v.valueToY(90f, 0f, 200f) >= 0f && v.valueToY(-90f, 0f, 200f) <= 200f, "the range is inside the plot")
        val under = v.yToValue(40f, 0f, 200f)
        v.zoom(2f, anchorY = 40f, top = 0f, height = 200f)
        assertEquals(under, v.yToValue(40f, 0f, 200f), 1e-3f, "the value under the mouse stays put")

        v.fit(3f, 3f, 200f)
        assertEquals(3f, v.center)
        assertEquals(200f * 0.8f / 2f, v.pxPerUnit, 1e-4f, "a flat curve gets a ±1 window")
    }

    @Test
    fun CurveViewport_GridStepsAreNiceNumbers() {
        val v = CurveViewport(center = 0f, pxPerUnit = 2f)
        val grid = v.gridValues(height = 200f, minPx = 30f)
        // 30 px at 2 px per unit is 15 units: the step rounds up to 20.
        assertEquals(listOf(-40f, -20f, 0f, 20f, 40f), grid)
    }
}
