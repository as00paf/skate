package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.ComponentSettings
import com.pafoid.skate.engine.ecs.components.FollowCameraComponent
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.events.Collision
import com.pafoid.skate.engine.gameplay.Mountable
import com.pafoid.skate.engine.gameplay.MotionMath
import com.pafoid.skate.engine.gameplay.camera.FollowCameraRig
import com.pafoid.skate.engine.gameplay.camera.FollowTarget
import com.pafoid.skate.engine.gameplay.camera.SphereProbe
import com.pafoid.skate.engine.getComponent
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f

/** Sphere sweep for a camera arm that ignores [exclude] (the followed object's own body); fraction or null. */
fun interface CameraProbe {
    fun cast(from: Vector3f, to: Vector3f, radius: Float, exclude: GameObject): Float?
}

/**
 * Drives every [FollowCameraComponent] while the scene runs. The target is found by name among the scene's top-level
 * objects each frame, so it also works for scenes loaded from disk. Its pose, speed, look input
 * ([InputStateComponent.cameraLook]) and walking/riding preset ([Mountable.cameraPreset]) feed [FollowCameraRig].
 * Runs after the rider and board have moved and before world matrices are rebuilt, so the view uses this frame's pose.
 * In edit mode the camera stays where it was placed, and the next Play snaps behind the target; in a paused Play it
 * holds still. In every mode an edit of the camera's settings shows on the next frame (ECS-21): the camera snaps to
 * the rest pose for the new settings instead of easing toward it (or not moving). The first frame a camera is seen
 * only records its settings, so loading a scene never moves a placed camera.
 */
class FollowCameraSystem(
    private val probe: CameraProbe? = null,
    eventSystem: EventSystem? = null,
) : System() {

    override val phase = SystemPhase.LATE_UPDATE
    override val runsAfter = listOf(MountFollowSystem::class)
    override val runsBefore = listOf(TransformSystem::class)
    override val runsWhilePaused: Boolean get() = true

    private val cameras = query(FollowCameraComponent::class, Transform::class)
    private val impacts = ArrayList<Collision>()

    init {
        eventSystem?.subscribe<Collision> { impacts += it }
    }

    override fun update(dt: Float) {
        val running = scene.isRunning
        val paused = running && scene.isPaused
        cameras.forEach { go ->
            val camera = go.getComponent<FollowCameraComponent>() ?: return@forEach
            val transform = go.getComponent<Transform>() ?: return@forEach
            val settings = ComponentSettings.of(camera, RUNTIME_FIELDS)
            val edited = camera.state.settings.let { it != null && it != settings }
            camera.state.settings = settings
            if (!running) camera.state.initialized = false // the next Play snaps
            if (!camera.enabled) return@forEach
            if (!running && !edited) return@forEach
            if (paused && !edited) return@forEach
            val target = scene.findFirstByName(camera.targetName, exclude = go) ?: return@forEach
            val sphere = probe?.let { p -> SphereProbe { from, to, r -> p.cast(from, to, r, target) } }
            val pose = followTarget(target)
            if (running && !paused) {
                impacts.forEach { if (it.self === target && it.impulse >= camera.impactShakeImpulse) camera.shake(camera.impactShake) }
            }
            if (edited) {
                FollowCameraRig.snap(camera, pose, sphere, transform)
                if (!running) camera.state.initialized = false
            } else {
                FollowCameraRig.update(camera, pose, dt, sphere, transform)
            }
        }
        impacts.clear()
    }

    companion object {
        /** Runtime state, not settings. */
        private val RUNTIME_FIELDS = setOf("state")

        /** The follow inputs of [target]: top-level position, facing, horizontal speed, look input and preset. */
        fun followTarget(target: GameObject): FollowTarget {
            val transform = target.getComponent<Transform>()
            val body = target.getComponent<RigidBody3D>()?.takeIf { it.rawBody != null }
            val rotation = body?.getRotation() ?: transform?.rotation?.let { r ->
                Quaternionf().rotateXYZ(Math.toRadians(r.x.toDouble()).toFloat(), Math.toRadians(r.y.toDouble()).toFloat(), Math.toRadians(r.z.toDouble()).toFloat())
            } ?: Quaternionf()
            val velocity = body?.linearVelocity ?: Vector3f()
            return FollowTarget(
                position = Vector3f(transform?.translation ?: Vector3f()),
                yaw = MotionMath.yawOfRotation(rotation),
                speed = Vector2f(velocity.x, velocity.z).length(),
                look = Vector2f(target.getComponent<InputStateComponent>()?.cameraLook ?: Vector2f()),
                preset = (target.getComponent<PlayerController>()?.behavior as? Mountable)?.cameraPreset,
            )
        }
    }
}
