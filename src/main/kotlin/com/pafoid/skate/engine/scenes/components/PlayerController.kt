package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.Prefabs
import com.pafoid.skate.engine.Stance
import com.pafoid.skate.engine.controls.input.InputBuffer
import com.pafoid.skate.engine.controls.input.IInputBuffer
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.toWorldMatrix
import org.lwjgl.glfw.GLFW.*
import com.pafoid.skate.engine.SkateStance
import com.pafoid.skate.player.state.PlayerState
import com.pafoid.skate.player.state.PlayerStateManager
import com.pafoid.skate.engine.controls.input.IInputProvider
import com.pafoid.skate.engine.controls.input.InputProvider
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.animation.Animator
import com.pafoid.skate.engine.animation.Skeleton
import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.ObjLoader
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.controls.listeners.GamepadConstants.AXIS_LEFT_X
import com.pafoid.skate.engine.controls.listeners.GamepadConstants.AXIS_LEFT_Y
import com.pafoid.skate.engine.controls.listeners.GamepadConstants.AXIS_RIGHT_TRIGGER
import com.pafoid.skate.engine.controls.listeners.GamepadConstants.BUTTON_A
import com.pafoid.skate.engine.controls.listeners.GamepadConstants.BUTTON_Y
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.render.VAOLoader
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToLong

class PlayerController : Component() {
    var preferredStance = Stance.REGULAR
    var pushForce = 5.0f
    var steerSpeed = 2.0f
    var jumpImpulse = 10.0f
    var flickSensitivity = 5.0f
    var catchStrength = 0.5f
    
    var walkSpeed = 3.0f

    @Transient lateinit var stateManager: PlayerStateManager

    @Transient var currentStance = SkateStance.REGULAR
    @Transient var isSwitch = false
    @Transient var inputBuffer: IInputBuffer = InputBuffer.instance
    @Transient var inputProvider: IInputProvider = InputProvider
    
    @Transient private var rb: IPhysicsBody3D? = null
    @Transient private var physics: SkateboardPhysics? = null
    @Transient private var lastVelocity = com.jme3.math.Vector3f()
    @Transient private var animator: Animator? = null
    @Transient private var skater: GameObject? = null
    @Transient private var currentLean = 0f
    private val maxLeanAngle = 20f
    private val leanSmoothness = 5f

    private val stanceMultiplier: Float
        get() = if (preferredStance == Stance.REGULAR) 1f else -1f

    override fun start() {
        rb = gameObject.getComponent(RigidBody3D::class.java)
        physics = gameObject.getComponent(SkateboardPhysics::class.java)
        
        // Find animator and skater object
        skater = gameObject.children.find { it.name == "Skater" }
        skater?.let { s: GameObject ->
            animator = s.getComponent<Animator>()
        }
        stateManager = PlayerStateManager(this)
        stateManager.transitionToState(PlayerState.RIDING)
    }

    override fun update(dt: Float) {
        handleStateToggle()
        stateManager.update(dt)

        val vel = rb?.linearVelocity
        if (vel != null) {
            lastVelocity.set(vel.x, vel.y, vel.z)
        }
    }

    fun isMoving(): Boolean {
        return (rb?.linearVelocity?.length() ?: 0f) > 0.1f
    }

