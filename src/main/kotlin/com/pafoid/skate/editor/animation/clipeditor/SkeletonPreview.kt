package com.pafoid.skate.editor.animation.clipeditor

import com.pafoid.skate.engine.animation.edit.ClipRig
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * The preview's bone overlay (ANI-14): world joint positions from the clip's locals, an orbit camera, and the
 * projection to the preview area. Pure (no GL): the window draws the result with ImGui and picks bones with [pick].
 */
class SkeletonPreview {
    /** Orbit camera: [yaw]/[pitch] in degrees around [target], [distance] in metres. */
    var yaw = 30f
    var pitch = 15f
    var distance = 3f
    val target = Vector3f(0f, 1f, 0f)

    /** World position of every joint for bone [locals] (`ClipEvaluator.locals`). */
    fun joints(rig: ClipRig, locals: Array<Matrix4f>): Array<Vector3f> {
        val world = arrayOfNulls<Matrix4f>(rig.boneCount)
        fun worldOf(i: Int): Matrix4f = world[i] ?: run {
            val p = rig.parents[i]
            (if (p < 0) Matrix4f(locals[i]) else Matrix4f(worldOf(p)).mul(locals[i])).also { world[i] = it }
        }
        return Array(rig.boneCount) { worldOf(it).getTranslation(Vector3f()) }
    }

    /** Frames [points]: the camera looks at their centre from far enough to see them all. */
    fun frame(points: Array<Vector3f>) {
        if (points.isEmpty()) return
        val min = Vector3f(Float.MAX_VALUE); val max = Vector3f(-Float.MAX_VALUE)
        points.forEach { min.min(it); max.max(it) }
        min.add(max, target).mul(0.5f)
        val radius = (max.distance(min) * 0.5f).coerceAtLeast(0.1f)
        distance = radius / tan(Math.toRadians(FOV_DEG / 2.0)).toFloat() * 1.2f
    }

    fun orbit(dxDeg: Float, dyDeg: Float) {
        yaw = (yaw + dxDeg) % 360f
        pitch = (pitch + dyDeg).coerceIn(-89f, 89f)
    }

    fun zoom(factor: Float) { distance = (distance * factor).coerceIn(0.05f, 1000f) }

    fun eye(): Vector3f {
        val y = Math.toRadians(yaw.toDouble()); val p = Math.toRadians(pitch.toDouble())
        return Vector3f(
            (cos(p) * sin(y)).toFloat(), sin(p).toFloat(), (cos(p) * cos(y)).toFloat(),
        ).mul(distance).add(target)
    }

    fun viewProjection(width: Float, height: Float): Matrix4f =
        Matrix4f().perspective(Math.toRadians(FOV_DEG).toFloat(), width / height.coerceAtLeast(1f), 0.01f, 1000f)
            .lookAt(eye(), target, Vector3f(0f, 1f, 0f))

    /** Screen positions (pixels from the area's top-left) of [points]; null for points behind the camera. */
    fun project(points: Array<Vector3f>, width: Float, height: Float): Array<FloatArray?> {
        val vp = viewProjection(width, height)
        val clip = org.joml.Vector4f()
        return Array(points.size) { i ->
            clip.set(points[i], 1f).mul(vp)
            if (clip.w <= 1e-4f) null
            else floatArrayOf((clip.x / clip.w * 0.5f + 0.5f) * width, (1f - (clip.y / clip.w * 0.5f + 0.5f)) * height)
        }
    }

    /** Index of the joint nearest to ([x], [y]) within [radius] pixels, or -1. */
    fun pick(screen: Array<FloatArray?>, x: Float, y: Float, radius: Float = 8f): Int {
        var best = -1
        var bestD = radius * radius
        screen.forEachIndexed { i, p ->
            if (p == null) return@forEachIndexed
            val d = (p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y)
            if (d <= bestD) { bestD = d; best = i }
        }
        return best
    }

    companion object {
        const val FOV_DEG = 45.0
    }
}
