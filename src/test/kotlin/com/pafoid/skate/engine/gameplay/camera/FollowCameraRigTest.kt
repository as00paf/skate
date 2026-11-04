package com.pafoid.skate.engine.gameplay.camera

import com.pafoid.skate.engine.ecs.components.FollowCameraComponent
import com.pafoid.skate.engine.ecs.components.FreeFlyCameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.gameplay.CameraPreset
import org.joml.Vector2f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class FollowCameraRigTest {

    private val dt = 1f / 60f
    private val out = Transform()

    private fun camera(block: FollowCameraComponent.() -> Unit = {}) = FollowCameraComponent(
        distance = 4f, pivotHeight = 1.5f, pitch = 10f, followSharpness = 1000f, autoAlign = false,
    ).apply(block)

    private fun target(z: Float = 0f, yaw: Float = 0f, speed: Float = 0f, look: Vector2f = Vector2f(), preset: CameraPreset? = null) =
        FollowTarget(Vector3f(0f, 0f, z), yaw, speed, look, preset)

    private fun armDistance() = out.translation.distance(Vector3f(0f, 1.5f, 0f))

    @Test
    fun SpringArm_ClearProbeKeepsTheDesiredLength() {
        assertEquals(4f, SpringArm.clampedLength(4f, null, 0.5f), 0f)
    }

    @Test
    fun SpringArm_HitShortensTheArmToTheHitButNotBelowTheMinimum() {
        assertEquals(2f, SpringArm.clampedLength(4f, 0.5f, 0.5f), 1e-6f)
        assertEquals(0.5f, SpringArm.clampedLength(4f, 0.01f, 0.5f), 1e-6f)
        // A minimum longer than the arm itself never lengthens it.
        assertEquals(0.3f, SpringArm.clampedLength(0.3f, 0f, 0.5f), 1e-6f)
        assertEquals(4f, SpringArm.clampedLength(4f, Float.NaN, 0.5f), 0f)
    }

    @Test
    fun SpringArm_PullsInAtOnceAndEasesBackOutFrameRateIndependently() {
        assertEquals(1f, SpringArm.smoothed(4f, 1f, 4f, dt), 0f)

        val oneStep = SpringArm.smoothed(1f, 4f, 4f, 0.1f)
        val twoSteps = SpringArm.smoothed(SpringArm.smoothed(1f, 4f, 4f, 0.05f), 4f, 4f, 0.05f)
        assertTrue(oneStep > 1f && oneStep < 4f)
        assertEquals(oneStep, twoSteps, 1e-5f)
    }

    @Test
    fun FollowCamera_FirstFrameSnapsBehindTheTargetLookingTheWayItFaces() {
        val cam = camera()

        FollowCameraRig.update(cam, target(yaw = 0f), dt, null, out)

        // Target faces +Z: the camera is behind (-Z), above the pivot, at the full arm, and looks toward +Z.
        assertTrue(out.translation.z < -3.5f && out.translation.y > 1.5f, "camera at ${out.translation}")
        assertEquals(4f, armDistance(), 1e-4f)
        assertTrue(FreeFlyCameraComponent.forward(out.rotation).z > 0.9f)
        assertEquals(0f, out.translation.x, 1e-4f)
    }

    @Test
    fun FollowCamera_SpringArmStopsAtTheObstacleAndReturnsWhenClear() {
        val cam = camera()
        var wall: Float? = 0.5f
        val probe = SphereProbe { _, _, radius -> assertEquals(cam.probeRadius, radius); wall }

        FollowCameraRig.update(cam, target(), dt, probe, out)
        assertEquals(2f, armDistance(), 1e-3f)

        wall = null
        FollowCameraRig.update(cam, target(), dt, probe, out)
        val easing = armDistance()
        assertTrue(easing > 2f && easing < 3f, "arm should ease back out, was $easing")
        repeat(300) { FollowCameraRig.update(cam, target(), dt, probe, out) }
        assertEquals(4f, armDistance(), 1e-2f)
    }

    @Test
    fun FollowCamera_CollisionOffIgnoresTheProbe() {
        val cam = camera { collision = false }

        FollowCameraRig.update(cam, target(), dt, { _, _, _ -> 0.1f }, out)

        assertEquals(4f, armDistance(), 1e-4f)
    }

    @Test
    fun FollowCamera_ProbeSweepsFromThePivotToTheWantedCameraPosition() {
        val cam = camera()
        var from: Vector3f? = null
        var to: Vector3f? = null

        FollowCameraRig.update(cam, target(), dt, { f, t, _ -> from = Vector3f(f); to = Vector3f(t); null }, out)

        assertEquals(Vector3f(0f, 1.5f, 0f), from)
        assertEquals(out.translation.x, to!!.x, 1e-4f)
        assertEquals(out.translation.z, to!!.z, 1e-4f)
    }

    @Test
    fun FollowCamera_LookInputOrbitsAndPitchStaysWithinItsLimits() {
        val cam = camera { maxPitch = 30f }
        FollowCameraRig.update(cam, target(), dt, null, out)
        val startYaw = out.rotation.y

        FollowCameraRig.update(cam, target(look = Vector2f(90f, 100f)), dt, null, out)

        assertEquals(startYaw + 90f, out.rotation.y, 1e-3f)
        assertEquals(30f, out.rotation.x, 1e-4f)
        assertEquals(4f, armDistance(), 1e-3f)
    }

    @Test
    fun FollowCamera_AutoAlignSwingsBackBehindAMovingTargetAfterTheDelay() {
        val cam = camera { autoAlign = true; autoAlignDelay = 0.5f; autoAlignSharpness = 6f }
        FollowCameraRig.update(cam, target(), dt, null, out)
        FollowCameraRig.update(cam, target(look = Vector2f(90f, 0f)), dt, null, out)
        val turned = out.rotation.y

        // Standing still: the camera keeps the orbit.
        repeat(120) { FollowCameraRig.update(cam, target(), dt, null, out) }
        assertEquals(turned, out.rotation.y, 1e-4f)

        // Moving: after the delay it swings back behind the target.
        repeat(240) { FollowCameraRig.update(cam, target(speed = 3f), dt, null, out) }
        assertTrue(abs(out.rotation.y - FollowCameraRig.yawBehind(0f)) < 1f, "yaw ${out.rotation.y}")
    }

    @Test
    fun FollowCamera_PlayerPresetBlendsDistanceAndPivotHeight() {
        val cam = camera()
        val ride = CameraPreset(distance = 6f, height = 1f)
        FollowCameraRig.update(cam, target(preset = CameraPreset(4f, 1.5f)), dt, null, out)

        FollowCameraRig.update(cam, target(preset = ride), dt, null, out)
        assertTrue(cam.state.distance > 4f && cam.state.distance < 6f)
        repeat(600) { FollowCameraRig.update(cam, target(preset = ride), dt, null, out) }
        assertEquals(6f, cam.state.distance, 1e-3f)
        assertEquals(1f, cam.state.pivot.y, 1e-3f)

        cam.usePlayerPresets = false
        repeat(600) { FollowCameraRig.update(cam, target(preset = ride), dt, null, out) }
        assertEquals(4f, cam.state.distance, 1e-3f)
    }

    @Test
    fun FollowCamera_FollowLagsTheTargetSmoothly() {
        val cam = camera { followSharpness = 5f }
        FollowCameraRig.update(cam, target(z = 0f), dt, null, out)

        FollowCameraRig.update(cam, target(z = 1f), dt, null, out)

        assertTrue(cam.state.pivot.z > 0f && cam.state.pivot.z < 0.2f, "pivot ${cam.state.pivot}")
    }

    @Test
    fun CameraShake_ImpactShakesThenSettlesBackToTheRestPose() {
        val cam = camera()
        FollowCameraRig.update(cam, target(), dt, null, out)
        val rest = Vector3f(out.translation)

        cam.shake(1f)
        val offsets = (1..20).map { FollowCameraRig.update(cam, target(), dt, null, out); out.translation.distance(rest) }
        assertTrue(offsets.max() > 0.02f, "no visible shake: ${offsets.max()}")
        assertTrue(offsets.max() <= cam.shakeMaxOffset * 1.8f)

        repeat(120) { FollowCameraRig.update(cam, target(), dt, null, out) }
        assertEquals(0f, cam.state.trauma, 0f)
        assertEquals(0f, out.translation.distance(rest), 1e-5f)
        assertEquals(0f, out.rotation.z, 0f)
    }

    @Test
    fun CameraShake_TraumaIsCappedAndDecaysLinearly() {
        assertEquals(1f, CameraShake.add(0.8f, 0.5f), 0f)
        assertEquals(0.5f, CameraShake.decay(1f, 5f, 0.1f), 1e-6f)
        assertEquals(0f, CameraShake.decay(0.1f, 5f, 0.1f), 0f)
        assertEquals(Vector3f(), CameraShake.offset(0f, 3f, 15f, 1f))
    }
}
