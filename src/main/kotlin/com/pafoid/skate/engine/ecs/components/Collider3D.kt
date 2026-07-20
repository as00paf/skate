package com.pafoid.skate.engine.ecs.components

import com.jme3.bullet.collision.shapes.CollisionShape
import org.joml.Vector3f

interface Collider3D {
    val offset: Vector3f
    fun createShape(): CollisionShape
}
