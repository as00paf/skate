package com.pafoid.skate.engine.gameplay.camera

import com.pafoid.skate.engine.ecs.components.CameraFocusComponent
import com.pafoid.skate.engine.ecs.components.FollowCameraComponent
import com.pafoid.skate.engine.ecs.components.FreeFlyCameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.gameplay.CameraPreset
import com.pafoid.skate.engine.utils.CameraMotion
import org.joml.Vector2f
import org.joml.Vector3f

/** What the camera follows this frame. */
data class FollowTarget(
    /** World position of the target's origin (feet). */
    val position: Vector3f,
    /** Facing in radians, 0 = +Z (MotionMath convention). */
    val yaw: Float,
    /** Horizontal speed in m/s. */
    val speed: Float = 0f,
    /** Look input in degrees this frame (x: turn right, y: look down). */
    val look: Vector2f = Vector2f(),
    /** Framing asked by the target's behaviour, or null. */
    val preset: CameraPreset? = null,
)

/** Sweeps a sphere of [radius] from [from] to [to]; returns the hit fraction (0..1) or null when clear. */
fun interface SphereProbe {
    fun cast(from: Vector3f, to: Vector3f, radius: Float): Float?
}

/** Follow/orbit/spring-arm/shake maths of [FollowCameraComponent], without GL or physics (see [SphereProbe]). */
object FollowCameraRig {

    /** Camera yaw (degrees, [Transform.lookAt] convention) that looks the way a target facing [targetYaw] faces. */
    fun yawBehind(targetYaw: Float): Float = 180f - Math.toDegrees(targetYaw.toDouble()).toFloat()

    /** Advances [camera] by [dt] seconds and writes the camera pose to [out]. */
    fun update(camera: FollowCameraComponent, target: FollowTarget, dt: Float, probe: SphereProbe?, out: Transform) {
        val s = camera.state
        val step = CameraMotion.clampDt(dt)
        val wantDistance = target.preset?.takeIf { camera.usePlayerPresets }?.distance ?: camera.distance
        val wantHeight = target.preset?.takeIf { camera.usePlayerPresets }?.height ?: camera.pivotHeight
        val desiredPivot = Vector3f(target.position).add(0f, wantHeight, 0f)

        if (!s.initialized) {
            s.initialized = true
            s.yaw = yawBehind(target.yaw)
            s.pitch = camera.pitch.coerceIn(camera.minPitch, camera.maxPitch)
            s.distance = wantDistance
            s.pivotHeight = wantHeight
            s.pivot.set(desiredPivot)
            s.armLength = wantDistance
            s.idle = 0f
            s.time = 0f
        } else {
            val preset = CameraMotion.smoothing(camera.presetSharpness, step)
            s.distance += (wantDistance - s.distance) * preset
            s.pivotHeight += (wantHeight - s.pivotHeight) * preset
            s.pivot.lerp(Vector3f(target.position).add(0f, s.pivotHeight, 0f), CameraMotion.smoothing(camera.followSharpness, step))
        }

        orbit(camera, target, step)

        // Spring arm: back from the pivot along the view direction.
        val forward = FreeFlyCameraComponent.forward(Vector3f(s.pitch, s.yaw, 0f))
        val wanted = Vector3f(s.pivot).sub(Vector3f(forward).mul(s.distance))
        val allowed = if (camera.collision && probe != null) {
            SpringArm.clampedLength(s.distance, probe.cast(s.pivot, wanted, camera.probeRadius), camera.minDistance)
        } else s.distance
        s.armLength = SpringArm.smoothed(s.armLength, allowed, camera.returnSharpness, step)

        // Shake.
        s.time += step
        s.trauma = CameraShake.decay(s.trauma, camera.shakeDecay, step)
        val shake = CameraShake.offset(s.trauma, s.time, camera.shakeFrequency, camera.shakeMaxOffset)

        out.translation.set(s.pivot).sub(forward.mul(s.armLength)).add(shake)
        out.rotation.set(s.pitch, s.yaw, CameraShake.roll(s.trauma, s.time, camera.shakeFrequency, camera.shakeMaxRoll))
    }

    private fun orbit(camera: FollowCameraComponent, target: FollowTarget, dt: Float) {
        val s = camera.state
        if (target.look.x != 0f || target.look.y != 0f) {
            s.yaw += target.look.x * camera.orbitSensitivity
            s.pitch = (s.pitch + target.look.y * camera.orbitSensitivity).coerceIn(camera.minPitch, camera.maxPitch)
            s.idle = 0f
            return
        }
        s.idle += dt
        if (camera.autoAlign && s.idle >= camera.autoAlignDelay && target.speed >= camera.autoAlignMinSpeed) {
            val k = CameraMotion.smoothing(camera.autoAlignSharpness, dt)
            s.yaw += CameraFocusComponent.deltaDegrees(s.yaw, yawBehind(target.yaw)) * k
            s.pitch += (camera.pitch.coerceIn(camera.minPitch, camera.maxPitch) - s.pitch) * k
        }
    }
}
