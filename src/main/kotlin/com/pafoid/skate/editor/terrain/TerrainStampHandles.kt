package com.pafoid.skate.editor.terrain

import com.pafoid.skate.engine.terrain.StampFootprint
import com.pafoid.skate.engine.terrain.StampMode
import com.pafoid.skate.engine.terrain.TerrainStamp
import org.joml.Vector3f
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Viewport handles of a terrain stamp (EDT-92 §4) and the "Flatten terrain under" footprint, in terrain-local metres.
 * Pure, unit-tested; `TerrainBrushTool` draws and drags them.
 *
 * Yaw follows [TerrainStamp]: degrees about +Y, so the stamp's local +X axis points to `(cos yaw, 0, -sin yaw)` and its
 * local +Z axis to `(sin yaw, 0, cos yaw)`.
 */
object TerrainStampHandles {

    enum class Handle { CENTER, WIDTH, DEPTH, RADIUS, YAW }

    /** Distance of the yaw knob beyond the footprint's +Z edge (m). */
    const val YAW_KNOB_GAP = 2f
    const val MIN_SIZE = 0.5f

    fun axisX(yawDegrees: Float, out: Vector3f = Vector3f()): Vector3f {
        val r = Math.toRadians(yawDegrees.toDouble())
        return out.set(cos(r).toFloat(), 0f, -sin(r).toFloat())
    }

    fun axisZ(yawDegrees: Float, out: Vector3f = Vector3f()): Vector3f {
        val r = Math.toRadians(yawDegrees.toDouble())
        return out.set(sin(r).toFloat(), 0f, cos(r).toFloat())
    }

    /** Half extent along the stamp's +Z axis (where the yaw knob sits). */
    private fun halfDepth(s: TerrainStamp) = if (s.footprint == StampFootprint.CIRCLE) s.radius else s.depth * 0.5f

    /** The handles shown for [s] with their positions (terrain-local, at the stamp's height). */
    fun handles(s: TerrainStamp): List<Pair<Handle, Vector3f>> {
        val c = Vector3f(s.x, s.y, s.z)
        val x = axisX(s.yaw); val z = axisZ(s.yaw)
        val out = ArrayList<Pair<Handle, Vector3f>>()
        out += Handle.CENTER to Vector3f(c)
        if (s.footprint == StampFootprint.CIRCLE) {
            out += Handle.RADIUS to Vector3f(x).mul(s.radius).add(c)
        } else {
            out += Handle.WIDTH to Vector3f(x).mul(s.width * 0.5f).add(c)
            out += Handle.DEPTH to Vector3f(z).mul(s.depth * 0.5f).add(c)
        }
        out += Handle.YAW to Vector3f(z).mul(halfDepth(s) + YAW_KNOB_GAP).add(c)
        return out
    }

    /** The handle within [tolerance] (XZ distance) of [point], nearest first, or null. */
    fun pick(s: TerrainStamp, point: Vector3f, tolerance: Float): Handle? =
        handles(s).map { (h, p) -> h to xzDistance(p, point) }.filter { it.second <= tolerance }.minByOrNull { it.second }?.first

    /** [s] after dragging [handle] to [point] (terrain-local ground point). The centre keeps its height. */
    fun drag(s: TerrainStamp, handle: Handle, point: Vector3f): TerrainStamp {
        val dx = point.x - s.x; val dz = point.z - s.z
        return when (handle) {
            Handle.CENTER -> s.copy(x = point.x, z = point.z)
            Handle.WIDTH -> s.copy(width = max(MIN_SIZE, 2f * kotlin.math.abs(dx * axisX(s.yaw).x + dz * axisX(s.yaw).z)))
            Handle.DEPTH -> s.copy(depth = max(MIN_SIZE, 2f * kotlin.math.abs(dx * axisZ(s.yaw).x + dz * axisZ(s.yaw).z)))
            Handle.RADIUS -> s.copy(radius = max(MIN_SIZE * 0.5f, sqrt(dx * dx + dz * dz)))
            // The knob sits on +Z: yaw is the angle of (dx, dz) from +Z about +Y
            Handle.YAW -> s.copy(yaw = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat())
        }
    }

    /** Footprint corners (RECT/PREFAB) or a circle polyline, for drawing. */
    fun outline(s: TerrainStamp, segments: Int = 48): List<Vector3f> {
        val c = Vector3f(s.x, s.y, s.z)
        val x = axisX(s.yaw); val z = axisZ(s.yaw)
        if (s.footprint == StampFootprint.CIRCLE) {
            return (0..segments).map { k ->
                val a = 2.0 * Math.PI * k / segments
                Vector3f(x).mul((cos(a) * s.radius).toFloat()).add(Vector3f(z).mul((sin(a) * s.radius).toFloat())).add(c)
            }
        }
        val hw = s.width * 0.5f; val hd = s.depth * 0.5f
        return listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f, -1f to -1f).map { (u, v) ->
            Vector3f(x).mul(u * hw).add(Vector3f(z).mul(v * hd)).add(c)
        }
    }

    /**
     * "Flatten terrain under" (EDT-92 §4): a RECT FLATTEN stamp covering the world-space bounding spheres [bounds]
     * (centre, radius) plus [margin], flattened to their lowest point, in the frame of a terrain at [terrainPosition].
     * Null when there are no bounds.
     */
    fun flattenUnder(bounds: List<Pair<Vector3f, Float>>, terrainPosition: Vector3f, margin: Float = 1f, falloff: Float = 4f): TerrainStamp? {
        if (bounds.isEmpty()) return null
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for ((c, r) in bounds) {
            minX = minOf(minX, c.x - r); maxX = maxOf(maxX, c.x + r)
            minZ = minOf(minZ, c.z - r); maxZ = maxOf(maxZ, c.z + r)
            minY = minOf(minY, c.y - r)
        }
        return TerrainStamp(
            footprint = StampFootprint.RECT, mode = StampMode.FLATTEN,
            x = (minX + maxX) * 0.5f - terrainPosition.x,
            y = minY - terrainPosition.y,
            z = (minZ + maxZ) * 0.5f - terrainPosition.z,
            yaw = 0f,
            width = (maxX - minX) + 2f * margin,
            depth = (maxZ - minZ) + 2f * margin,
            falloff = falloff,
        )
    }

    private fun xzDistance(a: Vector3f, b: Vector3f): Float {
        val dx = a.x - b.x; val dz = a.z - b.z
        return sqrt(dx * dx + dz * dz)
    }
}
