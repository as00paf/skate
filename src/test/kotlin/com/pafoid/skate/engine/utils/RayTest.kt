package com.pafoid.skate.engine.utils

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RayTest {
    private val ray = Ray(Vector3f(0f, 0f, 0f), Vector3f(1f, 0f, 0f))

    @Test
    fun Ray_DistanceToPoint_PointOnRayIsZero() {
        assertEquals(0f, ray.distanceToPoint(Vector3f(5f, 0f, 0f)), 1e-6f)
    }

    @Test
    fun Ray_DistanceToPoint_PointBesideRayIsPerpendicularDistance() {
        assertEquals(3f, ray.distanceToPoint(Vector3f(4f, 3f, 0f)), 1e-6f)
    }

    @Test
    fun Ray_DistanceToPoint_PointBehindOriginIsDistanceToOrigin() {
        assertEquals(5f, ray.distanceToPoint(Vector3f(-3f, 4f, 0f)), 1e-6f)
    }

    @Test
    fun Ray_DistanceToPoint_DoesNotMutateInputs() {
        val point = Vector3f(4f, 3f, 0f)
        ray.distanceToPoint(point)
        assertEquals(Vector3f(4f, 3f, 0f), point)
        assertEquals(Vector3f(0f, 0f, 0f), ray.origin)
        assertEquals(Vector3f(1f, 0f, 0f), ray.direction)
    }
}
