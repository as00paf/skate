package com.pafoid.skate.engine.audio

import kotlinx.serialization.Serializable
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Serializable
enum class AttenuationModel { NONE, INVERSE, INVERSE_CLAMPED, LINEAR, LINEAR_CLAMPED, EXPONENT, EXPONENT_CLAMPED }

/** Pure-Kotlin spatialisation math; mirrors the OpenAL distance-model formulas. */
object SpatialAudio {
    const val DEFAULT_SPEED_OF_SOUND = 343.3f

    fun attenuation(
        model: AttenuationModel,
        distance: Float,
        minDistance: Float,
        maxDistance: Float,
        rolloff: Float
    ): Float {
        if (model == AttenuationModel.NONE) return 1f
        val ref = max(minDistance, 0.0001f)
        val maxD = max(maxDistance, ref)
        val clamped = model.name.endsWith("CLAMPED")
        val d = if (clamped) distance.coerceIn(ref, maxD) else max(distance, 0f)
        val gain = when (model) {
            AttenuationModel.INVERSE, AttenuationModel.INVERSE_CLAMPED ->
                ref / (ref + rolloff * (d - ref))
            AttenuationModel.LINEAR, AttenuationModel.LINEAR_CLAMPED ->
                if (maxD == ref) 1f else 1f - rolloff * (d - ref) / (maxD - ref)
            AttenuationModel.EXPONENT, AttenuationModel.EXPONENT_CLAMPED ->
                (d / ref).pow(-rolloff)
            AttenuationModel.NONE -> 1f
        }
        return if (gain.isNaN()) 0f else gain.coerceIn(0f, 1f)
    }

    /**
     * Stereo pan in [-1 (left), 1 (right)] of [source] as heard by a listener at [listenerPos]
     * facing [forward] with [up]. 0 when the source is centred, in front, behind, or coincident.
     */
    fun pan(listenerPos: Vector3f, forward: Vector3f, up: Vector3f, source: Vector3f): Float {
        val toSource = Vector3f(source).sub(listenerPos)
        val len = toSource.length()
        if (len < 1e-6f) return 0f
        val right = Vector3f(forward).cross(up).normalize()
        return (toSource.dot(right) / len).coerceIn(-1f, 1f)
    }

    /** Doppler pitch factor (OpenAL formula, clamped velocity so the source never reaches Mach 1). */
    fun dopplerPitch(
        listenerPos: Vector3f,
        listenerVel: Vector3f,
        sourcePos: Vector3f,
        sourceVel: Vector3f,
        dopplerFactor: Float = 1f,
        speedOfSound: Float = DEFAULT_SPEED_OF_SOUND
    ): Float {
        val sl = Vector3f(listenerPos).sub(sourcePos)
        val dist = sl.length()
        if (dist < 1e-6f || dopplerFactor <= 0f) return 1f
        sl.div(dist)
        val limit = speedOfSound * 0.99f / dopplerFactor
        val vls = min(sl.dot(listenerVel), limit)
        val vss = min(sl.dot(sourceVel), limit)
        return (speedOfSound - dopplerFactor * vls) / (speedOfSound - dopplerFactor * vss)
    }

    fun velocityFromMotion(previous: Vector3f, current: Vector3f, dt: Float): Vector3f =
        if (dt <= 1e-6f) Vector3f() else Vector3f(current).sub(previous).div(dt)
}
