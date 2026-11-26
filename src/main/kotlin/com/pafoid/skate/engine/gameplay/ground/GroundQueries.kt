package com.pafoid.skate.engine.gameplay.ground

import com.pafoid.skate.engine.physics3d.IPhysicsBody3D
import com.pafoid.skate.engine.physics3d.PhysicsHit
import org.joml.Vector3f

/** The physics queries ground probing needs, so it runs against a fake in tests. */
interface GroundQueries {
    fun raycast(from: Vector3f, to: Vector3f, layerMask: Int, exclude: IPhysicsBody3D?): PhysicsHit?

    fun sphereCast(radius: Float, from: Vector3f, to: Vector3f, layerMask: Int, exclude: IPhysicsBody3D?): PhysicsHit?
}
