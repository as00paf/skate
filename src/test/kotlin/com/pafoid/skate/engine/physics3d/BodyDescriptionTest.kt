package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.attachComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CapsuleCollider3D
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.MeshCollider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BodyDescriptionTest {

    private fun go(rb: RigidBody3D?, vararg colliders: Component, transform: Transform = Transform()) = GameObject("Body").apply {
        addComponent(transform)
        rb?.let { attachComponent(it) }
        colliders.forEach { attachComponent(it) }
    }

    @Test
    fun BodyDescription_NoRigidBody_IsNull() {
        assertNull(BodyDescription.of(go(null, BoxCollider3D())))
    }

    @Test
    fun BodyDescription_MotionType_ResolvesStaticKinematicDynamic() {
        assertEquals(BodyType.Dynamic, BodyDescription.of(go(RigidBody3D(2f)))!!.motionType)
        assertEquals(2f, BodyDescription.of(go(RigidBody3D(2f)))!!.mass)

        val static = BodyDescription.of(go(RigidBody3D(2f, BodyType.Static)))!!
        assertEquals(BodyType.Static, static.motionType)
        assertEquals(0f, static.mass)

        val kinematic = BodyDescription.of(go(RigidBody3D(3f, BodyType.Kinematic)))!!
        assertEquals(BodyType.Kinematic, kinematic.motionType)
        assertEquals(3f, kinematic.mass)
    }

    @Test
    fun BodyDescription_DynamicWithoutMass_IsStatic() {
        val desc = BodyDescription.of(go(RigidBody3D(0f)))!!
        assertEquals(BodyType.Static, desc.motionType)
        assertEquals(0f, desc.mass)
    }

    @Test
    fun BodyDescription_StaticOnlyCollider_ForcesStaticEvenWhenKinematic() {
        val desc = BodyDescription.of(go(RigidBody3D(5f, BodyType.Kinematic), MeshCollider3D()))!!
        assertEquals(BodyType.Static, desc.motionType)
        assertEquals(0f, desc.mass)
    }

    @Test
    fun BodyDescription_Parts_OnePerColliderWithOffsets() {
        val desc = BodyDescription.of(
            go(RigidBody3D(), BoxCollider3D(Vector3f(1f)), CapsuleCollider3D(offset = Vector3f(0f, 2f, 0f)))
        )!!
        assertEquals(2, desc.parts.size)
        assertTrue(desc.parts[0].shape is ColliderShape.Box)
        assertEquals(Vector3f(0f, 2f, 0f), desc.parts[1].offset)
        assertFalse(desc.isSingleCentredShape)
        assertTrue(BodyDescription.of(go(RigidBody3D(), BoxCollider3D()))!!.isSingleCentredShape)
    }

    @Test
    fun BodyDescription_PoseAndMaterial_CopiedFromComponents() {
        val transform = Transform(Vector3f(1f, 2f, 3f)).apply { rotation.set(0f, 90f, 0f); scale.set(2f) }
        val rb = RigidBody3D().apply {
            friction = 0.7f; restitution = 0.3f; linearDamping = 0.1f; angularDamping = 0.2f
            collisionLayer = 4; collisionMask = 6; isTrigger = true
        }
        val desc = BodyDescription.of(go(rb, BoxCollider3D(margin = 0.02f), transform = transform))!!

        assertEquals(Vector3f(1f, 2f, 3f), desc.position)
        assertEquals(Vector3f(2f), desc.scale)
        val expected = Quaternionf().rotationY(Math.toRadians(90.0).toFloat())
        assertTrue(desc.rotation.equals(expected, 1e-5f))
        assertEquals(0.7f, desc.friction)
        assertEquals(0.3f, desc.restitution)
        assertEquals(0.1f, desc.linearDamping)
        assertEquals(0.2f, desc.angularDamping)
        assertEquals(4, desc.collisionLayer)
        assertEquals(6, desc.collisionMask)
        assertTrue(desc.isTrigger)
        assertEquals(0.02f, desc.boxMargin)
    }

    @Test
    fun BodyDescription_Ccd_HalfTheThinnestScaledDimension() {
        val transform = Transform().apply { scale.set(2f) }
        val desc = BodyDescription.of(go(RigidBody3D(useCCD = true), BoxCollider3D(Vector3f(1f, 0.1f, 1f)), transform = transform))!!
        assertEquals(0.1f, desc.ccdRadius!!, 1e-6f)

        assertNull(BodyDescription.of(go(RigidBody3D(), BoxCollider3D()))!!.ccdRadius)
        assertNull(BodyDescription.of(go(RigidBody3D(1f, BodyType.Static, useCCD = true), BoxCollider3D()))!!.ccdRadius)
    }

    @Test
    fun BodyDescription_CcdRadius_HasAFloorAndAHullDefault() {
        assertEquals(0.01f, BodyDescription.ccdRadius(listOf(ColliderShape.Box(Vector3f(0.001f))), Vector3f(1f)))
        assertEquals(0.05f, BodyDescription.ccdRadius(listOf(ColliderShape.ConvexHull(emptyList())), Vector3f(1f)))
        assertEquals(0.05f, BodyDescription.ccdRadius(emptyList(), Vector3f(1f)))
    }
}
