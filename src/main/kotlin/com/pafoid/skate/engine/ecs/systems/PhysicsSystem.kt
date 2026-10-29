package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RagdollComponent
import com.pafoid.skate.engine.ecs.components.RagdollState
import com.pafoid.skate.engine.gameplay.ground.GroundDetector
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
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
    private val physics3d: IPhysics3D = Physics3DFactory.create(debugRenderer),
) : System(priority = ExecutionPriority.EARLY) {

    override val phase = SystemPhase.FIXED_UPDATE

    private val bodies = query(RigidBody3D::class, topLevelOnly = true, includeDisabled = true)
    private val activeJoints = mutableMapOf<JointComponent, IPhysicsConstraint>()
    private val ragdolls = query(RagdollComponent::class, topLevelOnly = true, includeDisabled = true)
    private val activeRagdolls = HashSet<RagdollComponent>()

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
        states.clear()
        stateList.clear()
        bodies.forEach {
            trackedState(it)
            physics3d.add(it)
        }

        scene.getComponent<ScenePhysicsComponent>()?.let { component ->
            physics3d.debugEnabled = component.debugEnabled
            appliedGravity.set(component.gravity)
            physics3d.setGravity(component.gravity)
        }
    }

    // Scratch values reused every step/frame so the sync loops do not allocate
    private val tempPos = Vector3f()
    private val tempEuler = Vector3f()
    private val blendedRot = Quaternionf()
    private val appliedGravity = Vector3f(0f, -9.81f, 0f)

    /** The two most recent simulated poses of a body, blended per frame so motion stays smooth at any frame rate. */
    private class BodyState(val go: GameObject) {
        val prevPos = Vector3f()
        val currPos = Vector3f()
        val prevRot = Quaternionf()
        val currRot = Quaternionf()
        var transform: Transform? = null
        var stamp = 0L
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

    private val states = HashMap<GameObject, BodyState>()
    private val stateList = ArrayList<BodyState>()
    private var stepStamp = 0L

    private fun trackedState(go: GameObject): BodyState =
        states.getOrPut(go) { BodyState(go).also { stateList.add(it) } }

    /** Per-frame work: writes interpolated poses into transforms and draws debug shapes. */
    fun onFrame(dt: Float, alpha: Float) {
        physics3d.debugDraw(dt)
        if (!scene.isRunning) return

        for (i in stateList.indices) {
            val state = stateList[i]
            val transform = state.transform ?: state.go.getComponent<Transform>()?.also { state.transform = it } ?: continue
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
            if (appliedGravity != component.gravity) {
                appliedGravity.set(component.gravity)
                physics3d.setGravity(component.gravity)
            }
        }

        val stamp = ++stepStamp
        var liveCount = 0
        bodies.forEach { go ->
            liveCount++
            val state = trackedState(go)
            state.stamp = stamp
            val body = go.getComponent<RigidBody3D>() ?: return@forEach
            val handle = body.rawBody
            if (handle == null) {
                physics3d.add(go) // Add to physics if raw body is null
            } else {
                state.push(handle)
            }

            go.getComponent<PlayerController>()?.let { probeGround(it, body) }
        }
        if (stateList.size != liveCount) removeStaleBodies(stamp)

        syncJoints()
        syncRagdolls()
    }

    /**
     * Ragdoll bones are created by the backend ([RagdollBuilder][com.pafoid.skate.engine.physics3d.RagdollBuilder]);
     * here their joints join the world once, bones follow the ragdoll state, and everything leaves with the component.
     */
    private fun syncRagdolls() {
        var live = 0
        ragdolls.forEach { go ->
            val ragdoll = go.getComponent<RagdollComponent>() ?: return@forEach
            live++
            if (activeRagdolls.add(ragdoll)) ragdoll.joints.forEach { physics3d.addConstraint(it) }
            if (ragdoll.state == RagdollState.RAGDOLL) {
                ragdoll.boneBodies.values.forEach { if (it.isKinematic) it.isKinematic = false }
            }
        }
        if (live != activeRagdolls.size) removeStaleRagdolls()
    }

    private fun removeStaleRagdolls() {
        val liveSet = ragdolls.entities.mapNotNull { it.getComponent<RagdollComponent>() }
        activeRagdolls.filter { r -> liveSet.none { it === r } }.forEach { ragdoll ->
            ragdoll.joints.forEach { physics3d.removeConstraint(it) }
            ragdoll.boneBodies.values.forEach { physics3d.removeRigidBody(it) }
            activeRagdolls.remove(ragdoll)
        }
    }

    /** Drops bodies whose object left the scene or lost its RigidBody3D; only runs when membership changed. */
    private fun removeStaleBodies(stamp: Long) {
        val stale = stateList.filter { it.stamp != stamp }
        stale.forEach {
            physics3d.remove(it.go)
            states.remove(it.go)
        }
        stateList.removeAll(stale.toSet())
    }

    /** Creates joints whose bodies are ready and removes joints whose component or object is gone. */
    private fun syncJoints() {
        var liveActive = 0
        bodies.forEach { go ->
            val joint = go.getComponent<JointComponent>() ?: return@forEach
            if (joint.constraint != null) {
                if (joint in activeJoints) liveActive++
                return@forEach
            }
            createJoint(go, joint)
        }
        if (liveActive != activeJoints.size) removeStaleJoints()
    }

    private fun removeStaleJoints() {
        val live = bodies.entities.mapNotNull { it.getComponent<JointComponent>() }
        activeJoints.keys.filter { joint -> live.none { it === joint } }.forEach { joint ->
            joint.constraint?.let { physics3d.removeConstraint(it) }
            joint.constraint = null
            activeJoints.remove(joint)
        }
    }

    private fun createJoint(go: GameObject, joint: JointComponent) {
        val bodyA = go.getComponent<RigidBody3D>()?.rawBody ?: return
        val bodyB = if (joint.connectedBodyId == -1) {
            // Static world anchor placed at this body's anchor point
            val worldAnchor = Vector3f(joint.anchor).add(bodyA.getLocation(Vector3f()))
            joint.connectedAnchor.set(0f, 0f, 0f)
            physics3d.createStaticAnchor(worldAnchor)
        } else {
            bodies.entities.firstOrNull { it.uId == joint.connectedBodyId }?.getComponent<RigidBody3D>()?.rawBody ?: return
        }
        val constraint = joint.createConstraint(bodyA, bodyB)
        physics3d.addConstraint(constraint)
        joint.constraint = constraint
        activeJoints[joint] = constraint
    }

    /** Probes the ground below a player-controlled body and any climbable step ahead of it. */
    private fun probeGround(controller: PlayerController, body: RigidBody3D) {
        val config = controller.behavior.ground
        val detector = GroundDetector(config) { from, to -> physics3d.raycastClosest(from, to, body) }
        val origin = body.getWorldPosition()
        val feet = Vector3f(origin.x, origin.y - config.footOffset, origin.z)

        controller.wasGrounded = controller.ground.standing
        controller.ground = detector.probe(feet)

        val velocity = body.linearVelocity
        controller.stepAhead = if (config.stepUp) detector.findStep(feet, velocity.x, velocity.z) else null
    }

    fun toggleDebug() {
        physics3d.debugEnabled = !physics3d.debugEnabled
    }

    fun isDebugEnabled(): Boolean = physics3d.debugEnabled
}

/** Per-frame companion of [PhysicsSystem], which itself only runs in the fixed-update phase. */
class PhysicsFrameSystem(private val physicsSystem: PhysicsSystem) : System() {
    override val runsAfter = listOf(PlayerMotionSystem::class)

    override fun update(dt: Float) = physicsSystem.onFrame(dt, fixedStepAlpha)
}