    fun isPushing(): Boolean {
        var multiplier = 0f
        if (inputProvider.isKeyPressed(GLFW_KEY_W)) {
            multiplier = 1f
        }
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

    fun handleStability() {
        val s = skater ?: return
        // Force snap to board top center
        s.transform.translation.set(0f, 0.02f, 0f)
        
        // Face sideways relative to board (90 degrees)
        s.transform.rotation.set(0f, 90f, 0f)
    }

    fun updateRidingAnimation(dt: Float) {
        val anim = animator ?: return
        
        // Try to find "ride" or "idle" for riding, otherwise use the first available
        // The james.dae only has one animation: "mixamorig9_Hips"
        anim.play("mixamorig9_Hips", 0.2f)
        
        // If it's a static pose, we might want to pause it at frame 0
        // but for now let's let it play to see what it is.
    }

fun updateProceduralLean(dt: Float) {
    if (stateManager.currentState !is PlayerState.RIDING) return
    val entity = skater?.getComponent<Entity>() ?: return
    val skeleton = entity.gameObject.getComponent<Skeleton>() ?: entity.model.skeleton ?: return

        var steerInput = 0f
        inputProvider.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
            if (axes.size > AXIS_LEFT_X) {
                steerInput = axes[AXIS_LEFT_X]
            }
        }
        if (inputProvider.isKeyPressed(GLFW_KEY_A)) steerInput = -1f
        if (inputProvider.isKeyPressed(GLFW_KEY_D)) steerInput = 1f

        // Target lean based on steering
        val targetLean = -steerInput * maxLeanAngle * stanceMultiplier
        currentLean = com.pafoid.skate.engine.utils.Interpolation.lerp(currentLean, targetLean, leanSmoothness * dt)

        // Apply to spine joints
        val spineNames = listOf("mixamorig9_Spine", "mixamorig9_Spine1", "mixamorig9_Spine2")
        val leanPerJoint = currentLean / spineNames.size
        
        val rotationQuat = org.joml.Quaternionf().rotateZ(Math.toRadians(leanPerJoint.toDouble()).toFloat())

        spineNames.forEach { name ->
            // Multiply current local rotation by procedural lean
            // Since james model is facing sideways, lean might need to be on a different axis
            // Based on standard Mixamo: X is usually pitch, Y is yaw, Z is roll (side lean)
            skeleton.getJointByName(name)?.localTransform?.rotate(rotationQuat)
        }
    }

    fun handleWalking(dt: Float) {
        val scene = SceneManager.getCurrentScene() ?: return
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
        
        // Keyboard
        if (inputProvider.isKeyPressed(GLFW_KEY_W)) moveZ += 1f
        if (inputProvider.isKeyPressed(GLFW_KEY_S)) moveZ -= 1f
        if (inputProvider.isKeyPressed(GLFW_KEY_A)) moveX -= 1f
        if (inputProvider.isKeyPressed(GLFW_KEY_D)) moveX += 1f
        
        val moveInput = Vector3f(moveX, 0f, moveZ)
        if (moveInput.length() > 1f) moveInput.normalize()
        
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
            target.transform.translation.add(velocity)
            
            // Face movement direction
            val targetRotationY = Math.toDegrees(atan2(moveDir.x.toDouble(), moveDir.z.toDouble())).toFloat()
            target.transform.rotation.y = com.pafoid.skate.engine.utils.Interpolation.lerp(target.transform.rotation.y, targetRotationY, 10f * dt)
            
            animator?.play("walk", 0.2f)
        } else {
            animator?.play("idle", 0.2f)
        }
        
