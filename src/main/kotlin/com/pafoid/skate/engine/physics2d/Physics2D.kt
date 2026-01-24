package com.pafoid.skate.engine.physics2d

import com.pafoid.skate.engine.physics2d.components.Box2DCollider
import com.pafoid.skate.engine.physics2d.components.RigidBody2D
import com.pafoid.skate.engine.physics2d.enums.BodyType
import com.pafoid.skate.engine.scenes.GameObject
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import org.joml.Vector2f

class Physics2D {
    private val gravity: Vec2 = Vec2(0f, -10f)
    private val world = World(gravity)
    
    private var physicsTime = 0f
    private val physicsTimeStep = 1f / 60f
    private val velocityIterations = 8
    private val positionIterations = 3

    init {
        world.setContactListener(SkateContactListener())
    }

    fun add(go: GameObject) {
        val rb = go.getComponent<RigidBody2D>()
        if (rb != null && rb.rawBody == null) {
            val transform = go.transform

            val bodyDef = BodyDef()
            bodyDef.angle = Math.toRadians(transform.rotation.z.toDouble()).toFloat()
            bodyDef.position.set(transform.translation.x, transform.translation.y)
            bodyDef.angularDamping = rb.angularDamping
            bodyDef.linearDamping = rb.linearDamping
            bodyDef.fixedRotation = rb.fixedRotation
            bodyDef.bullet = rb.continuousCollision
            bodyDef.gravityScale = rb.gravityScale
            bodyDef.angularVelocity = rb.angularVelocity
            bodyDef.userData = go

            bodyDef.type = when (rb.bodyType) {
                BodyType.Static -> org.jbox2d.dynamics.BodyType.STATIC
                BodyType.Dynamic -> org.jbox2d.dynamics.BodyType.DYNAMIC
                BodyType.Kinematic -> org.jbox2d.dynamics.BodyType.KINEMATIC
            }

            val body = world.createBody(bodyDef)
            body.m_mass = rb.mass
            rb.rawBody = body
            
            val collider = go.getComponent<Box2DCollider>()
            if (collider != null) {
                addBox2DCollider(rb, collider)
            }
        }
    }

    fun update(dt: Float) {
        physicsTime += dt
        while (physicsTime >= 0f) {
            physicsTime -= physicsTimeStep
            world.step(physicsTimeStep, velocityIterations, positionIterations)
        }
    }

    private fun addBox2DCollider(rb: RigidBody2D, boxCollider: Box2DCollider) {
        val body = rb.rawBody ?: return

        val shape = PolygonShape()
        val halfSize = Vector2f(boxCollider.halfSize).mul(0.5f)
        val offset = boxCollider.offset
        shape.setAsBox(halfSize.x, halfSize.y, Vec2(offset.x, offset.y), 0f)
        
        val fixDef = FixtureDef()
        fixDef.shape = shape
        fixDef.density = 1f
        fixDef.friction = rb.friction
        fixDef.userData = boxCollider.gameObject
        fixDef.isSensor = rb.isSensor
        body.createFixture(fixDef)
    }

    fun destroyGameObject(go: GameObject) {
        val body = go.getComponent<RigidBody2D>()
        body?.rawBody?.let { rawBody ->
            world.destroyBody(rawBody)
            body.rawBody = null
        }
    }

    fun isLocked(): Boolean {
        return world.isLocked
    }
}