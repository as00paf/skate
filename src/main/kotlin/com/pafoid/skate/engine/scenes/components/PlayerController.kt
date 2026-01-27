package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.controls.KeyListener
import com.pafoid.skate.engine.controls.JoystickListener
import com.pafoid.skate.engine.controls.InputBuffer
import com.pafoid.skate.engine.controls.IInputBuffer
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.toWorldMatrix
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Matrix4f
import org.lwjgl.glfw.GLFW.*

class PlayerController : Component() {
    var pushForce = 5.0f
    var steerSpeed = 2.0f
    var jumpImpulse = 10.0f
    var flickSensitivity = 5.0f
    var catchStrength = 0.5f

    @Transient var inputBuffer: IInputBuffer = InputBuffer.instance
    
    @Transient private var rb: RigidBody3D? = null
    @Transient private var physics: SkateboardPhysics? = null
    @Transient private var lastVelocity = com.jme3.math.Vector3f()

    override fun start() {
        rb = gameObject.getComponent(RigidBody3D::class.java)
        physics = gameObject.getComponent(SkateboardPhysics::class.java)
    }

    override fun update(dt: Float) {
        handleSteering(dt)
        handlePushing(dt)
        handleJumping()
        handleFlicks(dt)
        handleCatch(dt)
        checkBail()

        rb?.rawBody?.getLinearVelocity(lastVelocity)
    }

    private fun checkBail() {
        val phys = physics ?: return
        val raw = rb?.rawBody ?: return
        
        val currentVelocity = com.jme3.math.Vector3f()
        raw.getLinearVelocity(currentVelocity)

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
        val linVel = com.jme3.math.Vector3f()
        val angVel = com.jme3.math.Vector3f()
        rb?.rawBody?.getLinearVelocity(linVel)
        rb?.rawBody?.getAngularVelocity(angVel)
        
        cubeRb.rawBody?.setLinearVelocity(linVel)
        cubeRb.rawBody?.setAngularVelocity(angVel)
        
        // Disable this controller
        this.enabled = false
        physics?.enabled = false
    }

    private fun handleCatch(dt: Float) {
        val rb3d = rb ?: return
        val rotation = gameObject.transform.rotation
        
        // Simple 2D catch logic for now (z-rotation)
        // Check if within 20 degrees of 0, 180, 360, etc.
        val angle = rotation.z % 180f
        val absAngle = if (angle < 0) angle + 180f else angle
        
        if (absAngle < 20f || absAngle > 160f) {
            val target = if (absAngle < 20f) 0f else 180f
            val diff = target - absAngle
            
            // Apply "magnetic" impulse
            rb3d.applyTorqueImpulse(org.joml.Vector3f(0f, 0f, diff * catchStrength * dt))
        }
    }

    private fun handleFlicks(dt: Float) {
        val flick = inputBuffer.getRightStickFlickVelocity(GLFW_JOYSTICK_1, 0.1f)
        if (flick.length() > 5.0f) {
            // Apply torque based on flick
            // X-flick = Kickflip/Heelflip (Roll)
            // Y-flick = Shuvit (Yaw)
            
            val localTorque = Vector3f(flick.y * flickSensitivity, flick.x * flickSensitivity, 0f)
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
        if (KeyListener.isKeyPressed(GLFW_KEY_A)) {
            steer += steerSpeed
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_D)) {
            steer -= steerSpeed
        }
        
        // Controller (Joystick 1 - Left Stick X)
        JoystickListener.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
            if (axes.size > JoystickListener.AXIS_LEFT_X) {
                val stickX = axes[JoystickListener.AXIS_LEFT_X]
                if (Math.abs(stickX) > 0.1f) {
                    steer -= stickX * steerSpeed
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
        if (KeyListener.isKeyPressed(GLFW_KEY_W)) {
            multiplier = 1f
        }
        
        // Controller (Left Stick Y for forward movement, or triggers)
        JoystickListener.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
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
            
            rb?.rawBody?.applyCentralForce(com.jme3.math.Vector3f(forward.x, forward.y, forward.z))
        }
    }

    private fun handleJumping() {
        var jump = KeyListener.keyBeginPress(GLFW_KEY_SPACE)
        
        // Controller (Button A/Cross)
        JoystickListener.getButtons(GLFW_JOYSTICK_1)?.let { buttons ->
            if (buttons.size > JoystickListener.BUTTON_A && buttons[JoystickListener.BUTTON_A]) {
                jump = true
            }
        }

        if (jump && (physics?.isGrounded == true)) {
            rb?.applyImpulse(Vector3f(0f, jumpImpulse, 0f))
        }
    }
}