        // Jump Button A
        if (inputProvider.buttonBeginPress(GLFW_JOYSTICK_1, BUTTON_A) || inputProvider.keyBeginPress(GLFW_KEY_SPACE)) {
            animator?.play("jump", 0.1f)
            // Note: Since unparented, board doesn't jump. Character just plays anim.
            // In a full controller, we'd add vertical velocity to the character transform.
        }
    }

    fun handleGroundSnapping() {
        val scene = SceneManager.getCurrentScene() ?: return
        val target = skater ?: return
        val pos = target.transform.translation
        
        val rayStart = Vector3f(pos.x, pos.y + 1f, pos.z)
        val rayEnd = Vector3f(pos.x, pos.y - 2f, pos.z)
        
        val results = scene.physics3d.rayTest(rayStart, rayEnd)
        if (results.isNotEmpty()) {
            val closest = results.minByOrNull { it.hitFraction }!!
            val hitY = rayStart.y + (rayEnd.y - rayStart.y) * closest.hitFraction
            pos.y = hitY
        }
    }

    private fun handleStateToggle() {
        var toggle = inputProvider.keyBeginPress(GLFW_KEY_Y)
        if (inputProvider.buttonBeginPress(GLFW_JOYSTICK_1, BUTTON_Y)) {
            toggle = true
        }

        if (toggle) {
            val s = skater ?: return
            SceneManager.getCurrentScene() ?: return

            if (stateManager.currentState == PlayerState.RIDING) {
                stateManager.transitionToState(PlayerState.WALKING)
                physics?.enabled = false
                
                // Transition to World Space
                val worldPos = Vector3f()
                val worldMatrix = s.transform.toWorldMatrix()
                worldMatrix.getTranslation(worldPos)
                
                // Get world rotation Y
                val worldRot = gameObject.transform.rotation.y + s.transform.rotation.y

                // Unparent (It remains in scene.gameObjects list)
                gameObject.removeChild(s)
                
                s.transform.translation.set(worldPos)
                s.transform.rotation.set(0f, worldRot, 0f)
                
                // Teleport offset: Move slightly to the right side of the board
                val right = Vector3f(0f, 0f, 0.4f)
                val boardWorldMatrix = gameObject.transform.toWorldMatrix()
                boardWorldMatrix.transformDirection(right)
                s.transform.translation.add(right)
                
            } else {
                stateManager.transitionToState(PlayerState.RIDING)
                physics?.enabled = true
                
                // Reparent back to board
                gameObject.addChild(s)
                
                // Reset local transform relative to board
                // Board top is ~0.02 above center. Feet at 0.02.
                s.transform.translation.set(0f, 0.02f, 0f) 
                s.transform.rotation.set(0f, 0f, 0f)
            }
        }
    }

    fun updateCurrentStance() {
        val body = rb ?: return
        val velocity = body.linearVelocity
        if (velocity.length() < 0.5f) return 

        val transform = gameObject.transform.toWorldMatrix()
        // Our board forward is X.
        val forward = Vector3f(1f, 0f, 0f)
        transform.transformDirection(forward)

        val dot = forward.dot(velocity)
        val movingForward = dot > 0

        currentStance = when {
            !isSwitch && movingForward -> SkateStance.REGULAR
            !isSwitch && !movingForward -> SkateStance.FAKIE
            isSwitch && movingForward -> SkateStance.SWITCH
            isSwitch && !movingForward -> SkateStance.NOLLIE
            else -> SkateStance.REGULAR
        }
    }

    override fun imgui() {
        imgui.ImGui.begin("Skater Debug")
        imgui.ImGui.text("State: ${stateManager.currentState::class.simpleName}")
        imgui.ImGui.text("Preferred Stance: $preferredStance")
        imgui.ImGui.text("Current Stance: $currentStance")
        imgui.ImGui.text("Is Switch: $isSwitch")
        imgui.ImGui.text("Grounded: ${physics?.isGrounded}")
        
        val vel = rb?.linearVelocity ?: Vector3f()
        imgui.ImGui.text("Velocity: ${String.format("%.2f, %.2f, %.2f", vel.x, vel.y, vel.z)}")
        
        if (imgui.ImGui.button("Toggle Switch")) {
            isSwitch = !isSwitch
        }

        if (imgui.ImGui.button("Toggle Preferred Stance")) {
            preferredStance = if (preferredStance == Stance.REGULAR) Stance.GOOFY else Stance.REGULAR
        }
        
        imgui.ImGui.end()
    }

    fun checkBail() {
        val phys = physics ?: return
        val currentVelocityJOML = rb?.linearVelocity ?: return
        
        val currentVelocity = com.jme3.math.Vector3f(currentVelocityJOML.x, currentVelocityJOML.y, currentVelocityJOML.z)

        if (phys.isGrounded) {
            val transform = gameObject.transform.toWorldMatrix()
            val localUp = Vector3f(0f, 1f, 0f)
            val worldUp = Vector3f()
            transform.transformDirection(localUp, worldUp)

            // Orientation bail
            if (worldUp.y < 0f) {
                bail()
                return
            }

            // High impact bail (large vertical velocity change)
            val dv = com.jme3.math.Vector3f(currentVelocity).subtract(lastVelocity)
            if (dv.length() > 20f) { // Arbitrary threshold for "slam"
                bail()
                return
            }
        }
    }

    private fun bail() {
        // Transition to Tumble Cube
        val scene = SceneManager.getCurrentScene() ?: return
        
        // Find the skater child
        val skater = gameObject.children.find { it.name == "Skater" }
        
        val tumbleCube = Prefabs.generateEntityObject(
            AssetPool.getRawModel(ObjLoader.CUBE, VAOLoader()),
            AssetPool.getTexture(Texture.WHITE),
            "TumbleCube"
        )
        
        tumbleCube.transform.translation.set(gameObject.transform.translation)
        tumbleCube.transform.rotation.set(gameObject.transform.rotation)
        
        val cubeRb = RigidBody3D(mass = 5f)
        tumbleCube.addComponent(cubeRb)
        
        val cubeCollider = BoxCollider3D()
        cubeCollider.halfExtents.set(0.5f, 0.5f, 0.5f)
        tumbleCube.addComponent(cubeCollider)
        
        scene.addGameObjectToScene(tumbleCube)
        // Add to physics immediately so we can set velocity
        scene.physics3d.add(tumbleCube)

        // Reparent skater to the tumble cube
        skater?.let {
            tumbleCube.addChild(it)
            // Reset local transform relative to cube
            it.transform.translation.set(0f, 0f, 0f)
            it.transform.rotation.set(0f, 0f, 0f)
            it.transform.scale.set(1f, 1f, 1f) // Adjust scale if needed, since cube is 1.0
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

    fun handleCatch(dt: Float) {
        val rb3d = rb ?: return
        val rotation = gameObject.transform.rotation
        
        // Wrap rotation to 0-360
        var yaw = rotation.y % 360f
        if (yaw < 0) yaw += 360f

        // Check for 180 increments
        val target180 = (yaw / 180f).roundToLong() * 180f
        val diff = target180 - yaw
        
        if (abs(diff) < 20f && (physics?.isGrounded == false)) {
            // Apply "magnetic" impulse to snap to 180 increments
            rb3d.applyTorqueImpulse(Vector3f(0f, diff * catchStrength * dt, 0f))
        }
        
        // Pitch/Roll catch
        val pAngle = rotation.x % 180f
        val absPAngle = if (pAngle < 0) pAngle + 180f else pAngle
        if (absPAngle !in 20f..160f) {
            val pTarget = if (absPAngle < 20f) 0f else 180f
            rb3d.applyTorqueImpulse(Vector3f((pTarget - absPAngle) * catchStrength * dt, 0f, 0f))
        }

        val rAngle = rotation.z % 180f
        val absRAngle = if (rAngle < 0) rAngle + 180f else rAngle
        if (absRAngle !in 20f..160f) {
            val rTarget = if (absRAngle < 20f) 0f else 180f
            rb3d.applyTorqueImpulse(Vector3f(0f, 0f, (rTarget - absRAngle) * catchStrength * dt))
        }
    }

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
            val transform = gameObject.transform.toWorldMatrix()
            transform.transformDirection(localTorque, worldTorque)
            
            rb?.applyTorqueImpulse(Vector3f(worldTorque.x, worldTorque.y, worldTorque.z))
        }
    }

    fun handleSteering(dt: Float) {
        var steer = 0f
        
        // Keyboard
        if (inputProvider.isKeyPressed(GLFW_KEY_A)) {
            steer += steerSpeed * stanceMultiplier
        }
        if (inputProvider.isKeyPressed(GLFW_KEY_D)) {
            steer -= steerSpeed * stanceMultiplier
        }
        
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

    fun handlePushing(dt: Float) {
        var multiplier = 0f
        
        // Keyboard
        if (inputProvider.isKeyPressed(GLFW_KEY_W)) {
            multiplier = 1f
        }
        
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
            val transform = gameObject.transform.toWorldMatrix()
            val forward = Vector3f(1f, 0f, 0f) // X is forward for our board
            transform.transformDirection(forward)
            forward.mul(pushForce * multiplier)
            
            rb?.applyCentralForce(forward)
        }
    }

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