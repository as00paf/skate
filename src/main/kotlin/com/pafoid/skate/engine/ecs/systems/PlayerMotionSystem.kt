package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.gameplay.CharacterInput
import com.pafoid.skate.engine.gameplay.CharacterMotion
import com.pafoid.skate.engine.gameplay.MotionMath
import com.pafoid.skate.engine.gameplay.MountRequest
import com.pafoid.skate.engine.gameplay.Mountable
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.gameplay.ground.GroundMotion
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f

/** Thin driver: feeds input to each player's behavior and applies the returned motion to its body. */
class PlayerMotionSystem(
    private val cameraManager: CameraManager,
) : System(priority = ExecutionPriority.DEFAULT) {

    private val players = query(PlayerController::class, IPhysicsBody3D::class, topLevelOnly = true)

    override fun update(dt: Float) {
        if (!scene.isRunning) return

        players.forEach { go ->
            val controller = go.getComponent<PlayerController>() ?: return@forEach
            val body = go.getComponent<RigidBody3D>() ?: return@forEach
            val inputState = go.getComponent<InputStateComponent>() ?: return@forEach

            val mount = (controller.behavior as? Mountable)?.let { findMount(it.mountTargetName, go) }
            val motion = controller.behavior.update(buildInput(controller, inputState, body, mount, body.getWorldPosition()), dt)
            apply(motion, body, controller, dt)
            if (motion.mount != MountRequest.NONE) mount?.let { placeMount(motion.mount, controller.behavior as Mountable, it, body) }

            if (motion.state != controller.state) {
                controller.state = motion.state
                motion.animation?.let { clip -> go.getComponent<Animator>()?.play(clip) }
            }
        }
    }

    private fun buildInput(
        controller: PlayerController,
        input: InputStateComponent,
        body: IPhysicsBody3D,
        mount: RigidBody3D?,
        position: Vector3f,
    ): CharacterInput {
        val velocity = body.linearVelocity
        val camera = cameraManager.camera
        val speed = Vector2f(velocity.x, velocity.z).length()
        return CharacterInput(
            move = Vector2f(input.moveDirection),
            sprint = input.sprintPressed,
            jumpPressed = input.jumpPressed,
            jumpHeld = input.jumpHeld,
            mountPressed = input.mountPressed,
            mountDistance = mount?.getWorldPosition()?.distance(position) ?: Float.NaN,
            crouch = input.crouchPressed,
            grounded = controller.isGrounded,
            horizontalSpeed = speed,
            velocityYaw = if (speed > MIN_HEADING_SPEED) kotlin.math.atan2(velocity.x, velocity.z) else Float.NaN,
            yaw = MotionMath.yawOfRotation(body.getRotation()),
            cameraForward = Vector3f(camera.camForward),
            cameraRight = Vector3f(camera.camRight),
        )
    }

    private fun findMount(name: String, self: GameObject): RigidBody3D? =
        scene.children.firstOrNull { it !== self && it.name == name }?.getComponent<RigidBody3D>()

    /** Mounting puts the mount under the character's feet; dismounting leaves it where it is. */
    private fun placeMount(request: MountRequest, mountable: Mountable, mount: RigidBody3D, body: IPhysicsBody3D) {
        if (request != MountRequest.MOUNT) return
        val feet = body.getWorldPosition().add(mountable.mountOffset)
        mount.rawBody?.setLocation(feet)
        mount.setRotation(Quaternionf().rotateY(MotionMath.yawOfRotation(body.getRotation())))
        mount.linearVelocity = Vector3f()
        mount.angularVelocity = Vector3f()
    }

    /** Follows slopes, climbs steps and snaps to the ground by adjusting vertical velocity. */
    private fun applyGround(motion: CharacterMotion, body: IPhysicsBody3D, controller: PlayerController, dt: Float) {
        val velocity = body.linearVelocity
        val vy = GroundMotion.verticalVelocity(
            config = controller.behavior.ground,
            ground = controller.ground,
            step = controller.stepAhead,
            wasGrounded = controller.wasGrounded,
            currentVy = velocity.y,
            horizontalX = velocity.x,
            horizontalZ = velocity.z,
            jumping = motion.impulse != null,
            dt = dt,
        ) ?: return
        velocity.y = vy
        body.linearVelocity = velocity
    }

    private companion object {
        const val MIN_HEADING_SPEED = 0.5f
    }

    private fun apply(motion: CharacterMotion, body: IPhysicsBody3D, controller: PlayerController, dt: Float) {
        motion.horizontalVelocity?.let { target ->
            if (!target.x.isNaN() && !target.z.isNaN()) {
                val velocity = body.linearVelocity
                velocity.x = target.x
                velocity.z = target.z
                body.linearVelocity = velocity
            }
        }
        applyGround(motion, body, controller, dt)

        if (!motion.targetYaw.isNaN()) {
            val current = MotionMath.yawOfRotation(body.getRotation())
            val yaw = if (motion.snapYaw) motion.targetYaw
            else MotionMath.smoothAngle(current, motion.targetYaw, motion.yawRate, dt)
            body.setRotation(Quaternionf().rotateY(yaw))
        }

        motion.impulse?.let { body.applyImpulse(it) }
    }
}
