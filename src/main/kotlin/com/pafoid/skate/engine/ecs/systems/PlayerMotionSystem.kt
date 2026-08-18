package com.pafoid.skate.engine.ecs.systems

import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Quaternion
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.events.JumpPressed
import com.pafoid.skate.engine.events.Landing
import com.pafoid.skate.engine.events.MovementInput
import com.pafoid.skate.engine.events.Takeoff
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.hasComponent
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.utils.Interpolator
import com.pafoid.skate.game.player.PlayerState
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.atan2

class PlayerMotionSystem(
    private val cameraManager: CameraManager,
    private val eventSystem: EventSystem,
) : System(priority = ExecutionPriority.DEFAULT) {

    private val cache = mutableListOf<GameObject>()

    private val tempQuat = Quaternionf()
    private val tempEuler = Vector3f()

    init {
        eventSystem.subscribe<JumpPressed> { onJumpPressed(it) }
        eventSystem.subscribe<Landing> { onLanding(it) }
        eventSystem.subscribe<Takeoff> { onTakeoff(it) }
        eventSystem.subscribe<MovementInput> { onMovementInput(it) }
    }

    private fun onJumpPressed(event: JumpPressed) {
        cache.forEach { go ->
            go.getComponent<PlayerController>()?.jumpPressed = true
        }
    }

    private fun onLanding(event: Landing) {
        cache.forEach { go ->
            go.getComponent<PlayerController>()?.let {
                it.isGrounded = true
                it.isJumping = false
            }
        }
    }

    private fun onTakeoff(event: Takeoff) {
        cache.forEach { go ->
            go.getComponent<PlayerController>()?.let {
                it.isGrounded = false
                it.isJumping = true
            }
        }
    }

    private fun onMovementInput(event: MovementInput) {
        cache.forEach { go ->
            go.getComponent<PlayerController>()?.let {
                it.movementDirection.set(event.direction)
                it.movementMagnitude = event.magnitude
            }
        }
    }

    override fun init(scene: Scene) {
        super.init(scene)
        invalidateCache()
        rebuildCache()
        // TODO: listen for component change action to update cache
    }

    override fun update(dt: Float) {
        if (!scene.isRunning) return
        if (cacheDirty) rebuildCache()

        updateState(dt)

        cache.forEach { go ->
            val body = go.getComponent<RigidBody3D>()
            body?.let { body ->
                applyMotion(go, body)
                go.getComponent<Transform>()?.let { updateTransform(go, it, body.rawBody) }
            }
        }
    }

    private fun updateTransform(go: GameObject, transform: Transform, body: PhysicsRigidBody?) {
        val pos = body?.getPhysicsLocation(null) ?: com.jme3.math.Vector3f()
        val rot = body?.getPhysicsRotation(null) ?: Quaternion()

        transform.translation.set(pos.x, pos.y, pos.z)

        // JME Quaternion to Euler (JOML) — reused temp objects
        tempQuat.set(rot.x, rot.y, rot.z, rot.w)
        tempQuat.getEulerAnglesXYZ(tempEuler)
        transform.rotation.set(
            Math.toDegrees(tempEuler.x.toDouble()).toFloat(),
            Math.toDegrees(tempEuler.y.toDouble()).toFloat(),
            Math.toDegrees(tempEuler.z.toDouble()).toFloat()
        )
    }

    private fun updateState(dt: Float) {
        cache.forEach { gameObject ->
            val controller = gameObject.getComponent<PlayerController>() ?: return@forEach
            val physics = gameObject.getComponent<RigidBody3D>()?.rawBody ?: return@forEach
            val animator = gameObject.getComponent<Animator>() ?: return@forEach

            val intent = controller.desiredMoveDirection.length()
            val hasIntent = intent > 0.15f

            val speed = physics.getLinearVelocity(null).length()
            val newState = if (controller.isJumping) {
                PlayerState.JUMPING
            } else if (!controller.isGrounded) {
                PlayerState.FALLING
            } else if (speed > 0.1f && hasIntent) {
                if (speed > 5f) {
                    PlayerState.RUNNING
                } else {
                    PlayerState.WALKING
                }
            } else {
                PlayerState.IDLE
            }

            if (animator.currentState == newState) return
            animator.currentState = newState
            when (animator.currentState) {
                PlayerState.WALKING -> animator.play("walking")
                PlayerState.RUNNING -> animator.play("running")
                PlayerState.JUMPING -> animator.play("jump")
                PlayerState.FALLING -> animator.play("falling idle")
                PlayerState.LANDING -> animator.play("hard landing")
                PlayerState.IDLE -> animator.play("idle")
            }
        }
    }

    private fun applyMotion(gameObject: GameObject, body: IPhysicsBody3D) {
        val playerController = gameObject.getComponent<PlayerController>()
        val motionData = playerController?.motionData ?: return
        val velocity = body.linearVelocity
        val desiredMoveDirection = getDesiredMoveDirection(
            playerController.desiredMoveDirection,
            Pair(cameraManager.camera.camForward, cameraManager.camera.camRight),
            motionData.inputDirection
        )
        if (desiredMoveDirection.x.isNaN() || desiredMoveDirection.y.isNaN() || desiredMoveDirection.z.isNaN()) {
            //logger.log("Desired move direction is NaN: $desiredMoveDirection")
            return
        }

        velocity.x = desiredMoveDirection.x * motionData.speed
        velocity.z = desiredMoveDirection.z * motionData.speed

        body.linearVelocity = velocity
        playerController.lastSpeed = motionData.speed

        val rotation = body.getRotation()
        val currentYaw = atan2(
            2f * (rotation.w * rotation.y + rotation.x * rotation.z),
            1f - 2f * (rotation.y * rotation.y + rotation.z * rotation.z)
        )

        if (!motionData.targetYaw.isNaN()) {
            val newYaw = Interpolator.lerpAngle(currentYaw, motionData.targetYaw, motionData.rotationSpeed)

            playerController.desiredRotation.set(Quaternionf().rotateY(newYaw))
            body.setRotation(playerController.desiredRotation)
        }

        //Jump
        with(playerController) {
            if (isJumping && isGrounded && jumpTimer <= 0f) { // Jump
                //logger.log("JUMP TIMER FINISHED!")
                body.applyImpulse(Vector3f(0f, jumpImpulse, 0f))
                jumpTimer = takeOffTime
            }
        }
    }

    private fun getDesiredMoveDirection(
        desiredMoveDirection: Vector3f,
        camForwardAndRight: Pair<Vector3f, Vector3f>,
        input: Vector2f
    ): Vector3f {
        desiredMoveDirection.zero()
        camForwardAndRight.first.mul(input.y, desiredMoveDirection)
        val rightPart = Vector3f(camForwardAndRight.second).mul(input.x)
        desiredMoveDirection.add(rightPart)

        return desiredMoveDirection.normalize()
    }

    override fun invalidateCache() {
        cache.clear()
        cacheDirty = true
    }

    override fun rebuildCache() {
        cache.addAll(
            scene.children.filter { it.hasComponent<PlayerController>() && it.hasComponent<IPhysicsBody3D>() }
        )
        cacheDirty = false
    }
}