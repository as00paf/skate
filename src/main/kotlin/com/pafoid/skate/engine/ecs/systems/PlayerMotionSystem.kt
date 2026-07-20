package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.config.ExecutionPriority
import com.pafoid.skate.engine.events.JumpPressed
import com.pafoid.skate.engine.events.Landing
import com.pafoid.skate.engine.events.MovementInput
import com.pafoid.skate.engine.events.Takeoff
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.hasComponent
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.render.CameraManager
import com.pafoid.skate.engine.utils.Interpolator
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.atan2

class PlayerMotionSystem(
    private val cameraManager: CameraManager,
    private val eventSystem: EventSystem,
    private val logger: LoggerService
) : System(priority = ExecutionPriority.DEFAULT) {

    private val gameObjects = mutableListOf<GameObject>()

    init {
        eventSystem.subscribe<JumpPressed> { onJumpPressed(it) }
        eventSystem.subscribe<Landing> { onLanding(it) }
        eventSystem.subscribe<Takeoff> { onTakeoff(it) }
        eventSystem.subscribe<MovementInput> { onMovementInput(it) }
    }

    private fun onJumpPressed(event: JumpPressed) {
        gameObjects.forEach { go ->
            go.getComponent<PlayerController>()?.jumpPressed = true
        }
    }

    private fun onLanding(event: Landing) {
        gameObjects.forEach { go ->
            go.getComponent<PlayerController>()?.let {
                it.isGrounded = true
                it.isJumping = false
            }
        }
    }

    private fun onTakeoff(event: Takeoff) {
        gameObjects.forEach { go ->
            go.getComponent<PlayerController>()?.let {
                it.isGrounded = false
                it.isJumping = true
            }
        }
    }

    private fun onMovementInput(event: MovementInput) {
        gameObjects.forEach { go ->
            go.getComponent<PlayerController>()?.let {
                it.movementDirection.set(event.direction)
                it.movementMagnitude = event.magnitude
            }
        }
    }

    override fun init(scene: Scene) {
        super.init(scene)
        gameObjects.clear()
        gameObjects.addAll(
            scene.gameObjects.filter { it.hasComponent<PlayerController>() && it.hasComponent<IPhysicsBody3D>() }
        )
        // TODO: listen for component change action to update cache
    }

    override fun update(dt: Float) {
        if (!scene.isRunning) return
        scene.gameObjects
            .filter { it.hasComponent<PlayerController>() && it.hasComponent<IPhysicsBody3D>() }
            .forEach {
            val body = it.getComponent<IPhysicsBody3D>()
            body?.let { body -> applyMotion(it, body) }
        }
    }

    private fun applyMotion(gameObject: GameObject, body: IPhysicsBody3D) {
        val playerController = gameObject.getComponent<PlayerController>()
        val motionData = playerController?.motionData ?: return
        val velocity = body.linearVelocity
        val camForwardAndRight = cameraManager.camera.getForwardAndRight()
        val desiredMoveDirection = getDesiredMoveDirection(
            playerController.desiredMoveDirection,
            camForwardAndRight,
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
}