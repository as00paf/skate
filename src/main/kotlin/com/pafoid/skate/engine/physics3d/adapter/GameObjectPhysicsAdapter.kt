package com.pafoid.skate.engine.physics3d.adapter

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.components.Collider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.utils.JmeVector3f
import org.joml.Quaternionf

/**
 * Responsible for adapting GameObjects to the physics engine.
 * This class handles the integration between GameObjects and the physics space,
 * including adding, removing, and updating physics representations of GameObjects.
 */
class GameObjectPhysicsAdapter {
    
    /**
     * Adds a GameObject to the physics simulation.
     * It inspects the GameObject for [RigidBody3D] and [Collider3D] components,
     * creates the necessary Bullet shapes, and adds them to the [PhysicsSpace].
     *
     * @param go The GameObject to add.
     * @param physicsSpace The physics space to add the object to.
     */
    fun add(go: GameObject, physicsSpace: PhysicsSpace) {
        val rb = go.getComponent<RigidBody3D>()
        if (rb != null) {
            val desiredMass = if (rb.bodyType == BodyType.Static) 0f else rb.mass

            // Check if we need to rebuild due to mass change
            if (rb.rawBody != null) {
                if (rb.rawBody?.mass != desiredMass) {
                    physicsSpace.remove(rb.rawBody)
                    rb.rawBody = null
                } else {
                    // Body exists and mass is correct, just sync properties
                    update(go, physicsSpace)
                    return
                }
            }

            if (rb.rawBody == null) {
                val colliders = go.components.filterIsInstance<Collider3D>()
                val compound = CompoundCollisionShape()

                colliders.forEach { c ->
                    val shape = c.createShape()
                    compound.addChildShape(shape, JmeVector3f(c.offset.x, c.offset.y, c.offset.z))
                }

                // If no colliders, provide a default box
                if (colliders.isEmpty()) {
                    val shape = BoxCollisionShape(JmeVector3f(1f, 1f, 1f))
                    shape.margin = 0.04f
                    compound.addChildShape(shape, JmeVector3f(0f, 0f, 0f))
                }

                val body = PhysicsRigidBody(compound, desiredMass)
                rb.rawBody = body
                update(go, physicsSpace) // Initial property sync

                if (rb.bodyType == BodyType.Kinematic) {
                    body.isKinematic = true
                }

                if (rb.useCCD) {
                    body.setCcdMotionThreshold(0.1f)
                    body.setCcdSweptSphereRadius(0.1f)
                }

                physicsSpace.add(body)
            }
        }
    }

    /**
     * Updates the physics properties of a GameObject's rigid body based on its component state.
     * This is typically used to sync changes from the editor or game logic to the physics engine.
     *
     * @param go The GameObject to update.
     * @param physicsSpace The physics space containing the object.
     */
    fun update(go: GameObject, physicsSpace: PhysicsSpace) {
        val rb = go.getComponent<RigidBody3D>()
        val body = rb?.rawBody ?: return
        syncBodyProperties(body, rb, go)
    }

    /**
     * Synchronizes properties from our component-based representation ([RigidBody3D], [GameObject] transform)
     * to the underlying Bullet [PhysicsRigidBody].
     *
     * @param body The raw Bullet rigid body to update.
     * @param rb The component containing the desired physics properties (friction, damping).
     * @param go The game object providing the transform (position, rotation, scale).
     */
    private fun syncBodyProperties(body: PhysicsRigidBody, rb: RigidBody3D, go: GameObject) {
        val transform = go.getComponent<Transform>() ?: return
        val trans = transform.translation
        val rot = transform.rotation
        val scale = transform.scale

        body.setPhysicsLocation(JmeVector3f(trans.x, trans.y, trans.z))
        body.collisionShape.setScale(JmeVector3f(scale.x, scale.y, scale.z))
        body.friction = rb.friction
        body.setDamping(rb.linearDamping, rb.angularDamping)

        val q = Quaternionf().rotationXYZ(
            Math.toRadians(rot.x.toDouble()).toFloat(),
            Math.toRadians(rot.y.toDouble()).toFloat(),
            Math.toRadians(rot.z.toDouble()).toFloat()
        )
        body.setPhysicsRotation(com.jme3.math.Quaternion(q.x, q.y, q.z, q.w))
    }

    /**
     * Removes a GameObject from the physics simulation.
     * It destroys the associated Bullet rigid body.
     *
     * @param go The GameObject to remove.
     * @param physicsSpace The physics space to remove the object from.
     */
    fun remove(go: GameObject, physicsSpace: PhysicsSpace) {
        val rb = go.getComponent<RigidBody3D>()
        rb?.rawBody?.let {
            physicsSpace.remove(it)
            rb.rawBody = null
        }
    }
}