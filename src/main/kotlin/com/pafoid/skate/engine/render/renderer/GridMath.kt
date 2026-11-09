package com.pafoid.skate.engine.render.renderer

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * Line spacing, anti-aliasing and fade of the editor grid (REN-25). CPU mirror of `editor_grid.glsl` for tests; keep
 * the two in sync. [GridRenderer] uploads these constants, so both sides use the same values.
 *
 * - **Spacing (LOD):** each pixel picks its own level from how many world units it covers ([lodLevel]). Level 0 shows
 *   lines every `minorStep` and every `minorStep * ratio` (the major lines). As the camera moves away, the minor
 *   lines fade out ([Lod.fade]) and the next level takes over: the old major lines become the minor ones and lines
 *   every `ratio^2` steps become major. Far parts of the floor (and grazing views) use coarser levels than near ones,
 *   so lines never pack tighter than [MIN_PIXELS_BETWEEN_LINES].
 * - **Anti-aliasing:** a line's distance is measured in pixels with screen-space derivatives ([pixelDistance]), so a
 *   line is always [MINOR_LINE_WIDTH_PX] (or [MAJOR_LINE_WIDTH_PX], [AXIS_LINE_WIDTH_PX]) wide on screen, at any
 *   distance or angle: it never thickens at grazing angles.
 * - **Distance fade:** the grid fades out with horizontal distance from the camera ([distanceFade]); the fade range
 *   grows with the camera height, between the grid component's min and max extent.
 */
object GridMath {
    /** Lines of a level fade out as they get closer than this on screen. */
    const val MIN_PIXELS_BETWEEN_LINES = 4f

    const val MINOR_LINE_WIDTH_PX = 1f
    const val MAJOR_LINE_WIDTH_PX = 1.5f
    const val AXIS_LINE_WIDTH_PX = 2f

    /** Opacity of minor and major lines at full coverage (axes are opaque). */
    const val MINOR_OPACITY = 0.5f
    const val MAJOR_OPACITY = 0.85f

    /** Fade end distance per metre of camera height, before clamping to the extent range. */
    const val FADE_DISTANCE_PER_HEIGHT = 40f

    /** The fade starts at this fraction of its end distance. */
    const val FADE_START_FRACTION = 0.3f

    /** Lines of a level: [minor] (fading out by [fade]), [major] and [nextMajor] spacing in world units. */
    data class Lod(val level: Float, val minor: Float, val major: Float, val nextMajor: Float) {
        /** 0: minor lines fully visible; 1: gone (the next level takes over). */
        val fade: Float get() = level - floor(level)
    }

    /** Major step / minor step, at least 2 (GridLines default 10/1 = 10). */
    fun ratio(minorStep: Float, majorStep: Float): Float = max(majorStep / minorStep, 2f)

    /** Continuous LOD level of a pixel covering [unitsPerPixel] world units (0 when minor lines are wide apart). */
    fun lodLevel(unitsPerPixel: Float, minorStep: Float, ratio: Float): Float {
        val x = unitsPerPixel * MIN_PIXELS_BETWEEN_LINES / minorStep
        if (x <= 0f) return 0f
        return max(0f, (ln(x) / ln(ratio)) + 1f)
    }

    fun lod(unitsPerPixel: Float, minorStep: Float, ratio: Float): Lod {
        val level = lodLevel(unitsPerPixel, minorStep, ratio)
        val minor = minorStep * ratio.pow(floor(level))
        return Lod(level, minor, minor * ratio, minor * ratio * ratio)
    }

    /**
     * Distance in pixels from world coordinate [coord] to the nearest line every [step] units, when one pixel covers
     * [unitsPerPixel] units along that axis (the shader's `fwidth`).
     */
    fun pixelDistance(coord: Float, step: Float, unitsPerPixel: Float): Float {
        val c = coord / step
        val toLine = abs((c - 0.5f) - floor(c - 0.5f) - 0.5f) // cells to the nearest line
        return toLine * step / max(unitsPerPixel, 1e-6f)
    }

    /** Coverage 0..1 of a line [widthPx] wide at [distancePx] pixels: a one-pixel anti-aliased edge. */
    fun coverage(distancePx: Float, widthPx: Float): Float = (widthPx * 0.5f + 0.5f - distancePx).coerceIn(0f, 1f)

    /** Distance at which the grid has faded out, for a camera [cameraHeight] above the grid. */
    fun fadeEnd(cameraHeight: Float, minExtent: Float, maxExtent: Float): Float =
        (max(abs(cameraHeight), 1f) * FADE_DISTANCE_PER_HEIGHT).coerceIn(minExtent, max(minExtent, maxExtent))

    fun fadeStart(fadeEnd: Float): Float = fadeEnd * FADE_START_FRACTION

    /** Opacity 0..1 at [horizontalDistance] from the camera: 1 up to [fadeStart], 0 from [fadeEnd]. */
    fun distanceFade(horizontalDistance: Float, fadeStart: Float, fadeEnd: Float): Float =
        1f - smoothstep(fadeStart, fadeEnd, horizontalDistance)

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
