package com.pafoid.skate.engine.gameplay.camera

import com.pafoid.skate.engine.ecs.components.FirstPersonCameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.gameplay.MotionMath
import com.pafoid.skate.engine.utils.CameraMotion
import org.joml.Vector3f

/** Pose maths of [FirstPersonCameraComponent], without GL or physics. */
object FirstPersonCameraRig {

    /** Eye point for [target]: [FirstPersonCameraComponent.eyeHeight] up, [FirstPersonCameraComponent.forwardOffset] ahead. */
    fun eye(camera: FirstPersonCameraComponent, target: FollowTarget, dest: Vector3f = Vector3f()): Vector3f {
        val forward = MotionMath.directionOfYaw(target.yaw)
        return dest.set(target.position).add(0f, camera.eyeHeight, 0f).fma(camera.forwardOffset, forward)
    }

    /** Puts [camera] on the eye pose of [target] at once (edit mode, a paused Play, a settings edit) and writes [out]. */
    fun snap(camera: FirstPersonCameraComponent, target: FollowTarget, out: Transform) {
        camera.state.initialized = false
        update(camera, target, 0f, out)
    }

    /** Advances [camera] by [dt] seconds toward the eye pose of [target] and writes it to [out]; sharpness <= 0 snaps. */
    fun update(camera: FirstPersonCameraComponent, target: FollowTarget, dt: Float, out: Transform) {
        val s = camera.state
        val eye = eye(camera, target)
        val yaw = FollowCameraRig.yawBehind(target.yaw)
        if (!s.initialized || camera.followSharpness <= 0f) {
            s.initialized = true
            s.position.set(eye)
            s.yaw = yaw
        } else {
            val t = CameraMotion.smoothing(camera.followSharpness, dt)
            s.position.lerp(eye, t)
            val delta = Math.toDegrees(MotionMath.wrapAngle(Math.toRadians((yaw - s.yaw).toDouble()).toFloat()).toDouble()).toFloat()
            s.yaw += delta * t
        }
        out.translation.set(s.position)
        out.rotation.set(camera.pitch, s.yaw, 0f)
    }
}
