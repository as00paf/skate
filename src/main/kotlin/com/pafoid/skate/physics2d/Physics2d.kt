package physics2d

import components.PillboxCollider
import marki.GameObject
import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.*
import org.joml.Vector2f
import physics2d.components.Box2DCollider
import physics2d.components.CircleCollider
import physics2d.components.RigidBody2D
import physics2d.enums.BodyType

class Physics2d {

    val gravity: Vec2 = Vec2(0f, -10f)

    private val world = World(gravity)
    private var physicsTime = 0f
    private val physicsTimeStep = 1f/60f
    private val velocityIterations = 8
    private val positionIterations = 3

    init {
        world.setContactListener(MarkiContactListener())
    }

    fun add(go: GameObject) {
        val rb =  go.getComponent(RigidBody2D::class.java)
        if(rb != null && rb.rawBody == null) {
            val transform = go.transform

            val bodyDef = BodyDef()
            bodyDef.angle = Math.toRadians(transform.rotation).toFloat()
            bodyDef.position.set(transform.position.x, transform.position.y)
            bodyDef.angularDamping = rb.angularDamping
            bodyDef.linearDamping = rb.linearDamping
            bodyDef.fixedRotation = rb.fixedRotation
            bodyDef.bullet = rb.continuousCollision
            bodyDef.gravityScale = rb.gravityScale
            bodyDef.angularVelocity = rb.angularVelocity
            bodyDef.userData = rb.gameObject

            bodyDef.type = when(rb.bodyType) {
                BodyType.Static -> org.jbox2d.dynamics.BodyType.STATIC
                BodyType.Dynamic -> org.jbox2d.dynamics.BodyType.DYNAMIC
                BodyType.Kinematic -> org.jbox2d.dynamics.BodyType.KINEMATIC
            }


            val body = world.createBody(bodyDef)
            body.m_mass = rb.mass
            rb.rawBody = body
            val collider = go.getComponent(CircleCollider::class.java) ?: go.getComponent(Box2DCollider::class.java) ?: go.getComponent(PillboxCollider::class.java)
            when (collider) {
                is CircleCollider -> addCircleCollider(rb, collider)
                is Box2DCollider -> addBox2DCollider(rb, collider)
                is PillboxCollider -> addPillboxCollider(rb, collider)
            }
        }
    }

    fun update(dt: Float) {
        physicsTime += dt
        if(physicsTime >= 0f){
            physicsTime -= physicsTimeStep
            world.step(physicsTimeStep, velocityIterations, positionIterations)
        }
    }

    fun setIsSensor(rb: RigidBody2D) {
        val body = rb.rawBody ?: return
        var fixture = body.fixtureList
        while(fixture != null) {
            fixture.m_isSensor = true
            fixture = fixture.m_next
        }
    }

    fun setNotSensor(rb: RigidBody2D) {
        val body = rb.rawBody ?: return
        var fixture = body.fixtureList
        while(fixture != null) {
            fixture.m_isSensor = false
            fixture = fixture.m_next
        }
    }

    fun destroyGameObject(go: GameObject) {
        val body = go.getComponent(RigidBody2D::class.java)
        body?.rawBody?.let { rawBody ->
            world.destroyBody(rawBody)
            body.rawBody = null
        }
    }

    fun destroyGameObjects(deadObjects: List<GameObject>) {
        deadObjects.forEach { destroyGameObject(it) }
    }

    fun resetCircleCollider(rb: RigidBody2D, collider: CircleCollider) {
        val body = rb.rawBody ?: return

        val size = fixtureListSize(body)
        for(i in 0 until size) {
            body.destroyFixture(body.fixtureList)
        }

        addCircleCollider(rb, collider)
        body.resetMassData()
    }

    fun resetBox2DCollider(rb: RigidBody2D, collider: Box2DCollider) {
        val body = rb.rawBody ?: return

        val size = fixtureListSize(body)
        for(i in 0 until size) {
            body.destroyFixture(body.fixtureList)
        }

        addBox2DCollider(rb, collider)
        body.resetMassData()
    }

    fun addBox2DCollider(rb: RigidBody2D, boxCollider: Box2DCollider) {
        assert(rb.rawBody != null) { "Raw body cannot be null" }
        val body = rb.rawBody!!

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

    private fun addCircleCollider(rb: RigidBody2D, collider: CircleCollider) {
        assert(rb.rawBody != null) { "Raw body cannot be null" }
        val body = rb.rawBody!!

        val shape = CircleShape()
        shape.radius = collider.radius
        shape.m_p.set(collider.offset.x, collider.offset.y)

        val fixDef = FixtureDef()
        fixDef.shape = shape
        fixDef.density = 1f
        fixDef.friction = rb.friction
        fixDef.userData = collider.gameObject
        fixDef.isSensor = rb.isSensor
        body.createFixture(fixDef)
    }

    fun raycast(requestingObject: GameObject?, point1: Vector2f, point2: Vector2f):RaycastInfo {
        val callback = RaycastInfo(requestingObject)
        world.raycast(callback, Vec2(point1.x, point1.y) , Vec2(point2.x, point2.y))
        return callback
    }

    private fun fixtureListSize(body: Body):Int {
        var size = 0
        var fixture: Fixture = body.fixtureList
        while(fixture != null) {
            size++
            fixture = fixture.m_next
        }
        return size
    }

    fun resetPillboxCollider(rb: RigidBody2D, collider: PillboxCollider) {
        val body = rb.rawBody ?: return

        val size = fixtureListSize(body)
        for(i in 0 until size) {
            body.destroyFixture(body.fixtureList)
        }

        addPillboxCollider(rb, collider)
        body.resetMassData()
    }

    fun addPillboxCollider(rb: RigidBody2D, collider: PillboxCollider) {
        assert(rb.rawBody != null) { "Raw body cannot be null" }
        val body = rb.rawBody!!

        addBox2DCollider(rb, collider.box)
        addCircleCollider(rb, collider.topCircle)
        addCircleCollider(rb, collider.bottomCircle)
    }

    fun isLocked(): Boolean {
        return world.isLocked
    }
}