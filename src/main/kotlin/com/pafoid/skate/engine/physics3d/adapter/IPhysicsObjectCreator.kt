package com.pafoid.skate.engine.physics3d.adapter

import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f

/**
 * Interface for creating physics objects that can be mocked in tests.
 */
interface IPhysicsObjectCreator {
    fun createRigidBody(collisionShape: CollisionShape, mass: Float): PhysicsRigidBody
    fun createBoxCollisionShape(halfExtents: Vector3f): CollisionShape
    fun createCompoundCollisionShape(): CompoundCollisionShape
}