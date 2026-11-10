package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RagdollComponent
import com.pafoid.skate.engine.ecs.components.RagdollState
import com.pafoid.skate.engine.ecs.components.VehicleComponent
import com.pafoid.skate.engine.physics3d.RaycastVehicleSolver
import com.pafoid.skate.engine.gameplay.MotionMath
import com.pafoid.skate.engine.gameplay.ground.GroundDetector
import com.pafoid.skate.engine.gameplay.ik.FootPlacement
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.events.Collision
import com.pafoid.skate.engine.events.CollisionMeshUnavailable
import com.pafoid.skate.engine.events.PhysicsBackendFellBack
import com.pafoid.skate.engine.events.TriggerEntered
import com.pafoid.skate.engine.events.TriggerExited
import com.pafoid.skate.engine.physics3d.ALL_LAYERS
import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.CollisionMeshes
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import com.pafoid.skate.engine.physics3d.PhysicsContactListener
import com.pafoid.skate.engine.physics3d.Physics3DFactory
import com.pafoid.skate.engine.physics3d.constraints.IPhysicsConstraint
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.toDegreesF
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.Collections
import java.util.IdentityHashMap

class PhysicsSystem(
    private val debugRenderer: DebugRenderer,
    private val eventSystem: EventSystem? = null,
    private val physics3d: IPhysics3D = Physics3DFactory.create(debugRenderer) { fallback ->
        eventSystem?.publish(PhysicsBackendFellBack(fallback.requested, fallback.used, fallback.reason))
    },
) : System(priority = ExecutionPriority.EARLY) {

    override val phase = SystemPhase.FIXED_UPDATE

    private val bodies = query(RigidBody3D::class, topLevelOnly = true, includeDisabled = true)
    // Identity-keyed: joint/ragdoll components are data classes, so a reloaded scene's copy would equal the old one
    private val activeJoints = IdentityHashMap<JointComponent, IPhysicsConstraint>()
    /** Static world anchors created for joints with no connected body; removed with their joint. */
    private val jointAnchors = IdentityHashMap<JointComponent, IRigidBodyHandle>()
    private val ragdolls = query(RagdollComponent::class, topLevelOnly = true, includeDisabled = true)
    private val vehicles = query(VehicleComponent::class, RigidBody3D::class, topLevelOnly = true)
    private val vehicleSolver = RaycastVehicleSolver(physics3d)
    private val activeRagdolls: MutableSet<RagdollComponent> = Collections.newSetFromMap(IdentityHashMap())

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
        releaseObjectsOutsideScene()
        // Adds new bodies; bodies kept from a previous load restart from the loaded pose
        bodies.forEach { teleport(it) }
        simulating = false
        publishCollisionMeshFailures()

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

        /** The Transform values the body was last synced to (or that the simulation last wrote). */
        private val syncedPos = Vector3f()
        private val syncedRot = Vector3f()
        private val syncedScale = Vector3f()

        fun markSynced(t: Transform) {
            syncedPos.set(t.translation)
            syncedRot.set(t.rotation)
            syncedScale.set(t.scale)
        }

        fun poseMatches(t: Transform) = t.translation == syncedPos && t.rotation == syncedRot
        fun scaleMatches(t: Transform) = t.scale == syncedScale

        /** Restarts interpolation at the body's current pose so a teleport does not blend from the old one. */
        fun restartAt(handle: IRigidBodyHandle) {
            initialized = false
            push(handle)
        }

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

    /**
     * Per-frame work. In edit mode, bodies follow Transform edits (see [syncEditedTransforms]); while running,
     * interpolated poses are written into transforms. Then debug shapes are drawn.
     */
    fun onFrame(dt: Float, alpha: Float) {
        if (!scene.isRunning) {
            simulating = false
            syncEditedTransforms()
        }
        physics3d.debugEnabled = isDebugEnabled()
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
            state.markSynced(transform)
        }
    }

    /** False until the first fixed step after the scene starts running. */
    private var simulating = false

    /**
     * Brings the world in line with Transforms changed outside the simulation (inspector, gizmos, undo/redo, scripts
     * in edit mode). Bodies whose pose changed are teleported with velocities cleared, bodies whose scale changed are
     * rebuilt, and new or removed objects gain or lose their body. Only runs while the scene is not simulating:
     * during play other systems (e.g. [MountFollowSystem]) write transforms on purpose; use [teleport] there.
     */
    fun syncEditedTransforms() {
        val stamp = ++stepStamp
        var liveCount = 0
        bodies.forEach { go ->
            liveCount++
            val state = trackedState(go)
            state.stamp = stamp
            val transform = go.getComponent<Transform>()?.also { state.transform = it } ?: return@forEach
            val body = go.getComponent<RigidBody3D>() ?: return@forEach
            when {
                body.rawBody == null -> physics3d.add(go)
                !state.scaleMatches(transform) -> {
                    physics3d.remove(go)
                    physics3d.add(go)
                }
                !state.poseMatches(transform) -> physics3d.teleport(go)
                else -> return@forEach
            }
            state.markSynced(transform)
            body.rawBody?.let { state.restartAt(it) }
        }
        if (stateList.size != liveCount) removeStaleBodies(stamp)
        publishCollisionMeshFailures()
    }

    /** Reports `meshRef` models that could not be loaded (each once; see [CollisionMeshes]). */
    private fun publishCollisionMeshFailures() {
        CollisionMeshes.drainFailures().forEach { eventSystem?.publish(CollisionMeshUnavailable(it.reference, it.reason)) }
    }

    /**
     * Moves [go]'s body to its current Transform (rebuilding it if the scale changed) and clears its velocities.
     * For poses set on purpose at runtime (respawn, editor edits during play).
     */
    fun teleport(go: GameObject) {
        val transform = go.getComponent<Transform>() ?: return physics3d.add(go)
        val body = go.getComponent<RigidBody3D>() ?: return
        val state = trackedState(go)
        if (body.rawBody != null && !state.scaleMatches(transform)) physics3d.remove(go)
        physics3d.teleport(go)
        state.transform = transform
        state.markSynced(transform)
        body.rawBody?.let { state.restartAt(it) }
    }

    override fun fixedUpdate(fixedDt: Float) {
        if (!scene.isRunning) {
            simulating = false
            return
        }
        if (!simulating) {
            // Catch edits made since the last edit-mode frame so play starts from the edited pose
            syncEditedTransforms()
            simulating = true
        }
        vehicles.forEach { go ->
            val vehicle = go.getComponent<VehicleComponent>() ?: return@forEach
            val body = go.getComponent<RigidBody3D>() ?: return@forEach
            vehicleSolver.step(vehicle, body, fixedDt)
        }
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
                teleport(go) // object or body added while running
            } else {
                state.push(handle)
            }

            go.getComponent<PlayerController>()?.let { probeGround(it, body) }
        }
        if (stateList.size != liveCount) removeStaleBodies(stamp)

        syncJoints()
        syncRagdolls()
        publishCollisionMeshFailures()
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

    /** Number of rigid bodies in the physics world; each scene body must be registered exactly once. */
    val worldBodyCount: Int get() = physics3d.rigidBodyCount

    /**
     * On (re)load, removes every body, joint and ragdoll registered for objects that are not in the newly loaded scene.
     * Without this, opening another scene instance (new project, scene switch, play snapshot restore) left the old
     * instance's bodies in the world, overlapping the new ones.
     */
    private fun releaseObjectsOutsideScene() {
        val live: MutableSet<GameObject> = Collections.newSetFromMap(IdentityHashMap())
        live.addAll(bodies.entities)
        val stale = stateList.filter { it.go !in live }
        stale.forEach {
            physics3d.remove(it.go)
            states.remove(it.go)
        }
        stateList.removeAll(stale.toSet())
        removeStaleJoints()
        removeStaleRagdolls()
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
            jointAnchors.remove(joint)?.let { physics3d.removeRigidBody(it) }
        }
    }

    private fun createJoint(go: GameObject, joint: JointComponent) {
        val bodyA = go.getComponent<RigidBody3D>()?.rawBody ?: return
        val bodyB = if (joint.connectedBodyGuid.isEmpty()) {
            // Static world anchor placed at this body's anchor point
            val worldAnchor = Vector3f(joint.anchor).add(bodyA.getLocation(Vector3f()))
            joint.connectedAnchor.set(0f, 0f, 0f)
            physics3d.createStaticAnchor(worldAnchor).also { jointAnchors[joint] = it }
        } else {
            bodies.entities.firstOrNull { it.guid == joint.connectedBodyGuid }?.getComponent<RigidBody3D>()?.rawBody ?: return
        }
        val constraint = joint.createConstraint(bodyA, bodyB)
        physics3d.addConstraint(constraint)
        joint.constraint = constraint
        activeJoints[joint] = constraint
    }

    /** Probes the ground below a player-controlled body and any climbable step ahead of it. */
    private fun probeGround(controller: PlayerController, body: RigidBody3D) {
        val config = controller.behavior.ground
        // Probe only what the character collides with: a ridden board (mount layer) must not count as ground
        val detector = GroundDetector(config) { from, to -> physics3d.raycast(from, to, body.collisionMask, body)?.fraction }
        val origin = body.getWorldPosition()
        val feet = Vector3f(origin.x, origin.y - config.footOffset, origin.z)

        controller.wasGrounded = controller.ground.standing
        controller.ground = detector.probe(feet)
        // What is underfoot: a sphere as wide as the ground samples, so a thin rail between the rays still counts.
        controller.groundTag = physics3d.shapeCast(
            ColliderShape.Capsule(maxOf(config.sampleRadius, GROUND_TAG_MIN_RADIUS), SPHERE_CAPSULE_HEIGHT, 1),
            Vector3f(feet).add(0f, config.probeStartHeight + GROUND_TAG_MIN_RADIUS, 0f),
            Vector3f(feet).sub(0f, config.snapDistance, 0f),
            body.collisionMask, body,
        )?.takeIf { it.normal.y > GROUND_TAG_MIN_NORMAL_Y }?.gameObject?.tag?.takeIf { it.isNotEmpty() }

        val velocity = body.linearVelocity
        controller.stepAhead = if (config.stepUp) detector.findStep(feet, velocity.x, velocity.z) else null

        val yaw = MotionMath.yawOfRotation(body.getRotation())
        controller.footIk.legs.forEachIndexed { i, leg ->
            val target = controller.footTargets.getOrNull(i) ?: return@forEachIndexed
            FootPlacement.compute(feet, yaw, leg, controller.footIk, target) { from, to ->
                physics3d.raycast(from, to, body.collisionMask, body)
            }
        }
    }

    /**
     * Flips the collider debug view. The scene's [ScenePhysicsComponent] is the source of truth when present,
     * so the toolbar state, scene saves and a re-[init] (play / snapshot restore) all agree with what is drawn.
     */
    fun toggleDebug() {
        val enabled = !isDebugEnabled()
        physics3d.debugEnabled = enabled
        scene.getComponent<ScenePhysicsComponent>()?.debugEnabled = enabled
    }

    fun isDebugEnabled(): Boolean = scene.getComponent<ScenePhysicsComponent>()?.debugEnabled ?: physics3d.debugEnabled

    /**
     * Sweeps a sphere of [radius] from [from] to [to] against solid bodies (triggers ignored), skipping [exclude];
     * returns the hit fraction (0..1) or null when clear. Used by camera spring arms.
     */
    fun sphereCast(from: Vector3f, to: Vector3f, radius: Float, exclude: IPhysicsBody3D? = null): Float? =
        physics3d.shapeCast(ColliderShape.Capsule(radius, SPHERE_CAPSULE_HEIGHT, 1), from, to, ALL_LAYERS, exclude)?.fraction

    private companion object {
        /** A capsule this short is a sphere for every practical purpose (not every backend has a sphere shape). */
        const val SPHERE_CAPSULE_HEIGHT = 1e-3f
        const val GROUND_TAG_MIN_RADIUS = 0.05f
        /** Only a surface hit from above counts as underfoot, not the side of a ledge brushed in passing. */
        const val GROUND_TAG_MIN_NORMAL_Y = 0.5f
    }
}

/** Per-frame companion of [PhysicsSystem], which itself only runs in the fixed-update phase. */
class PhysicsFrameSystem(private val physicsSystem: PhysicsSystem) : System() {
    override val runsAfter = listOf(PlayerMotionSystem::class)

    override fun update(dt: Float) = physicsSystem.onFrame(dt, fixedStepAlpha)
}
