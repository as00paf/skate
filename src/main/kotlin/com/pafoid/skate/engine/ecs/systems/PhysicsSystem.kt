package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.hasComponent
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.Physics3DFactory
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.utils.toDegreesF
import org.joml.Quaternionf
import org.joml.Vector3f

class PhysicsSystem(
    private val debugRenderer: DebugRenderer
) : System(priority = ExecutionPriority.EARLY) {

    private val physics3d: IPhysics3D = Physics3DFactory.create(debugRenderer)

    private val bodies = query(RigidBody3D::class, topLevelOnly = true, includeDisabled = true)
    private val tracked = mutableSetOf<GameObject>()

    override fun init(scene: Scene) {
        super.init(scene)
        tracked.clear()
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

    override fun update(dt: Float) {
        physics3d.debugDraw(dt)
        if (!scene.isRunning) return
        // Step Bullet at deterministic fixed-step first so downstream ECS reads current frame data.
        val timeScale = scene.getComponent<DayNightCycleComponent>()?.timeScale ?: 1.0f
        physics3d.update(dt * timeScale)

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
            val po = PhysicsObject(go, go.getComponent<Transform>(), body)
            po.body.rawBody?.let { body ->
                try {
                    val transform = po.transform ?: return
                    val pos = body.getLocation(tempPos)
                    val rot = body.getRotation(tempQuat)

                    transform.translation.set(pos.x, pos.y, pos.z)

                    rot.getEulerAnglesXYZ(tempEuler)
                    transform.rotation.set(
                        tempEuler.x.toDegreesF(),
                        tempEuler.y.toDegreesF(),
                        tempEuler.z.toDegreesF()
                    )
                } catch (e: AssertionError) {
                    // Body is not yet in physics world - skip this frame
                }
            } ?: physics3d.add(po.go) // Add to physics if raw body is null

            po.go.getComponent<PlayerController>()?.let {//TODO: move
                it.isGrounded = checkIfGrounded(po.body)
            }
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
