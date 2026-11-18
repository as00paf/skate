package com.pafoid.skate.engine.environment

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** What [WaterQueries.sample] found at a point: all values in world space. */
class WaterSample {
    /** World Y of the moving surface. */
    var surfaceY = 0f
    val normal = Vector3f(0f, 1f, 0f)
    /** Flow in world XZ (y = 0), m/s. */
    val flow = Vector3f()
    var density = 1000f
    var linearDrag = 0f
    var angularDrag = 0f
    /** World Y of the volume's bottom (surface rest height minus `depth`). */
    var floorY = 0f
    /** The entity holding the [WaterComponent]. */
    var source: GameObject? = null
}

/** Depths below the surface (positive = under water) of a capsule's feet, centre and head, and its submerged fraction. */
class Submersion {
    var feetDepth = 0f
    var centerDepth = 0f
    var headDepth = 0f
    /** 0 dry .. 1 fully under, along the capsule's height. */
    var fraction = 0f
    var source: GameObject? = null
}

/**
 * Water lookups for physics, gameplay and audio (PHY-16, `docs/design/REN-29.md` §8.3–8.4), for bounded
 * [WaterComponent] volumes: the surface rectangle (position and yaw of the Transform) from `depth` below the rest
 * surface up to the highest crest. Heights use [WaterWaves] in world XZ with the same wind settings as the renderer, so
 * what floats is what you see. The ocean source (task 20) is not handled yet.
 *
 * Call [refresh] once per fixed step, then query.
 */
class WaterQueries {
    /** One water volume as of the last [refresh]. */
    class Volume internal constructor(val source: GameObject, val water: WaterComponent) {
        val waves = WaterWaves()
        val center = Vector3f()
        var yaw = 0f
            internal set
        internal var cos = 1f
        internal var sin = 0f
        /** Flow in world XZ. */
        val flow = Vector3f()

        val restY: Float get() = center.y
        val floorY: Float get() = center.y - water.depth
        val topY: Float get() = center.y + waves.totalAmplitude

        /** True when world ([x], [z]) lies on the surface rectangle, grown by [margin] metres. */
        fun containsXZ(x: Float, z: Float, margin: Float = 0f): Boolean {
            val dx = x - center.x
            val dz = z - center.z
            // Into the water's frame: inverse yaw
            val lx = dx * cos - dz * sin
            val lz = dx * sin + dz * cos
            return abs(lx) <= water.sizeX / 2f + margin && abs(lz) <= water.sizeZ / 2f + margin
        }

        fun surfaceY(x: Float, z: Float, t: Float): Float = center.y + waves.heightAt(x, z, t)
    }

    private val cache = IdentityHashMap<WaterComponent, Volume>()
    private val list = ArrayList<Volume>()

    /** Volumes found by the last [refresh], in scene order. */
    val volumes: List<Volume> get() = list

    /** Rebuilds the volumes from the enabled [waters] (entities with a [WaterComponent] and a [Transform]). */
    fun refresh(waters: Iterable<GameObject>, environment: EnvironmentComponent?) {
        list.clear()
        val seen = java.util.Collections.newSetFromMap(IdentityHashMap<WaterComponent, Boolean>())
        for (go in waters) {
            val water = go.getComponent<WaterComponent>()?.takeIf { it.enabled } ?: continue
            val transform = go.getComponent<Transform>()?.takeIf { it.enabled } ?: continue
            val volume = cache.getOrPut(water) { Volume(go, water) }
            seen += water
            volume.waves.configure(water.waves, water.windDrivenWaves, environment?.windSpeed ?: 0f, environment?.windDirection ?: 0f)
            val m = transform.worldMatrix
            volume.center.set(m.m30(), m.m31(), m.m32())
            // Same frame as the renderer (WaterRenderer.modelMatrix): translation plus yaw only
            volume.yaw = atan2(m.m20(), m.m22())
            volume.cos = cos(volume.yaw)
            volume.sin = sin(volume.yaw)
            val fx = water.flowVelocity.x
            val fz = water.flowVelocity.y
            volume.flow.set(fx * volume.cos + fz * volume.sin, 0f, -fx * volume.sin + fz * volume.cos)
            list += volume
        }
        cache.keys.retainAll(seen)
    }

    /** The first volume whose rectangle holds world ([x], [z]) and whose vertical range holds [y], or null. */
    fun volumeAt(x: Float, y: Float, z: Float, margin: Float = 0f): Volume? =
        list.firstOrNull { it.containsXZ(x, z) && y >= it.floorY - margin && y <= it.topY + margin }

    /** Fills [out] for the water over world ([x], [z]) at time [t]; false when there is none. */
    fun sample(x: Float, z: Float, t: Float, out: WaterSample): Boolean {
        val volume = list.firstOrNull { it.containsXZ(x, z) } ?: return false
        out.surfaceY = volume.surfaceY(x, z, t)
        volume.waves.normalAt(x, z, t, out.normal)
        out.flow.set(volume.flow)
        out.density = volume.water.density
        out.linearDrag = volume.water.linearDrag
        out.angularDrag = volume.water.angularDrag
        out.floorY = volume.floorY
        out.source = volume.source
        return true
    }

    /**
     * Depths of an upright capsule centred at [center] ([radius], cylinder [height] between the caps, as
     * `ColliderShape.Capsule` on axis Y) in the water at time [t]; false when no water is under it.
     */
    fun submersion(center: Vector3f, radius: Float, height: Float, t: Float, out: Submersion): Boolean {
        val volume = list.firstOrNull { it.containsXZ(center.x, center.z) } ?: return false
        val surface = volume.surfaceY(center.x, center.z, t)
        val half = height / 2f + radius
        out.feetDepth = surface - (center.y - half)
        out.centerDepth = surface - center.y
        out.headDepth = surface - (center.y + half)
        out.fraction = if (half <= 0f) (if (out.centerDepth >= 0f) 1f else 0f) else (out.feetDepth / (2f * half)).coerceIn(0f, 1f)
        out.source = volume.source
        return true
    }
}
