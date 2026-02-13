package com.pafoid.skate.engine.physics3d.adapter

import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f

/**
 * Default implementation that creates actual physics objects.
 */
class DefaultPhysicsObjectCreator : IPhysicsObjectCreator {
    override fun createRigidBody(collisionShape: CollisionShape, mass: Float): PhysicsRigidBody {
        return PhysicsRigidBody(collisionShape, mass)
    }

    override fun createBoxCollisionShape(halfExtents: Vector3f): CollisionShape {
        return BoxCollisionShape(halfExtents)
    }

    override fun createCompoundCollisionShape(): CompoundCollisionShape {
        return CompoundCollisionShape()
    }
}