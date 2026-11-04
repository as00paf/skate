package com.pafoid.skate.engine.render.shadow

import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.abs

/**
 * Shadow data produced by the shadow pass and consumed by the geometry pass in the same frame.
 * [consume] hands it over once, so a disabled shadow pass leaves the next geometry pass without stale shadows.
 */
class ShadowFrame {
    var valid = false
        private set

    var quality: ShadowQualityLevel = ShadowQualityLevel.DEFAULT
    var cascadeCount = 0
    val cascades = Array(CascadeSplits.MAX_CASCADES) { CascadeFit() }

    /** Far distance (view depth) of each cascade; `splits[0]` is the camera near plane. */
    val splits = FloatArray(CascadeSplits.MAX_CASCADES + 1)
    var cascadeTextureId = 0
    var cascadeResolution = 0

    val localSlots = ArrayList<LocalShadowSlot>(LocalShadows.MAX_SHADOWED_LIGHTS)
    val localMatrices = Array(LocalShadows.MAX_LAYERS) { Matrix4f() }
    var localTextureId = 0
    var localResolution = 0

    /** Extra receiver biases from the directional light component (metres). */
    var extraDepthBias = 0f
    var extraNormalOffset = 0f

    fun publish() { valid = true }

    fun reset() {
        valid = false
        cascadeCount = 0
        localSlots.clear()
    }

    /** Returns true once per published frame. */
    fun consume(): Boolean {
        val was = valid
        valid = false
        return was
    }
}

/** Bounding-sphere tests used to skip shadow casters that cannot touch a given map. */
object ShadowCulling {
    private val tmp = Vector4f() // render thread only

    /**
     * True if the sphere overlaps the cascade's light-space box. Casters nearer to the light than the box are kept:
     * the shadow pass renders with depth clamping, so they still cast ("pancaking").
     */
    fun intersectsCascade(fit: CascadeFit, center: Vector3f, radius: Float): Boolean {
        if (fit.radius <= 0f) return false
        fit.matrix.transform(tmp.set(center, 1f))
        val rxy = radius / fit.radius
        val rz = 2f * radius / fit.depthRange
        return abs(tmp.x) <= 1f + rxy && abs(tmp.y) <= 1f + rxy && tmp.z <= 1f + rz
    }

    /** True if the caster sphere overlaps the light's sphere of influence. */
    fun intersectsLight(lightPosition: Vector3f, range: Float, center: Vector3f, radius: Float): Boolean {
        val r = range + radius
        return lightPosition.distanceSquared(center) <= r * r
    }
}
