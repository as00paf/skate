package com.pafoid.skate.engine.animation

import org.joml.FrustumIntersection
import org.joml.Matrix4f
import org.joml.Vector3f

/** Answers whether a world-space bounding sphere can be seen by the active camera. */
interface AnimationVisibility {
    /** Called once per frame before any [isVisible] query. */
    fun beginFrame() {}
    fun isVisible(center: Vector3f, radius: Float): Boolean
}

/** Frustum test against the camera's `projection * view`, refreshed each frame from [matrices]. */
class FrustumVisibility(private val matrices: () -> Pair<Matrix4f, Matrix4f>) : AnimationVisibility {
    private val frustum = FrustumIntersection()
    private val viewProjection = Matrix4f()

    override fun beginFrame() {
        val (projection, view) = matrices()
        update(projection, view)
    }

    fun update(projection: Matrix4f, view: Matrix4f) {
        frustum.set(projection.mul(view, viewProjection))
    }

    override fun isVisible(center: Vector3f, radius: Float) =
        frustum.testSphere(center.x, center.y, center.z, radius)
}

/**
 * Per-object update throttle. Visible objects update every frame; off-screen ones accumulate time and update
 * once per [offscreenInterval] seconds with the whole accumulated delta, so their playback stays in sync
 * (and shadows/reflections of off-screen casters never freeze for long).
 */
class AnimationUpdateGate(var offscreenInterval: Float = DEFAULT_OFFSCREEN_INTERVAL) {
    private var accumulated = 0f

    /** Returns the delta to simulate this frame, or 0 when the update should be skipped. */
    fun consume(visible: Boolean, dt: Float): Float {
        accumulated += dt
        if (visible || accumulated >= offscreenInterval) {
            val out = accumulated
            accumulated = 0f
            return out
        }
        return 0f
    }

    companion object {
        const val DEFAULT_OFFSCREEN_INTERVAL = 0.25f
    }
}
