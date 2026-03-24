package com.pafoid.skate.engine.ecs.systems

import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RagdollComponent
import com.pafoid.skate.engine.ecs.components.RagdollState
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.joml.Matrix4f
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Unit tests for RagdollSystem.
 * Follows the MethodName_Scenario_ExpectedBehavior pattern.
 * Respects the zero-assertion policy (no !! operator).
 */
class RagdollSystemTest {

    private lateinit var scene: Scene
    private lateinit var ragdollSystem: RagdollSystem

    @BeforeEach
    fun setup() {
        scene = mockk(relaxed = true)
        ragdollSystem = RagdollSystem()
        // In this engine, the scene is typically injected or set on the system
        ragdollSystem.init(scene)
    }

    @Test
    fun update_RagdollStateIsRagdoll_UpdatesBoneTransformsFromPhysics() {
        // Arrange
        val gameObject = mockk<GameObject>(relaxed = true)
        val ragdollComponent = RagdollComponent().apply { state = RagdollState.RAGDOLL }
        val transform = Transform()

        val rootBone = Bone(0, "Root", Matrix4f())
        val skeleton = Skeleton(rootBone, 1)
        val pose = SkeletonPose(skeleton)
        val skeletonComponent = SkeletonComponent(pose)

        val mockBody = mockk<PhysicsRigidBody>(relaxed = true)
        val physicsLoc = com.jme3.math.Vector3f(1f, 2f, 3f)
        val physicsRot = com.jme3.math.Quaternion(0f, 0f, 0f, 1f)

        every { mockBody.getPhysicsLocation(any()) } returns physicsLoc
        every { mockBody.getPhysicsRotation(any()) } returns physicsRot

        ragdollComponent.boneBodies["Root"] = mockBody

        every { gameObject.getComponent<RagdollComponent>() } returns ragdollComponent
        every { gameObject.getComponent<SkeletonComponent>() } returns skeletonComponent
        every { gameObject.getComponent<Transform>() } returns transform
        every { scene.gameObjectManager.gameObjects } returns mutableListOf(gameObject)

        // Act
        ragdollSystem.update(0.016f)

        // Assert
        val expectedMatrix = Matrix4f().translation(1f, 2f, 3f)
        // With identity transform on the GameObject, the physics world position 
        // maps directly to the bone's global transform in GameObject space.
        assertEquals(expectedMatrix, pose.globalTransforms[0])
    }

    @Test
    fun update_RagdollStateIsAnimated_SynchronizesPhysicsBodiesWithAnimation() {
        // Arrange
        val gameObject = mockk<GameObject>(relaxed = true)
        val ragdollComponent = RagdollComponent().apply { state = RagdollState.ANIMATED }
        val transform = Transform()

        val rootBone = Bone(0, "Root", Matrix4f())
        val skeleton = Skeleton(rootBone, 1)
        val pose = SkeletonPose(skeleton)
        // Set a specific transform in the pose (e.g., bone moved to 5, 5, 5)
        pose.globalTransforms[0].translation(5f, 5f, 5f)
        val skeletonComponent = SkeletonComponent(pose)

        val mockBody = mockk<PhysicsRigidBody>(relaxed = true)
        ragdollComponent.boneBodies["Root"] = mockBody

        every { gameObject.getComponent<RagdollComponent>() } returns ragdollComponent
        every { gameObject.getComponent<SkeletonComponent>() } returns skeletonComponent
        every { gameObject.getComponent<Transform>() } returns transform
        every { scene.gameObjectManager.gameObjects } returns mutableListOf(gameObject)

        // Act
        ragdollSystem.update(0.016f)

        // Assert
        verify {
            mockBody.setPhysicsLocation(withArg {
                assertEquals(5f, it.x)
                assertEquals(5f, it.y)
                assertEquals(5f, it.z)
            })
        }
        verify { mockBody.isKinematic = true }
    }
}
