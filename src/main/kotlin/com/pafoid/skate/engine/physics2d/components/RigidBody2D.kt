package com.pafoid.skate.engine.physics2d.components

import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.physics2d.enums.BodyType
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.joml.Vector2f

class RigidBody2D: Component() {
    val velocity: Vector2f = Vector2f()
    var angularVelocity: Float = 0f
        set(value) {
            field = value
            rawBody?.angularVelocity = value
        }
    var gravityScale = 1f
        set(value) {
            field = value
            rawBody?.gravityScale = value
        }
    var isSensor = false

    var angularDamping: Float = 0.8f
    var linearDamping: Float = 0.9f
    var mass: Float = 0f
    var bodyType: BodyType = BodyType.Dynamic
    var fixedRotation = false
    var continuousCollision = true
    var friction: Float = 0.1f
    
    @Transient var rawBody: Body? = null

    override fun update(dt: Float) {
        rawBody?.let { rb ->
            if(bodyType == BodyType.Static) {
                rb.setTransform(Vec2(gameObject.transform.translation.x, gameObject.transform.translation.y), Math.toRadians(gameObject.transform.rotation.z.toDouble()).toFloat())
            } else {
                gameObject.transform.translation.x = rb.position.x
                gameObject.transform.translation.y = rb.position.y
                gameObject.transform.rotation.z = Math.toDegrees(rb.angle.toDouble()).toFloat()
                
                val vel = rb.linearVelocity
                velocity.set(vel.x, vel.y)
            }
        }
    }

    fun addVelocity(forceToAdd: Vector2f) {
        rawBody?.applyForceToCenter(Vec2(forceToAdd.x, forceToAdd.y))
    }

    fun addImpulse(impulse: Vector2f) {
        rawBody?.applyLinearImpulse(Vec2(impulse.x, impulse.y), rawBody?.worldCenter)
    }

    fun setVelocity(velocity: Vector2f) {
        this.velocity.set(velocity)
        rawBody?.linearVelocity = Vec2(velocity.x, velocity.y)
    }
}