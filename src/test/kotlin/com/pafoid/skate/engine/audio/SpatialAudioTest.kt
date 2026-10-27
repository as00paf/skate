package com.pafoid.skate.engine.audio

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.pafoid.skate.engine.audio.AttenuationModel as M

class SpatialAudioTest {
    private val eps = 1e-4f
    private fun att(m: M, d: Float, min: Float = 1f, max: Float = 11f, r: Float = 1f) =
        SpatialAudio.attenuation(m, d, min, max, r)

    @Test
    fun SpatialAudio_NoneIsAlwaysUnity() = assertEquals(1f, att(M.NONE, 1000f), eps)

    @Test
    fun SpatialAudio_InverseMatchesFormula() {
        assertEquals(1f, att(M.INVERSE_CLAMPED, 0.2f), eps)
        assertEquals(0.5f, att(M.INVERSE_CLAMPED, 2f), eps)
        assertEquals(1f / 11f, att(M.INVERSE_CLAMPED, 500f), eps) // clamped at max distance
        assertEquals(0.5f, att(M.INVERSE, 2f), eps)
        assertEquals(1f / 101f, att(M.INVERSE, 101f), eps) // unclamped keeps falling
    }

    @Test
    fun SpatialAudio_LinearReachesSilenceAtMax() {
        assertEquals(0.5f, att(M.LINEAR_CLAMPED, 6f), eps)
        assertEquals(0f, att(M.LINEAR_CLAMPED, 11f), eps)
        assertEquals(0f, att(M.LINEAR_CLAMPED, 90f), eps)
        assertEquals(0f, att(M.LINEAR, 90f), eps) // gain floored at 0
    }

    @Test
    fun SpatialAudio_ExponentMatchesFormula() {
        assertEquals(0.25f, att(M.EXPONENT_CLAMPED, 2f, r = 2f), eps)
        assertEquals(1f, att(M.EXPONENT_CLAMPED, 0.5f, r = 2f), eps)
    }

    @Test
    fun SpatialAudio_RolloffZeroDisablesAttenuation() {
        assertEquals(1f, att(M.INVERSE_CLAMPED, 8f, r = 0f), eps)
        assertEquals(1f, att(M.LINEAR_CLAMPED, 8f, r = 0f), eps)
    }

    @Test
    fun SpatialAudio_DegenerateDistancesStayInRange() {
        val g = SpatialAudio.attenuation(M.LINEAR_CLAMPED, 5f, 3f, 3f, 1f)
        assertTrue(g in 0f..1f)
        assertTrue(SpatialAudio.attenuation(M.INVERSE_CLAMPED, 5f, 0f, 0f, 1f) in 0f..1f)
    }

    @Test
    fun SpatialAudio_PanFollowsListenerOrientation() {
        val origin = Vector3f()
        val fwd = Vector3f(0f, 0f, -1f)
        val up = Vector3f(0f, 1f, 0f)
        assertEquals(1f, SpatialAudio.pan(origin, fwd, up, Vector3f(5f, 0f, 0f)), eps)
        assertEquals(-1f, SpatialAudio.pan(origin, fwd, up, Vector3f(-5f, 0f, 0f)), eps)
        assertEquals(0f, SpatialAudio.pan(origin, fwd, up, Vector3f(0f, 0f, -5f)), eps)
        assertEquals(0f, SpatialAudio.pan(origin, fwd, up, origin), eps)
        // Listener turned to face +X: a source at -Z is now on the left... and +Z on the right.
        val faceX = Vector3f(1f, 0f, 0f)
        assertEquals(1f, SpatialAudio.pan(origin, faceX, up, Vector3f(0f, 0f, 5f)), eps)
        assertEquals(-1f, SpatialAudio.pan(origin, faceX, up, Vector3f(0f, 0f, -5f)), eps)
    }

    @Test
    fun SpatialAudio_DopplerRaisesPitchWhenApproachingAndLowersWhenReceding() {
        val l = Vector3f()
        val still = Vector3f()
        val src = Vector3f(0f, 0f, -100f)
        assertEquals(1f, SpatialAudio.dopplerPitch(l, still, src, still), eps)
        assertTrue(SpatialAudio.dopplerPitch(l, still, src, Vector3f(0f, 0f, 30f)) > 1f)
        assertTrue(SpatialAudio.dopplerPitch(l, still, src, Vector3f(0f, 0f, -30f)) < 1f)
        assertEquals(1f, SpatialAudio.dopplerPitch(l, still, src, Vector3f(0f, 0f, 30f), dopplerFactor = 0f), eps)
    }

    @Test
    fun SpatialAudio_DopplerSupersonicSourceStaysFinite() {
        val p = SpatialAudio.dopplerPitch(Vector3f(), Vector3f(), Vector3f(0f, 0f, -100f), Vector3f(0f, 0f, 5000f))
        assertTrue(p.isFinite() && p > 0f)
    }

    @Test
    fun SpatialAudio_VelocityFromMotion() {
        assertEquals(10f, SpatialAudio.velocityFromMotion(Vector3f(), Vector3f(1f, 0f, 0f), 0.1f).x, eps)
        assertEquals(0f, SpatialAudio.velocityFromMotion(Vector3f(), Vector3f(1f, 0f, 0f), 0f).x, eps)
    }
}
