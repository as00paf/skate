package com.pafoid.skate.engine.physics3d.interfaces

import com.jme3.math.Vector3f

/**
 * Interface for a collision shape that abstracts the underlying physics engine implementation.
 */
interface ICollisionShape {
    fun setScale(scale: Vector3f)
    var margin: Float
}