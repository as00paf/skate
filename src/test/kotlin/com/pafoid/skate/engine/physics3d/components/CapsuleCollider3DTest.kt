package com.pafoid.skate.engine.physics3d.components

import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.ecs.components.CapsuleCollider3D
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CapsuleCollider3DTest {

    @Test
    fun `default values are correct`() {
        val collider = CapsuleCollider3D()

        assertEquals(0.5f, collider.radius)
        assertEquals(1.0f, collider.height)
        assertEquals(1, collider.axis)
        assertEquals(Vector3f(0f, 0f, 0f), collider.offset)
    }

    @Test
    fun `custom values are correctly assigned`() {
        val collider = CapsuleCollider3D(
            radius = 1.2f,
            height = 3.5f,
            axis = 0,
            offset = Vector3f(1f, 2f, 3f)
        )

        assertEquals(1.2f, collider.radius)
        assertEquals(3.5f, collider.height)
        assertEquals(0, collider.axis)
        assertEquals(Vector3f(1f, 2f, 3f), collider.offset)
    }

    @Test
    fun `toShape returns Capsule description with correct properties`() {
        val collider = CapsuleCollider3D(radius = 0.8f, height = 2.0f, axis = 2)

        assertEquals(ColliderShape.Capsule(0.8f, 2.0f, 2), collider.toShape())
    }
}
