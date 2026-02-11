package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.animation.Animator
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.controls.input.IInputBuffer
import com.pafoid.skate.engine.controls.input.IInputProvider
import com.pafoid.skate.engine.controls.listeners.GamepadConstants.AXIS_LEFT_X
import com.pafoid.skate.engine.controls.listeners.GamepadConstants.AXIS_LEFT_Y
import com.pafoid.skate.engine.controls.listeners.GamepadConstants.AXIS_RIGHT_TRIGGER
import com.pafoid.skate.engine.controls.listeners.GamepadConstants.BUTTON_A
import com.pafoid.skate.engine.controls.listeners.GamepadConstants.BUTTON_Y
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.player.PlayerState
import com.pafoid.skate.engine.player.PlayerStateManager
import com.pafoid.skate.engine.prefabs.PrefabsGenerator
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.utils.Interpolation
import com.pafoid.skate.engine.utils.JmeVector3f
import com.pafoid.skate.engine.utils.StringManager
import com.pafoid.skate.skateboard.PreferredStance
import com.pafoid.skate.skateboard.Stance
import imgui.ImGui
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1
import org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_Y
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToLong

class PlayerController : Component(), KoinComponent {
    private val inputBuffer: IInputBuffer by inject()
    private val resourceManager: ResourceManager by inject()
    private val inputProvider: IInputProvider by inject()
    private val prefabsGenerator: PrefabsGenerator by inject()
    private val sceneManager: SceneManager by inject()
    private val stringManager: StringManager by inject()

    var preferredStance = PreferredStance.REGULAR
    var pushForce = 5.0f
    var steerSpeed = 2.0f
    var jumpImpulse = 10.0f
    var flickSensitivity = 5.0f
    var catchStrength = 0.5f
    
    var walkSpeed = 3.0f

    lateinit var stateManager: PlayerStateManager

    var currentStance = Stance.REGULAR
    var isSwitch = false
    
    private var rb: IPhysicsBody3D? = null
    private var physics: SkateboardPhysics? = null
    private var lastVelocity = JmeVector3f()
    private var animator: Animator? = null
    private var skater: GameObject? = null
    private var currentLean = 0f
    private val maxLeanAngle = 20f
    private val leanSmoothness = 5f

    private val stanceMultiplier: Float
        get() = if (preferredStance == PreferredStance.REGULAR) 1f else -1f

    override fun start() {
        rb = gameObject.getComponent<RigidBody3D>()
        physics = gameObject.getComponent<SkateboardPhysics>()
        
        // Find animator and skater object
        skater = gameObject.children.find { it.name == "Skater" }
        skater?.let { s: GameObject ->
            animator = s.getComponent<Animator>()
        }
        stateManager = PlayerStateManager(this)
    }

    override fun update(dt: Float) {
        handleStateToggle()
        stateManager.update(dt)

        val vel = rb?.linearVelocity
        if (vel != null) {
            lastVelocity.set(vel.x, vel.y, vel.z)
        }
    }

    /**
     * Checks if the skateboard is currently in motion.
     * @return True if the linear velocity's length is above a small threshold, false otherwise.
     */
    fun isMoving(): Boolean {
        return (rb?.linearVelocity?.length() ?: 0f) > 0.1f
    }

