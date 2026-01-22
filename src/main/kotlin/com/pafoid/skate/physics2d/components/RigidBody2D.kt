package physics2d.components

import components.Component
import marki.Window
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.joml.Vector2f
import physics2d.enums.BodyType

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

    var angularDamping:Float = 0.8f
    var linearDamping:Float = 0.9f
    var mass: Float = 0f
    var bodyType: BodyType = BodyType.Dynamic
    var fixedRotation = false
    var continuousCollision = true
    var friction: Float = 0f
    @Transient var rawBody: Body? = null

    override fun update(dt: Float) {
        rawBody?.let { rb ->

            if(bodyType == BodyType.Static) {
                rb.setTransform(Vec2(gameObject.transform.position.x, gameObject.transform.position.y), gameObject.transform.rotation.toFloat())
            } else {
                gameObject.transform.position.set(rb.position.x, rb.position.y)
                gameObject.transform.rotation = Math.toDegrees(rb.angle.toDouble())
                val vel = rb.linearVelocity
                velocity.set(vel.x, vel.y)
            }
        }
    }

    fun addVelocity(forceToAdd: Vector2f) {
        rawBody?.applyForceToCenter(Vec2(forceToAdd.x, forceToAdd.y))
    }

    fun addImpulse(impulse: Vector2f) {
        rawBody?.applyLinearImpulse(Vec2(velocity.x, velocity.y), rawBody?.worldCenter)
    }

    fun setVelocity(velocity: Vector2f) {
        this.velocity.set(velocity)
    }

    fun setIsSensor() {
        this.isSensor = true
        rawBody?.let{
            Window.getPhysics().setIsSensor(this)
        }
    }

    fun setNotSensor() {
        this.isSensor = false
        rawBody?.let{
            Window.getPhysics().setNotSensor(this)
        }
    }
}