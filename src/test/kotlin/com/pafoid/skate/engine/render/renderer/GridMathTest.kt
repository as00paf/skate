package com.pafoid.skate.engine.render.renderer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-25: spacing (LOD), pixel-width anti-aliasing and distance fade of the infinite editor grid. */
class GridMathTest {
    private val eps = 1e-4f

    @Test
    fun closeUp_ShowsMinorAndMajorSteps() {
        // 1 cm per pixel: 1 m cells are 100 px apart
        val lod = GridMath.lod(unitsPerPixel = 0.01f, minorStep = 1f, ratio = 10f)
        assertEquals(0f, lod.level, eps)
        assertEquals(1f, lod.minor, eps)
        assertEquals(10f, lod.major, eps)
        assertEquals(100f, lod.nextMajor, eps)
        assertEquals(0f, lod.fade, eps)
    }

    @Test
    fun movingAway_FadesMinorLinesThenStepsUpOneLevel() {
        val ratio = 10f
        // Minor cells exactly MIN_PIXELS apart: level 1, the old major lines are now the minor ones
        val atThreshold = GridMath.lod(GridMath.MIN_PIXELS_BETWEEN_LINES.let { 1f / it }, 1f, ratio)
        assertEquals(1f, atThreshold.level, eps)
        assertEquals(10f, atThreshold.minor, eps)
        assertEquals(100f, atThreshold.major, eps)

        // Halfway (in log scale) between levels 0 and 1: minor lines half faded, spacing unchanged
        val between = GridMath.lod(1f / kotlin.math.sqrt(ratio) / GridMath.MIN_PIXELS_BETWEEN_LINES, 1f, ratio)
        assertEquals(0.5f, between.level, eps)
        assertEquals(0.5f, between.fade, eps)
        assertEquals(1f, between.minor, eps)
    }

    @Test
    fun lodLevel_GrowsMonotonicallyWithDistance() {
        var previous = -1f
        for (upp in listOf(0.001f, 0.01f, 0.05f, 0.2f, 1f, 5f, 40f)) {
            val level = GridMath.lodLevel(upp, 1f, 10f)
            assertTrue(level >= previous, "level $level after $previous at $upp")
            previous = level
        }
    }

    @Test
    fun linesNeverPackTighterThanMinPixels() {
        for (upp in listOf(0.001f, 0.02f, 0.3f, 2f, 17f, 250f)) {
            val lod = GridMath.lod(upp, 1f, 10f)
            val minorPx = lod.minor / upp
            // Fully visible minor lines (fade 0) are at least ratio * MIN_PIXELS apart; faded ones at least MIN_PIXELS
            assertTrue(minorPx >= GridMath.MIN_PIXELS_BETWEEN_LINES - 1e-3f, "minor lines $minorPx px apart at $upp")
            assertTrue(lod.major / upp >= GridMath.MIN_PIXELS_BETWEEN_LINES * 10f - 1e-2f)
        }
    }

    @Test
    fun ratio_FollowsTheComponentStepsAndIsAtLeastTwo() {
        assertEquals(10f, GridMath.ratio(1f, 10f), eps)
        assertEquals(5f, GridMath.ratio(0.5f, 2.5f), eps)
        assertEquals(2f, GridMath.ratio(1f, 1f), eps)
    }

    @Test
    fun pixelDistance_MeasuresInScreenPixels() {
        // 0.1 m from the line at x = 2 with 0.05 m per pixel -> 2 px
        assertEquals(2f, GridMath.pixelDistance(2.1f, 1f, 0.05f), 1e-3f)
        assertEquals(2f, GridMath.pixelDistance(1.9f, 1f, 0.05f), 1e-3f)
        assertEquals(0f, GridMath.pixelDistance(3f, 1f, 0.05f), 1e-3f)
        assertEquals(10f, GridMath.pixelDistance(-2.5f, 1f, 0.05f), 1e-3f)
    }

