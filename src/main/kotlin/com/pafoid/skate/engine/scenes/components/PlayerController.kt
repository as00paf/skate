package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.Stance
import com.pafoid.skate.engine.controls.KeyListener
import com.pafoid.skate.engine.controls.JoystickListener
import com.pafoid.skate.engine.controls.InputBuffer
import com.pafoid.skate.engine.controls.IInputBuffer
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.toWorldMatrix
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*
import com.pafoid.skate.engine.SkateStance
import com.pafoid.skate.engine.PlayerState

import com.pafoid.skate.engine.controls.IInputProvider
import com.pafoid.skate.engine.controls.InputProvider
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D

class PlayerController : Component() {
    var preferredStance = Stance.REGULAR
    var pushForce = 5.0f
    var steerSpeed = 2.0f
    var jumpImpulse = 10.0f
    var flickSensitivity = 5.0f
    var catchStrength = 0.5f

    var state = PlayerState.RIDING

    @Transient var currentStance = SkateStance.REGULAR
    @Transient var isSwitch = false
    @Transient var inputBuffer: IInputBuffer = InputBuffer.instance
    @Transient var inputProvider: IInputProvider = InputProvider
    
    @Transient private var rb: IPhysicsBody3D? = null
    @Transient private var physics: SkateboardPhysics? = null
    @Transient private var lastVelocity = com.jme3.math.Vector3f()

    private val stanceMultiplier: Float
        get() = if (preferredStance == Stance.REGULAR) 1f else -1f

    override fun start() {
        rb = gameObject.getComponent(RigidBody3D::class.java)
        physics = gameObject.getComponent(SkateboardPhysics::class.java)
    }

    override fun update(dt: Float) {
        handleStateToggle()

        if (state == PlayerState.RIDING) {
            updateCurrentStance()
            handleSteering(dt)
            handlePushing(dt)
            handleJumping()
            handleFlicks(dt)
            handleCatch(dt)
            checkBail()
        }

        val vel = rb?.linearVelocity
        if (vel != null) {
            lastVelocity.set(vel.x, vel.y, vel.z)
        }
    }

    private fun handleStateToggle() {
        var toggle = inputProvider.keyBeginPress(GLFW_KEY_Y)
        if (inputProvider.buttonBeginPress(GLFW_JOYSTICK_1, JoystickListener.BUTTON_Y)) {
            toggle = true
        }

        if (toggle) {
            if (state == PlayerState.RIDING) {
                state = PlayerState.WALKING
                physics?.enabled = false
                // Teleport offset: Move slightly up and to the side when getting off
                gameObject.transform.translation.y += 0.2f
                gameObject.transform.translation.z += 0.5f 
            } else {
                state = PlayerState.RIDING
                physics?.enabled = true
                // Teleport offset: Move back to center
                gameObject.transform.translation.z -= 0.5f
            }
        }
    }

    private fun updateCurrentStance() {
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
        imgui.ImGui.text("State: $state")
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

    private fun checkBail() {
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
        val scene = com.pafoid.skate.engine.scenes.SceneManager.getCurrentScene() ?: return
        
        // Find the skater child
        val skater = gameObject.children.find { it.name == "Skater" }
        
        val tumbleCube = com.pafoid.skate.engine.Prefabs.generateEntityObject(
            com.pafoid.skate.engine.assets.AssetPool.getRawModel(com.pafoid.skate.engine.assets.ObjLoader.CUBE, com.pafoid.skate.engine.render.VAOLoader()),
            com.pafoid.skate.engine.assets.AssetPool.getTexture(com.pafoid.skate.engine.assets.Texture.WHITE),
            "TumbleCube"
        )
        
        tumbleCube.transform.translation.set(gameObject.transform.translation)
        tumbleCube.transform.rotation.set(gameObject.transform.rotation)
        
        val cubeRb = RigidBody3D(mass = 5f)
        tumbleCube.addComponent(cubeRb)
        
        val cubeCollider = com.pafoid.skate.engine.physics3d.components.BoxCollider3D()
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

    private fun handleCatch(dt: Float) {
        val rb3d = rb ?: return
        val rotation = gameObject.transform.rotation
        
        // Wrap rotation to 0-360
        var yaw = rotation.y % 360f
        if (yaw < 0) yaw += 360f

        // Check for 180 increments
        val target180 = Math.round(yaw / 180f) * 180f
        val diff = target180 - yaw
        
        if (Math.abs(diff) < 20f && (physics?.isGrounded == false)) {
            // Apply "magnetic" impulse to snap to 180 increments
            rb3d.applyTorqueImpulse(org.joml.Vector3f(0f, diff * catchStrength * dt, 0f))
        }
        
        // Pitch/Roll catch
        val pAngle = rotation.x % 180f
        val absPAngle = if (pAngle < 0) pAngle + 180f else pAngle
        if (absPAngle < 20f || absPAngle > 160f) {
            val pTarget = if (absPAngle < 20f) 0f else 180f
            rb3d.applyTorqueImpulse(org.joml.Vector3f((pTarget - absPAngle) * catchStrength * dt, 0f, 0f))
        }

        val rAngle = rotation.z % 180f
        val absRAngle = if (rAngle < 0) rAngle + 180f else rAngle
        if (absRAngle < 20f || absRAngle > 160f) {
            val rTarget = if (absRAngle < 20f) 0f else 180f
            rb3d.applyTorqueImpulse(org.joml.Vector3f(0f, 0f, (rTarget - absRAngle) * catchStrength * dt))
        }
    }

    private fun handleFlicks(dt: Float) {
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
            
            rb?.applyTorqueImpulse(org.joml.Vector3f(worldTorque.x, worldTorque.y, worldTorque.z))
        }
    }

    private fun handleSteering(dt: Float) {
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
            if (axes.size > JoystickListener.AXIS_LEFT_X) {
                val stickX = axes[JoystickListener.AXIS_LEFT_X]
                if (Math.abs(stickX) > 0.1f) {
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

    private fun handlePushing(dt: Float) {
        var multiplier = 0f
        
        // Keyboard
        if (inputProvider.isKeyPressed(GLFW_KEY_W)) {
            multiplier = 1f
        }
        
        // Controller (Left Stick Y for forward movement, or triggers)
        inputProvider.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
            if (axes.size > JoystickListener.AXIS_LEFT_Y) {
                val stickY = -axes[JoystickListener.AXIS_LEFT_Y] // Inverted stick Y
                if (stickY > 0.1f) {
                    multiplier = Math.max(multiplier, stickY)
                }
            }
            // Optional: Support Right Trigger for acceleration
            if (axes.size > JoystickListener.AXIS_RIGHT_TRIGGER) {
                val rt = (axes[JoystickListener.AXIS_RIGHT_TRIGGER] + 1f) / 2f // Normalize -1..1 to 0..1
                if (rt > 0.1f) {
                    multiplier = Math.max(multiplier, rt)
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

    private fun handleJumping() {
        var jump = inputProvider.keyBeginPress(GLFW_KEY_SPACE)
        
        // Controller (Button A/Cross)
        inputProvider.getButtons(GLFW_JOYSTICK_1)?.let { buttons ->
            if (buttons.size > JoystickListener.BUTTON_A && buttons[JoystickListener.BUTTON_A]) {
                jump = true
            }
        }

        if (jump && (physics?.isGrounded == true)) {
            rb?.applyImpulse(Vector3f(0f, jumpImpulse, 0f))
        }
    }
}