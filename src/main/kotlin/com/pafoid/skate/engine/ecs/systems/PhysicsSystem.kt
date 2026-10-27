package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.hasComponent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.events.Collision
import com.pafoid.skate.engine.events.TriggerEntered
import com.pafoid.skate.engine.events.TriggerExited
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import com.pafoid.skate.engine.physics3d.PhysicsContactListener
import com.pafoid.skate.engine.physics3d.Physics3DFactory
import com.pafoid.skate.engine.physics3d.constraints.IPhysicsConstraint
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.toDegreesF
import org.joml.Quaternionf
import org.joml.Vector3f

class PhysicsSystem(
    private val debugRenderer: DebugRenderer,
    private val eventSystem: EventSystem? = null,
) : System(priority = ExecutionPriority.EARLY) {

    override val phase = SystemPhase.FIXED_UPDATE

    private val physics3d: IPhysics3D = Physics3DFactory.create(debugRenderer)

    private val bodies = query(RigidBody3D::class, topLevelOnly = true, includeDisabled = true)
    private val tracked = mutableSetOf<GameObject>()
    private val activeJoints = mutableMapOf<JointComponent, IPhysicsConstraint>()

    init {
        physics3d.contactListener = object : PhysicsContactListener {
            override fun onCollisionStarted(a: GameObject, b: GameObject, point: Vector3f, normal: Vector3f, impulse: Float) {
                eventSystem?.publish(Collision(a, b, point, normal, impulse))
                eventSystem?.publish(Collision(b, a, point, Vector3f(normal).negate(), impulse))
            }

            override fun onTriggerEnter(trigger: GameObject, other: GameObject) {
                eventSystem?.publish(TriggerEntered(trigger, other))
            }

            override fun onTriggerExit(trigger: GameObject, other: GameObject) {
                eventSystem?.publish(TriggerExited(trigger, other))
            }
        }
    }

    override fun init(scene: Scene) {
        super.init(scene)
        tracked.clear()
        states.clear()
        bodies.forEach {
            tracked.add(it)
            physics3d.add(it)
        }

        scene.getComponent<ScenePhysicsComponent>()?.let { component ->
            physics3d.debugEnabled = component.debugEnabled
            physics3d.setGravity(component.gravity)
        }
    }

    private val tempQuat = Quaternionf()
    private val tempPos = Vector3f()
    private val tempEuler = Vector3f()

    /** The two most recent simulated poses of a body, blended per frame so motion stays smooth at any frame rate. */
    private class BodyState {
        val prevPos = Vector3f()
        val currPos = Vector3f()
        val prevRot = Quaternionf()
        val currRot = Quaternionf()
        private var initialized = false

        fun push(handle: IRigidBodyHandle) {
            if (initialized) {
                prevPos.set(currPos)
                prevRot.set(currRot)
            }
            handle.getLocation(currPos)
            handle.getRotation(currRot)
            if (!initialized) {
                prevPos.set(currPos)
                prevRot.set(currRot)
                initialized = true
            }
        }
    }

    private val states = mutableMapOf<GameObject, BodyState>()
    private val blendedRot = Quaternionf()

    /** Per-frame work: writes interpolated poses into transforms and draws debug shapes. */
    fun onFrame(dt: Float, alpha: Float) {
        physics3d.debugDraw(dt)
        if (!scene.isRunning) return

        for ((go, state) in states) {
            val transform = go.getComponent<Transform>() ?: continue
            state.prevPos.lerp(state.currPos, alpha, tempPos)
            state.prevRot.nlerp(state.currRot, alpha, blendedRot)

            transform.translation.set(tempPos)
            blendedRot.getEulerAnglesXYZ(tempEuler)
            transform.rotation.set(tempEuler.x.toDegreesF(), tempEuler.y.toDegreesF(), tempEuler.z.toDegreesF())
        }
    }

    override fun fixedUpdate(fixedDt: Float) {
        if (!scene.isRunning) return
        physics3d.step(fixedDt)

        scene.getComponent<ScenePhysicsComponent>()?.let { component ->
            if (physics3d.getGravity() != component.gravity) {
                physics3d.setGravity(component.gravity)
            }
        }

        // Remove bodies whose object left the scene or lost its RigidBody3D
        val live = bodies.entities
        val liveSet = live.toHashSet()
        val removed = tracked.filter { it !in liveSet }
        removed.forEach { physics3d.remove(it) }
        tracked.removeAll(removed.toSet())
        tracked.addAll(live)

        for (go in live) {
            val body = go.getComponent<RigidBody3D>() ?: continue
            val handle = body.rawBody
            if (handle == null) {
                physics3d.add(go) // Add to physics if raw body is null
            } else {
                states.getOrPut(go) { BodyState() }.push(handle)
            }

            go.getComponent<PlayerController>()?.let {//TODO: move
                it.isGrounded = checkIfGrounded(body)
            }
        }
        states.keys.retainAll(liveSet)

        syncJoints()
    }

    /** Creates joints whose bodies are ready and removes joints whose component or object is gone. */
    private fun syncJoints() {
        val live = bodies.entities.mapNotNull { go -> go.getComponent<JointComponent>()?.let { go to it } }

        activeJoints.filter { (joint, _) -> live.none { it.second === joint } }.forEach { (joint, _) ->
            joint.constraint?.let { physics3d.removeConstraint(it) }
            joint.constraint = null
            activeJoints.remove(joint)
        }

        for ((go, joint) in live) {
            if (joint.constraint != null) continue
            val bodyA = go.getComponent<RigidBody3D>()?.rawBody ?: continue
            val bodyB = if (joint.connectedBodyId == -1) {
                // Static world anchor placed at this body's anchor point
                val worldAnchor = Vector3f(joint.anchor).add(bodyA.getLocation(Vector3f()))
                joint.connectedAnchor.set(0f, 0f, 0f)
                physics3d.createStaticAnchor(worldAnchor)
            } else {
                bodies.entities.firstOrNull { it.uId == joint.connectedBodyId }?.getComponent<RigidBody3D>()?.rawBody ?: continue
            }
            val constraint = joint.createConstraint(bodyA, bodyB)
            physics3d.addConstraint(constraint)
            joint.constraint = constraint
            activeJoints[joint] = constraint
        }
    }

    fun checkIfGrounded(body: RigidBody3D): Boolean {
        val originPosition = body.getWorldPosition()

        // Start the ray from the player's feet (below the collider)
        val feetY = originPosition.y
        val rayStart = Vector3f(originPosition.x, feetY, originPosition.z)

        // Ray goes down a small distance to detect ground
        val rayLength = 0.05f
        val rayEnd = Vector3f(rayStart.x, rayStart.y - rayLength, rayStart.z)

        // Exclude the player's own physics body from the raycast
        return physics3d.raycastClosest(rayStart, rayEnd, body) != null
    }

    fun toggleDebug() {
        physics3d.debugEnabled = !physics3d.debugEnabled
    }

    fun isDebugEnabled(): Boolean = physics3d.debugEnabled

    data class PhysicsObject(val go: GameObject, val transform: Transform?, val body: RigidBody3D)
}

/** Per-frame companion of [PhysicsSystem], which itself only runs in the fixed-update phase. */
class PhysicsFrameSystem(private val physicsSystem: PhysicsSystem) : System() {
    override val runsAfter = listOf(PlayerMotionSystem::class)

    override fun update(dt: Float) = physicsSystem.onFrame(dt, fixedStepAlpha)
}
