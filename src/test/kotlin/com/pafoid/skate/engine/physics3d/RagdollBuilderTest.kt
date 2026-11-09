package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** PHY-17d: the Mixamo ragdoll builds and simulates on every backend with joints. */
class RagdollBuilderTest {
    private lateinit var physics: IPhysics3D

    @AfterEach
    fun tearDown() {
        if (::physics.isInitialized) physics.destroy()
    }

    private fun mixamoSkeleton(): Skeleton {
        val names = listOf(
            "Hips", "Spine", "Spine1", "Spine2", "Head", "LeftUpLeg", "LeftLeg", "RightUpLeg", "RightLeg",
            "LeftArm", "LeftForeArm", "RightArm", "RightForeArm",
        )
        val root = Bone(0, "mixamorig:${names[0]}")
        names.drop(1).forEachIndexed { i, name -> root.children += Bone(i + 1, "mixamorig:$name") }
        return Skeleton(root, names.size)
    }

    @ParameterizedTest
    @MethodSource("com.pafoid.skate.engine.physics3d.PhysicsBackends#joints")
    fun ragdoll_MixamoRagdoll_BuildsAndFallsAsOnePiece(backend: PhysicsBackend) {
        physics = PhysicsBackends.create(backend)
        val ragdoll = RagdollBuilder.buildMixamoRagdoll(physics, mixamoSkeleton())
        assertEquals(13, ragdoll.boneBodies.size)
        assertEquals(12, ragdoll.joints.size)
        ragdoll.boneBodies.values.forEach { it.setLocation(Vector3f(0f, 10f, 0f)) }

        ragdoll.joints.forEach { physics.addConstraint(it) }
        ragdoll.boneBodies.values.forEach { it.isKinematic = false }
        repeat(60) { physics.step(1f / 60f) }

        val positions = ragdoll.boneBodies.values.map { it.getLocation(Vector3f()) }
        assertTrue(positions.all { it.isFinite }, "positions $positions")
        assertTrue(positions.all { it.y < 9f }, "bones should fall, $positions")
        // Joints keep the bones together (pivots are 0.3 m apart along each chain)
        val spread = positions.maxOf { a -> positions.maxOf { b -> a.distance(b) } }
        assertTrue(spread < 3f, "ragdoll flew apart: spread $spread")
    }
}
