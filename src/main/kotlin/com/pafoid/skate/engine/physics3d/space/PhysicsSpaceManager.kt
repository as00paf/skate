package com.pafoid.skate.engine.physics3d.space

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.utils.JmeVector3f
import com.pafoid.skate.engine.utils.JomlVector3f
import org.joml.Vector3f

/**
 * Responsible for managing the physics space and all operations related to it.
 * This class handles physics space operations like adding/removing objects,
 * performing raycasts, updating the simulation, and managing gravity.
 */
class PhysicsSpaceManager(private val physicsSpace: PhysicsSpace) {

    /**
     * Gets the underlying physics space.
     */
    fun getPhysicsSpace(): PhysicsSpace {
        return physicsSpace
    }

    /**
     * Gets the current gravity vector of the physics world.
     *
     * @return The gravity vector in m/s².
     */
    fun getGravity(): JomlVector3f {
        val g = physicsSpace.getGravity(null)
        return JomlVector3f(g.x, g.y, g.z)
    }

    /**
     * Sets the gravity vector for the physics world.
     *
     * @param gravity The new gravity vector in m/s².
     */
    fun setGravity(gravity: JomlVector3f) {
        physicsSpace.setGravity(JmeVector3f(gravity.x, gravity.y, gravity.z))
    }

    /**
     * Performs a ray test (raycast) in the physics world and returns all hits.
     *
     * @param from The start point of the ray in world space.
     * @param to The end point of the ray in world space.
     * @return A list of [PhysicsRayTestResult] containing hit information.
     */
    fun rayTest(from: JomlVector3f, to: JomlVector3f): List<PhysicsRayTestResult> {
        val start = JmeVector3f(from.x, from.y, from.z)
        val end = JmeVector3f(to.x, to.y, to.z)
        return physicsSpace.rayTest(start, end)
    }

    /**
     * Adds a GameObject to the physics simulation.
     * It inspects the GameObject for [RigidBody3D] components,
     * and adds the associated rigid body to the [PhysicsSpace].
     *
     * @param go The GameObject to add.
     */
    fun add(go: GameObject) {
        val rb = go.getComponent<RigidBody3D>()
        rb?.rawBody?.let {
            physicsSpace.add(it)
        }
    }

    /**
     * Removes a GameObject from the physics simulation.
     * It removes the associated rigid body from the [PhysicsSpace].
     *
     * @param go The GameObject to remove.
     */
    fun remove(go: GameObject) {
        val rb = go.getComponent<RigidBody3D>()
        rb?.rawBody?.let {
            physicsSpace.remove(it)
        }
    }

    /**
     * Steps the physics simulation forward by the given delta time.
     *
     * @param dt The time elapsed since the last frame in seconds.
     */
    fun update(dt: Float) {
        physicsSpace.update(dt, 0)
    }

    /**
     * Gets the list of rigid bodies in the physics space.
     * Used for debug rendering purposes.
     */
    fun getRigidBodyList(): Collection<com.jme3.bullet.objects.PhysicsRigidBody> {
        return physicsSpace.rigidBodyList
    }

    /**
     * Destroys the physics space and releases associated resources.
     */
    fun destroy() {
        physicsSpace.destroy()
    }
}