    /**
     * Checks if the player is actively providing push input.
     * @return True if keyboard or controller push inputs are detected, false otherwise.
     */
    fun isPushing(): Boolean {
        var multiplier = 0f

        inputProvider.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
            if (axes.size > AXIS_LEFT_Y) {
                val stickY = -axes[AXIS_LEFT_Y]
                if (stickY > 0.1f) {
                    multiplier = max(multiplier, stickY)
                }
            }
            if (axes.size > AXIS_RIGHT_TRIGGER) {
                val rt = (axes[AXIS_RIGHT_TRIGGER] + 1f) / 2f
                if (rt > 0.1f) {
                    multiplier = max(multiplier, rt)
                }
            }
        }
        return multiplier > 0f
    }

    /**
     * Enforces the skater model's position and orientation relative to the board,
     * ensuring it remains snapped to the deck.
     */
    fun handleStability() {
        val s = skater ?: return
        val sTransform = s.getComponent<com.pafoid.skate.engine.scenes.components.Transform>()
        // Force snap to board top center
        sTransform?.translation?.set(0f, 0.02f, 0f)

        // Face sideways relative to board (90 degrees)
        sTransform?.rotation?.set(0f, 90f, 0f)
    }

    /**
     * Updates the skater's animation to a standard riding pose.
     * @param dt Delta time for animation blending (currently unused but good practice).
     */
    fun updateRidingAnimation(dt: Float) {
        val anim = animator ?: return
        
        // Try to find "ride" or "idle" for riding, otherwise use the first available
        anim.play("Ride", 0.2f)
        
        // If it's a static pose, we might want to pause it at frame 0
        // but for now let's let it play to see what it is.
    }

    /**
     * Procedurally rotates the skater's spine bones based on steering input to create a leaning effect.
     * @param dt Delta time for smooth interpolation.
     */
    fun updateProceduralLean(dt: Float) {
    if (stateManager.currentState !is PlayerState.RIDING) return
    val renderComponent = skater?.getComponent<RenderComponent>() ?: return
    val skeletonComponent = skater?.getComponent<SkeletonComponent>() ?: return
        val skeleton = skeletonComponent.pose?.skeleton ?: return

        var steerInput = 0f
        inputProvider.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
            if (axes.size > AXIS_LEFT_X) {
                steerInput = axes[AXIS_LEFT_X]
            }
        }

        // Target lean based on steering
        val targetLean = -steerInput * maxLeanAngle * stanceMultiplier
        currentLean = Interpolation.lerp(currentLean, targetLean, leanSmoothness * dt)

        // Apply to spine joints
        val spineNames = listOf("Spine", "Spine1", "Spine2")
        val leanPerJoint = currentLean / spineNames.size
        
        val rotationQuat = Quaternionf().rotateZ(Math.toRadians(leanPerJoint.toDouble()).toFloat())

        spineNames.forEach { name ->
            // Multiply current local rotation by procedural lean
            // Since james model is facing sideways, lean might need to be on a different axis
            // Based on standard Mixamo: X is usually pitch, Y is yaw, Z is roll (side lean)
            skeleton.getBoneByName(name)?.localTransform?.rotate(rotationQuat)
        }
    }

    /**
     * Handles character movement when in the 'WALKING' state, including input processing,
     * camera-relative movement, and animation triggering.
     * @param dt Delta time.
     */
    fun handleWalking(dt: Float) {
        val scene = sceneManager.currentScene ?: return
        val camera = scene.camera
        val target = skater ?: return
        
        var moveX = 0f
        var moveZ = 0f
        
        // LS Input
        inputProvider.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
            if (axes.size > AXIS_LEFT_Y) {
                moveZ = -axes[AXIS_LEFT_Y]
                moveX = axes[AXIS_LEFT_X]
            }
        }

        val moveInput = Vector3f(moveX, 0f, moveZ)
        if (moveInput.length() > 1f) moveInput.normalize()

        target.getComponent<Transform>()?.let{ transform ->
            if (moveInput.length() > 0.1f) {
                // Calculate movement relative to camera
                val viewInv = camera.getInverseView()
                val camForward = Vector3f(0f, 0f, -1f)
                viewInv.transformDirection(camForward)
                camForward.y = 0f
                camForward.normalize()

                val camRight = Vector3f(1f, 0f, 0f)
                viewInv.transformDirection(camRight)
                camRight.y = 0f
                camRight.normalize()

                val moveDir = Vector3f()
                camForward.mul(moveInput.z, moveDir)
                val rightPart = Vector3f(camRight).mul(moveInput.x)
                moveDir.add(rightPart)

                // Apply movement to transform
                val velocity = Vector3f(moveDir).mul(walkSpeed * dt)
                transform.translation.add(velocity)

                // Face movement direction
                val targetRotationY = Math.toDegrees(atan2(moveDir.x.toDouble(), moveDir.z.toDouble())).toFloat()
                transform.rotation.y = Interpolation.lerp(transform.rotation.y, targetRotationY, 10f * dt)

                animator?.play("walk", 0.2f)
            } else {
                animator?.play("idle", 0.2f)
            }
        }
        
        // Jump Button A
        if (inputProvider.buttonBeginPress(GLFW_JOYSTICK_1, BUTTON_A) || inputProvider.keyBeginPress(GLFW_KEY_SPACE)) {
            animator?.play("jump", 0.1f)
            // Note: Since unparented, board doesn't jump. Character just plays anim.
            // In a full controller, we'd add vertical velocity to the character transform.
        }
    }

    /**
     * Raycasts downwards to snap the skater model to the ground while in the 'WALKING' state.
     * Prevents floating or clipping through terrain.
     */
    fun handleGroundSnapping() {
        val scene = sceneManager.currentScene ?: return
        val target = skater ?: return
        val pos = target.getComponent<Transform>()?.translation ?: return
        
        val rayStart = Vector3f(pos.x, pos.y + 1f, pos.z)
        val rayEnd = Vector3f(pos.x, pos.y - 2f, pos.z)
        
        val closest = scene.physics3d.raycastClosest(rayStart, rayEnd)
        if (closest != null) {
            val hitY = rayStart.y + (rayEnd.y - rayStart.y) * closest.hitFraction
            pos.y = hitY
        }
    }

    /**
     * Transitions the player into a "Ragdoll" state (currently represented by a Tumble Cube).
     * This disables the main player controller and spawns a physics-driven object
     * that inherits the player's velocity.
     */
    private fun bail() {
        // Transition to Tumble Cube
        val scene = sceneManager.currentScene ?: return
        
        // Find the skater child
        val skater = gameObject.children.find { it.name == "Skater" }
        
        val baseModel = resourceManager.loadModelSync(Assets.Models.CUBE)
        val tumbleCube = prefabsGenerator.generateEntityObject(
            baseModel.mesh[0].rawModel,
            resourceManager.loadTextureSync(Assets.Textures.DEFAULT),
            "TumbleCube"
        )

        val tumbleCubeTransform = gameObject.getComponent<Transform>()
        tumbleCubeTransform?.let { transform ->
            gameObject.getComponent<Transform>()?.let{
                transform.translation.set(it.translation)
                transform.rotation.set(it.rotation)
            }
        }

        
        val cubeRb = RigidBody3D(mass = 5f)
        tumbleCube.addComponent(cubeRb)

        val cubeCollider = BoxCollider3D()
        cubeCollider.halfExtents.set(0.5f, 0.5f, 0.5f)
        tumbleCube.addComponent(cubeCollider)

        scene.addGameObjectToScene(tumbleCube)
        // Add to physics immediately so we can set velocity
        scene.physics3d.add(tumbleCube)

        // Reparent skater to the tumble cube
        skater?.getComponent<Transform>()?.let { transform ->
            tumbleCube.addChild(skater)
            // Reset local transform relative to cube
            transform.translation.set(0f, 0f, 0f)
            transform.rotation.set(0f, 0f, 0f)
            transform.scale.set(1f, 1f, 1f) // Adjust scale if needed, since cube is 1.0
        }
        
        // Inherit velocity
        val linVel = rb?.linearVelocity ?: Vector3f()
        val angVel = rb?.angularVelocity ?: Vector3f()
        
        cubeRb.linearVelocity = linVel
        cubeRb.angularVelocity = angVel
        
        // Disable this controller
        this.enabled = false
        physics?.enabled = false
    }
    
    /**
     * Checks for input (Y Button or Key) to toggle between WALKING and RIDING states.
     * Manages reparenting logic, physics enabling/disabling, and transform adjustments.
     */
    private fun handleStateToggle() {
        var toggle = inputProvider.keyBeginPress(GLFW_KEY_Y)
        if (inputProvider.buttonBeginPress(GLFW_JOYSTICK_1, BUTTON_Y)) {
            toggle = true
        }

        if (toggle) {
            sceneManager.currentScene ?: return
            val character = skater ?:return
            character.getComponent<Transform>()?.let{ transform ->
                if (stateManager.currentState == PlayerState.RIDING) {
                    stateManager.transitionToState(PlayerState.WALKING)
                    physics?.enabled = false

                    // Transition to World Space
                    val worldPos = Vector3f()
                    val worldMatrix = transform.toWorldMatrix()
                    worldMatrix.getTranslation(worldPos)

                    // Get world rotation Y
                    val worldRot = (gameObject.getComponent<Transform>()?.rotation?.y ?: 0f) + transform.rotation.y

                    // Unparent (It remains in scene.gameObjects list)
                    gameObject.removeChild(character)

                    transform.translation.set(worldPos)
                    transform.rotation.set(0f, worldRot, 0f)

                    // Teleport offset: Move slightly to the right side of the board
                    val right = Vector3f(0f, 0f, 0.4f)
                    val boardWorldMatrix = gameObject.getComponent<Transform>()?.toWorldMatrix()
                    boardWorldMatrix?.transformDirection(right)
                    transform.translation.add(right)

                } else {
                    stateManager.transitionToState(PlayerState.RIDING)
                    physics?.enabled = true

                    // Reparent back to board
                    gameObject.addChild(character)

                    // Reset local transform relative to board
                    // Board top is ~0.02 above center. Feet at 0.02.
                    transform.translation.set(0f, 0.02f, 0f)
                    transform.rotation.set(0f, 0f, 0f)
                }
            }
        }
    }

    /**
     * Updates the player's current riding stance (Regular, Fakie, Switch, Nollie) based on
     * movement direction and the `isSwitch` flag.
     */
    fun updateCurrentStance() {
        val body = rb ?: return
        val velocity = body.linearVelocity
        if (velocity.length() < 0.5f) return 

        val transform = gameObject.getComponent<Transform>()?.toWorldMatrix() ?: return
        // Our board forward is X.
        val forward = Vector3f(1f, 0f, 0f)
        transform.transformDirection(forward)

        val dot = forward.dot(velocity)
        val movingForward = dot > 0

        currentStance = when {
            !isSwitch && movingForward -> Stance.REGULAR
            !isSwitch && !movingForward -> Stance.FAKIE
            isSwitch && movingForward -> Stance.SWITCH
            isSwitch && !movingForward -> Stance.NOLLIE
            else -> Stance.REGULAR
        }
    }

    /**
     * Displays a debug window with information about the player's state, stance, and velocity.
     */
    override fun imgui() {
        ImGui.text(stringManager.getString("lbl.player.state", stateManager.currentState::class.simpleName ?: "N/A"))
        ImGui.text(stringManager.getString("lbl.player.preferred_stance", preferredStance))
        ImGui.text(stringManager.getString("lbl.player.current_stance", currentStance))
        ImGui.text(stringManager.getString("lbl.player.is_switch", isSwitch))
        ImGui.text(stringManager.getString("lbl.player.grounded", physics?.isGrounded ?: false))
        
        val vel = rb?.linearVelocity ?: Vector3f()
        ImGui.text(stringManager.getString("lbl.player.velocity", String.format("%.2f, %.2f, %.2f", vel.x, vel.y, vel.z)))
        
        if (ImGui.button(stringManager.getString("btn.player.toggle_switch"))) {
            isSwitch = !isSwitch
        }

        if (ImGui.button(stringManager.getString("btn.player.toggle_preferred_stance"))) {
            preferredStance = if (preferredStance == PreferredStance.REGULAR) PreferredStance.GOOFY else PreferredStance.REGULAR
        }
    }

    /**
     * Checks for conditions that would cause the player to "bail" or fall, such as being upside down
     * or experiencing a high-impact landing.
     */
    fun checkBail() {
        val phys = physics ?: return
        val currentVelocityJOML = rb?.linearVelocity ?: return
        
        val currentVelocity = JmeVector3f(currentVelocityJOML.x, currentVelocityJOML.y, currentVelocityJOML.z)

        if (phys.isGrounded) {
            val transform = gameObject.getComponent<Transform>()?.toWorldMatrix() ?: return
            val localUp = Vector3f(0f, 1f, 0f)
            val worldUp = Vector3f()
            transform.transformDirection(localUp, worldUp)

            // Orientation bail
            if (worldUp.y < 0f) {
                bail()
                return
            }

            // High impact bail (large vertical velocity change)
            val dv = JmeVector3f(currentVelocity).subtract(lastVelocity)
            if (dv.length() > 20f) { // Arbitrary threshold for "slam"
                bail()
                return
            }
        }
    }

    /**
     * Applies corrective torques to the skateboard to "catch" it and stabilize its rotation
     * after a trick, snapping it to the nearest 180-degree yaw and flattening its roll and pitch.
     * @param dt Delta time.
     */
    fun handleCatch(dt: Float) {
        val rb3d = rb ?: return
        val rotation = gameObject.getComponent<Transform>()?.rotation ?: return
        
        // Wrap rotation to 0-360
        var yaw = rotation.y % 360f
        if (yaw < 0) yaw += 360f

        // Check for 180 increments (Yaw Snap)
        val target180 = (yaw / 180f).roundToLong() * 180f
        val diff = target180 - yaw
        
        if (abs(diff) < 20f && (physics?.isGrounded == false)) {
            // Yaw is usually global Y, so global torque is fine
            rb3d.applyTorqueImpulse(Vector3f(0f, diff * catchStrength * dt, 0f))
        }
        
        // Local Space Corrections
        // X-Axis = Roll (Kickflip/Heelflip axis)
        // Z-Axis = Pitch (Manual/Nose Manual axis)
        
        var rollTorque = 0f
        var pitchTorque = 0f

        // Roll (X) Catch
        val rollAngle = rotation.x % 180f
        val absRoll = if (rollAngle < 0) rollAngle + 180f else rollAngle
        // If we are not in the "middle" of a flip (20..160), snap to flat (0) or upside down (180)
        if (absRoll !in 20f..160f) {
            val target = if (absRoll < 20f) 0f else 180f
            // Calculate difference, handling the wrap
            // Simple diff works because we normalized to 0..180 sort of, but strict diff:
            rollTorque = (target - absRoll) * catchStrength * dt
        }

        // Pitch (Z) Catch - Usually we want 0 (flat)
        // We generally don't snap pitch to 180 unless strictly needed
        val pitchAngle = rotation.z % 360f
        // Normalize -180..180
        val normPitch = if (pitchAngle > 180) pitchAngle - 360 else if (pitchAngle < -180) pitchAngle + 360 else pitchAngle
        
        if (abs(normPitch) < 30f) {
            pitchTorque = (0f - normPitch) * catchStrength * dt
        }

        // Apply Local Torque
        if (rollTorque != 0f || pitchTorque != 0f) {
            val localTorque = Vector3f(rollTorque, 0f, pitchTorque)
            val worldTorque = Vector3f()
            gameObject.getComponent<Transform>()?.toWorldMatrix()?.transformDirection(localTorque, worldTorque)
            rb3d.applyTorqueImpulse(worldTorque)
        }
    }

    /**
     * Reads the right analog stick for "flick" inputs and applies corresponding
     * rotational forces to the skateboard for tricks.
     * @param dt Delta time (currently unused).
     */
    fun handleFlicks(dt: Float) {
        val flick = inputBuffer.getRightStickFlickVelocity(GLFW_JOYSTICK_1, 0.1f)
        if (flick.length() > 5.0f) {
            // Apply torque based on flick
            // X-flick = Kickflip/Heelflip (Roll)
            // Y-flick = Shuvit (Yaw)
            
            // Mirroring: In Goofy, Kickflip/Heelflip direction is inverted relative to the board's forward
            val localTorque = Vector3f(flick.y * flickSensitivity * stanceMultiplier, flick.x * flickSensitivity, 0f)
            val worldTorque = Vector3f()
            
            // Convert local torque to world space
            val transform = gameObject.getComponent<Transform>()?.toWorldMatrix()
            transform?.transformDirection(localTorque, worldTorque)
            
            rb?.applyTorqueImpulse(Vector3f(worldTorque.x, worldTorque.y, worldTorque.z))
        }
    }

    /**
     * Handles steering input from the keyboard or left analog stick, applying yaw
     * torque to the skateboard.
     * @param dt Delta time (currently unused).
     */
    fun handleSteering(dt: Float) {
        var steer = 0f
        
        // Controller (Joystick 1 - Left Stick X)
        inputProvider.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
            if (axes.size > AXIS_LEFT_X) {
                val stickX = axes[AXIS_LEFT_X]
                if (abs(stickX) > 0.1f) {
                    steer -= stickX * steerSpeed * stanceMultiplier
                }
            }
        }
        
        if (steer != 0f && (physics?.isGrounded == true)) {
            val angVel = rb?.angularVelocity ?: return
            angVel.y = steer
            rb?.angularVelocity = angVel
        }
    }

    /**
     * Handles pushing input, applying a forward force to the skateboard.
     * @param dt Delta time (currently unused).
     */
    fun handlePushing(dt: Float) {
        var multiplier = 0f

        // Controller (Left Stick Y for forward movement, or triggers)
        inputProvider.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
            if (axes.size > AXIS_LEFT_Y) {
                val stickY = -axes[AXIS_LEFT_Y] // Inverted stick Y
                if (stickY > 0.1f) {
                    multiplier = multiplier.coerceAtLeast(stickY)
                }
            }
            // Optional: Support Right Trigger for acceleration
            if (axes.size > AXIS_RIGHT_TRIGGER) {
                val rt = (axes[AXIS_RIGHT_TRIGGER] + 1f) / 2f // Normalize -1..1 to 0..1
                if (rt > 0.1f) {
                    multiplier = multiplier.coerceAtLeast(rt)
                }
            }
        }

        if (multiplier > 0f && (physics?.isGrounded == true)) {
            val transform = gameObject.getComponent<Transform>()?.toWorldMatrix()
            val forward = Vector3f(1f, 0f, 0f) // X is forward for our board
            transform?.transformDirection(forward)
            forward.mul(pushForce * multiplier)
            
            rb?.applyCentralForce(forward)
        }
    }

    /**
     * Handles jump input, applying a vertical impulse for an ollie.
     */
    fun handleJumping() {
        var jump = inputProvider.keyBeginPress(GLFW_KEY_SPACE)
        
        // Controller (Button A/Cross)
        inputProvider.getButtons(GLFW_JOYSTICK_1)?.let { buttons ->
            if (buttons.size > BUTTON_A && buttons[BUTTON_A]) {
                jump = true
            }
        }

        if (jump && (physics?.isGrounded == true)) {
            rb?.applyImpulse(Vector3f(0f, jumpImpulse, 0f))
        }
    }
}