package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.gameplay.ActionQuery
import com.pafoid.skate.engine.gameplay.CharacterInput
import com.pafoid.skate.engine.gameplay.LookTuning
import com.pafoid.skate.engine.gameplay.CharacterInputSampler
import com.pafoid.skate.engine.gameplay.CharacterMotion
import com.pafoid.skate.engine.gameplay.MotionMath
import com.pafoid.skate.engine.gameplay.MountMath
import com.pafoid.skate.engine.gameplay.MountRequest
import com.pafoid.skate.engine.gameplay.Mountable
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.gameplay.ground.GroundMotion
import com.pafoid.skate.engine.gameplay.ik.FootIkBinder
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f

/**
 * Thin driver: feeds input to each player's behavior and applies the returned motion to its body.
 * With an [inputSampler], every top-level [InputStateComponent] is filled from the character actions first;
 * without one (tests), the components are used as they are.
 */
class PlayerMotionSystem(
    private val cameraManager: CameraManager,
    private val inputSampler: CharacterInputSampler? = null,
) : System(priority = ExecutionPriority.DEFAULT) {

    private val footBinders = java.util.WeakHashMap<PlayerController, FootIkBinder>()

    private val players = query(PlayerController::class, IPhysicsBody3D::class, topLevelOnly = true)
    private val inputs = query(InputStateComponent::class, topLevelOnly = true)

    /** Per-character action views (stateless wrappers, cached to avoid a per-frame allocation). */
    private val actionViews = java.util.WeakHashMap<PlayerController, ActionQuery>()
    private val seen = HashSet<Any>()

    override fun init(scene: Scene) {
        super.init(scene)
        // A new scene (Play snapshot, restart, scene switch): game presets' actions, and no context left held.
        inputSampler?.applyPresets()
        inputSampler?.leases?.releaseAll()
    }

    override fun update(dt: Float) {
        if (!scene.isRunning) {
            inputSampler?.leases?.releaseAll()
            return
        }
        inputSampler?.let { sampler ->
            inputs.forEach { go ->
                val state = go.getComponent<InputStateComponent>() ?: return@forEach
                // Each character's own look tuning (its config); objects without a controller get the defaults.
                sampler.sample(state, go.getComponent<PlayerController>()?.behavior?.look ?: LookTuning())
            }
        }

        seen.clear()
        players.forEach { go ->
            val controller = go.getComponent<PlayerController>() ?: return@forEach
            val body = go.getComponent<RigidBody3D>() ?: return@forEach
            val inputState = go.getComponent<InputStateComponent>() ?: return@forEach
            seen += controller

            val mount = (controller.behavior as? Mountable)?.let { findMount(it.mountTargetName, go) }
            val motion = controller.behavior.update(buildInput(controller, inputState, body, mount, body.getWorldPosition()), dt)
            apply(motion, body, controller, dt)
            val mountable = controller.behavior as? Mountable
            if (mountable != null && mount != null) {
                if (motion.mount == MountRequest.MOUNT) snapRiderOnto(mountable, mount, body)
                syncMount(mountable, mount, body)
            }

            go.getComponent<Animator>()?.let { animator ->
                val footIk = footBinders.getOrPut(controller) { FootIkBinder(controller.footIk) }
                val riding = (controller.behavior as? Mountable)?.mounted == true
                footIk.update(animator.ik, controller.footTargets, controller.ground.standing && !riding, dt)
            }

            controller.state = CharacterAnimationDriver.drive(controller.behavior, motion, controller.state, go.getComponent<Animator>())
        }
        // Characters that left the scene give their input contexts back.
        inputSampler?.leases?.retainOnly(seen)
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
            grounded = (controller.behavior as? Mountable).let { m ->
                MountMath.grounded(controller.ground, m?.mounted == true, m?.rideHeight ?: 0f, controller.behavior.ground.groundedDistance)
            },
            horizontalSpeed = speed,
            verticalSpeed = velocity.y,
            groundTag = controller.groundTag,
            velocityYaw = if (speed > MIN_HEADING_SPEED) kotlin.math.atan2(velocity.x, velocity.z) else Float.NaN,
            yaw = MotionMath.yawOfRotation(body.getRotation()),
            cameraForward = Vector3f(camera.camForward),
            cameraRight = Vector3f(camera.camRight),
            actions = inputSampler?.let { s -> actionViews.getOrPut(controller) { s.actionsFor(controller) } } ?: ActionQuery.NONE,
        )
    }

    private fun findMount(name: String, self: GameObject): RigidBody3D? =
        findMountObject(scene, name, self)?.getComponent<RigidBody3D>()

    /** On mount, the rider steps onto the deck: takes the board's heading and stands so the board sits at the mount offset. */
    private fun snapRiderOnto(mountable: Mountable, mount: RigidBody3D, character: RigidBody3D) {
        val yaw = MotionMath.yawOfRotation(mount.getRotation())
        character.rawBody?.setLocation(MountMath.riderFeet(mount.getWorldPosition(), yaw, mountable.mountOffset))
        character.setRotation(Quaternionf().rotateY(yaw))
        character.linearVelocity = Vector3f()
        character.angularVelocity = Vector3f()
    }

    /**
     * Keeps the mount consistent with the character: ridden, it is a kinematic body carried at the feet with the
     * character's heading that collides with nothing; otherwise it is a normal dynamic body left where it was.
     * The rendered board is placed again from the final rider pose by [MountFollowSystem].
     */
    private fun syncMount(mountable: Mountable, mount: RigidBody3D, character: RigidBody3D) {
        mount.collisionLayer = MountMath.MOUNT_LAYER
        val characterMask = MountMath.characterMask(character.collisionMask)
        if (character.collisionMask != characterMask) character.collisionMask = characterMask

        val state = MountMath.mountBodyState(mountable.mounted)
        if (mount.collisionMask != state.collisionMask) mount.collisionMask = state.collisionMask
        mount.rawBody?.let { if (it.isKinematic != state.kinematic) it.isKinematic = state.kinematic }
        if (!mountable.mounted) return

        val yaw = MotionMath.yawOfRotation(character.getRotation())
        mount.rawBody?.setLocation(MountMath.mountPosition(character.getWorldPosition(), yaw, mountable.mountOffset))
        mount.setRotation(Quaternionf().rotateY(yaw))
        mount.linearVelocity = Vector3f()
        mount.angularVelocity = Vector3f()
    }

    /** Follows slopes, climbs steps and snaps to the ground by adjusting vertical velocity. */
    private fun applyGround(motion: CharacterMotion, body: IPhysicsBody3D, controller: PlayerController, dt: Float) {
        val velocity = body.linearVelocity
        val mountable = controller.behavior as? Mountable
        if (mountable != null && mountable.mounted && motion.impulse == null) {
            MountMath.hoverVelocity(controller.ground.found, controller.ground.distance, mountable.rideHeight)?.let {
                velocity.y = it
                body.linearVelocity = velocity
                return
            }
        }
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

/** The top-level object named [name] other than [self]: the mount a [Mountable] character refers to. */
internal fun findMountObject(scene: GameObject, name: String, self: GameObject): GameObject? =
    scene.children.firstOrNull { it !== self && it.name == name }
