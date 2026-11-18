package com.pafoid.skate.engine.terrain

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A flattened area (REN-28 §5.4): ground under a park, a ramp or a building, applied after the recipe in metres.
 * Position and target height [y] are terrain-local (relative to the terrain's Transform), [yaw] in degrees.
 * `PREFAB` footprints use [width] × [depth] too: the editor fills them from the prefab's XZ bounds and keeps
 * [prefabGuid] to follow it.
 */
@Serializable
data class TerrainStamp(
    val footprint: StampFootprint = StampFootprint.RECT,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val yaw: Float = 0f,
    val width: Float = 10f,
    val depth: Float = 10f,
    val radius: Float = 5f,
    val prefabGuid: String = "",
    val mode: StampMode = StampMode.FLATTEN,
    /** Metres of smooth blend back to the recipe height outside the footprint. */
    val falloff: Float = 4f,
    /** Higher stamps win where stamps overlap. */
    val priority: Int = 0,
) {
    /** Half extent of the stamp's influence (footprint plus falloff) on any axis, for the overlap index. */
    val reach: Float
        get() = falloff + when (footprint) {
            StampFootprint.CIRCLE -> radius
            else -> 0.5f * sqrt(width * width + depth * depth)
        }

    /** Distance (m) from local ([px], [pz]) to the footprint; 0 inside. */
    fun distanceOutside(px: Float, pz: Float): Float {
        val dx = px - x
        val dz = pz - z
        return when (footprint) {
            StampFootprint.CIRCLE -> max(0f, sqrt(dx * dx + dz * dz) - radius)
            else -> {
                val r = Math.toRadians(yaw.toDouble())
                val c = cos(r).toFloat(); val s = sin(r).toFloat()
                // Into the stamp's frame (inverse yaw about Y)
                val lx = dx * c - dz * s
                val lz = dx * s + dz * c
                val ox = max(0f, abs(lx) - width * 0.5f)
                val oz = max(0f, abs(lz) - depth * 0.5f)
                sqrt(ox * ox + oz * oz)
            }
        }
    }

    /** [height] with this stamp applied at ([px], [pz]). */
    fun apply(px: Float, pz: Float, height: Float): Float {
        val d = distanceOutside(px, pz)
        val weight = if (d <= 0f) 1f else if (falloff <= 0f || d >= falloff) return height else 1f - smoothstep(d / falloff)
        val target = when (mode) {
            StampMode.FLATTEN -> y
            StampMode.RAISE_TO -> max(height, y)
            StampMode.LOWER_TO -> kotlin.math.min(height, y)
        }
        return height + (target - height) * weight
    }

    private fun smoothstep(t: Float): Float { val c = t.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
}

@Serializable
enum class StampFootprint { RECT, CIRCLE, PREFAB }

@Serializable
enum class StampMode { FLATTEN, RAISE_TO, LOWER_TO }

/**
 * Uniform grid index over stamps (REN-28 §5.4): a chunk only evaluates the stamps whose reach overlaps it, so stamps
 * cost nothing elsewhere. Stamps apply in ascending priority (then list order), so higher priority wins.
 */
class StampIndex(stamps: List<TerrainStamp>, private val cellSize: Float) {
    private val ordered = stamps.withIndex().sortedWith(compareBy({ it.value.priority }, { it.index })).map { it.value }
    private val cells = HashMap<Long, MutableList<Int>>()

    init {
        require(cellSize > 0f)
        ordered.forEachIndexed { i, s ->
            val r = s.reach
            for (cz in cell(s.z - r)..cell(s.z + r)) for (cx in cell(s.x - r)..cell(s.x + r)) {
                cells.getOrPut(key(cx, cz)) { ArrayList() }.add(i)
            }
        }
    }

    val isEmpty: Boolean get() = ordered.isEmpty()

    private fun cell(v: Float) = kotlin.math.floor(v / cellSize).toInt()
    private fun key(cx: Int, cz: Int) = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

    /** Stamps (in application order) that may affect the local rectangle [minX, maxX] × [minZ, maxZ]. */
    fun overlapping(minX: Float, minZ: Float, maxX: Float, maxZ: Float): List<TerrainStamp> {
        if (ordered.isEmpty()) return emptyList()
        val hits = java.util.TreeSet<Int>()
        for (cz in cell(minZ)..cell(maxZ)) for (cx in cell(minX)..cell(maxX)) cells[key(cx, cz)]?.let(hits::addAll)
        return hits.map { ordered[it] }
    }

    /** Applies [stamps] (from [overlapping]) to [height] at local ([x], [z]). */
    fun apply(stamps: List<TerrainStamp>, x: Float, z: Float, height: Float): Float {
        var h = height
        for (s in stamps) h = s.apply(x, z, h)
        return h
    }
}
