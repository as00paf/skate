package com.pafoid.skate.engine.physics3d.interfaces

import com.jme3.math.Vector3f
import com.pafoid.skate.engine.physics3d.BodyType

/**
 * Interface for a factory that creates physics objects.
 */
interface IPhysicsObjectFactory {
    fun createRigidBody(collisionShape: ICollisionShape, mass: Float): IPhysicsRigidBody
    fun createBoxCollisionShape(halfExtents: Vector3f): ICollisionShape
    fun createCompoundCollisionShape(): ICollisionShape
}