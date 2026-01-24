package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.controls.KeyListener
import com.pafoid.skate.engine.controls.JoystickListener
import com.pafoid.skate.engine.controls.InputBuffer
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.toMatrix
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Matrix4f
import org.lwjgl.glfw.GLFW.*

class PlayerController : Component() {
    var pushForce = 5.0f
    var steerSpeed = 2.0f
    var jumpImpulse = 10.0f
    var flickSensitivity = 5.0f
    
    @Transient private var rb: RigidBody3D? = null
    @Transient private var physics: SkateboardPhysics? = null
    @Transient private var lastVelocity = com.jme3.math.Vector3f()

    override fun start() {
        rb = gameObject.getComponent<RigidBody3D>()
        physics = gameObject.getComponent<SkateboardPhysics>()
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
            val transform = gameObject.transform.toMatrix()
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
            val catchStrength = 0.5f
            rb3d.rawBody?.applyTorqueImpulse(com.jme3.math.Vector3f(0f, 0f, diff * catchStrength * dt))
        }
    }

    private fun handleFlicks(dt: Float) {
        val flick = InputBuffer.getJoystickFlickVelocity(GLFW_JOYSTICK_1, 0.1f)
        if (flick.length() > 5.0f) {
            // Apply torque based on flick
            // X-flick = Kickflip/Heelflip (Roll)
            // Y-flick = Shuvit (Yaw)
            
            val localTorque = Vector3f(flick.y * flickSensitivity, flick.x * flickSensitivity, 0f)
            val worldTorque = Vector3f()
            
            // Convert local torque to world space
            val transform = gameObject.transform.toMatrix()
            transform.transformDirection(localTorque, worldTorque)
            
            rb?.rawBody?.applyTorqueImpulse(com.jme3.math.Vector3f(worldTorque.x, worldTorque.y, worldTorque.z))
        }
    }

    private fun handleSteering(dt: Float) {
        var rotation = 0f
        
        // Keyboard
        if (KeyListener.isKeyPressed(GLFW_KEY_A)) {
            rotation += steerSpeed
        }
        if (KeyListener.isKeyPressed(GLFW_KEY_D)) {
            rotation -= steerSpeed
        }
        
        // Controller (Joystick 0)
        JoystickListener.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
            if (axes.size > 0) {
                val stickX = axes[0]
                if (Math.abs(stickX) > 0.1f) {
                    rotation -= stickX * steerSpeed
                }
            }
        }
        
        // rb.angularVelocity = rotation
    }

    private fun handlePushing(dt: Float) {
        var multiplier = 0f
        
        // Keyboard
        if (KeyListener.isKeyPressed(GLFW_KEY_W)) {
            multiplier = 1f
        }
        
        // Controller
        JoystickListener.getAxes(GLFW_JOYSTICK_1)?.let { axes ->
            if (axes.size > 1) {
                val stickY = -axes[1] // Inverted stick Y
                if (stickY > 0.1f) {
                    multiplier = Math.max(multiplier, stickY)
                }
            }
        }

        if (multiplier > 0f) {
            val angle = Math.toRadians(gameObject.transform.rotation.z.toDouble())
            val force = Vector2f(Math.cos(angle).toFloat(), Math.sin(angle).toFloat()).mul(pushForce * multiplier)
            // rb.addVelocity(force)
        }
    }

    private fun handleJumping() {
        var jump = KeyListener.keyBeginPress(GLFW_KEY_SPACE)
        
        // Controller
        JoystickListener.getButtons(GLFW_JOYSTICK_1)?.let { buttons ->
            if (buttons.size > 0 && buttons[0]) { // Button 0 is usually A/Cross
                jump = true
            }
        }

        if (jump) {
            // rb.addImpulse(Vector2f(0f, jumpImpulse))
        }
    }
}