    @Test
    fun grazingAngle_LineKeepsItsPixelWidth() {
        // Same line seen head-on (1 cm per pixel) and at a grazing angle (1 m per pixel across it): its covered
        // footprint in pixels is the same, so it does not thicken
        fun footprintPx(unitsPerPixel: Float): Float {
            var px = 0f
            var x = -5f
            while (x <= 5f) {
                px += GridMath.coverage(GridMath.pixelDistance(x * unitsPerPixel, 1000f, unitsPerPixel), GridMath.MINOR_LINE_WIDTH_PX) * 0.01f
                x += 0.01f
            }
            return px
        }
        assertEquals(GridMath.MINOR_LINE_WIDTH_PX, footprintPx(0.01f), 0.02f)
        assertEquals(GridMath.MINOR_LINE_WIDTH_PX, footprintPx(1f), 0.02f)
    }

    @Test
    fun coverage_IsAnAntiAliasedPixelEdge() {
        assertEquals(1f, GridMath.coverage(0f, 1f), eps)
        assertEquals(0.5f, GridMath.coverage(0.5f, 1f), eps)
        assertEquals(0f, GridMath.coverage(1f, 1f), eps)
        assertEquals(1f, GridMath.coverage(0.5f, 2f), eps)
        assertEquals(0f, GridMath.coverage(1.5f, 2f), eps)
    }

    @Test
    fun distanceFade_IsOpaqueNearAndGoneFar() {
        val end = GridMath.fadeEnd(cameraHeight = 5f, minExtent = 50f, maxExtent = 500f)
        val start = GridMath.fadeStart(end)
        assertEquals(200f, end, eps)
        assertEquals(1f, GridMath.distanceFade(0f, start, end), eps)
        assertEquals(1f, GridMath.distanceFade(start, start, end), eps)
        assertEquals(0f, GridMath.distanceFade(end, start, end), eps)
        val mid = GridMath.distanceFade((start + end) / 2f, start, end)
        assertEquals(0.5f, mid, eps)
    }

    @Test
    fun fadeEnd_GrowsWithHeightWithinTheExtentRange() {
        assertEquals(50f, GridMath.fadeEnd(0f, 50f, 500f), eps)
        assertEquals(50f, GridMath.fadeEnd(-0.5f, 50f, 500f), eps)
        assertEquals(400f, GridMath.fadeEnd(10f, 50f, 500f), eps)
        assertEquals(500f, GridMath.fadeEnd(100f, 50f, 500f), eps)
        assertEquals(400f, GridMath.fadeEnd(-10f, 50f, 500f), eps, "below the grid fades like above")
    }

    @Test
    fun shader_MirrorsTheMathAndMeasuresLinesWithDerivatives() {
        val src = javaClass.getResourceAsStream("/shaders/editor_grid.glsl")!!.readBytes().toString(Charsets.UTF_8)
        val fragment = src.substringAfter("#type fragment")
        assertTrue(fragment.contains("fwidth(p)"), "line distances are in screen pixels")
        assertTrue(fragment.contains("clamp(widthPx * 0.5 + 0.5 - distancePx, 0.0, 1.0)"), "same coverage as GridMath")
        assertTrue(fragment.contains("log(uLodRatio) + 1.0"), "same LOD level as GridMath")
        assertTrue(fragment.contains("smoothstep(uFadeStart, uFadeEnd"), "distance fade")
        assertTrue(fragment.contains("gl_FragDepth"), "depth pull per fragment, like the debug lines")
        assertTrue(Regex("""X_AXIS_COLOR\s*=\s*vec3\(0\.9,""").containsMatchIn(fragment), "red X axis")
        assertTrue(Regex("""Z_AXIS_COLOR\s*=\s*vec3\(0\.2, 0\.35, 0\.95\)""").containsMatchIn(fragment), "blue Z axis")
    }
}
