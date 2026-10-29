package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.physics3d.IRigidBodyHandle
import org.joml.Quaternionf
import org.joml.Vector3f
import com.pafoid.skate.engine.addComponent
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

class RagdollSystemTest {

    private lateinit var scene: Scene
    private lateinit var ragdollSystem: RagdollSystem

    @BeforeEach
    fun setup() {
        scene = mockk(relaxed = true)
        every { scene.isRunning } returns true
        ragdollSystem = RagdollSystem()
        ragdollSystem.init(scene)
    }

    @Test
    fun update_RagdollStateIsRagdoll_UpdatesBoneTransformsFromPhysics() {
        // Arrange
        val ragdollComponent = RagdollComponent().apply { state = RagdollState.RAGDOLL }
        val transform = Transform()

        val rootBone = Bone(0, "Root", Matrix4f())
        val skeleton = Skeleton(rootBone, 1)
        val pose = SkeletonPose(skeleton)
        val skeletonComponent = SkeletonComponent(pose)

        val mockBody = mockk<IRigidBodyHandle>(relaxed = true)

        every { mockBody.getLocation(any()) } answers {
            val out = arg<Vector3f>(0)
            out.set(1f, 2f, 3f)
            out
        }
        every { mockBody.getRotation(any()) } answers {
            val out = arg<Quaternionf>(0)
            out.set(0f, 0f, 0f, 1f)
            out
        }

        ragdollComponent.boneBodies["Root"] = mockBody

        val gameObject = GameObject("TestGO")
        gameObject.addComponent(ragdollComponent)
        gameObject.addComponent(skeletonComponent)
        gameObject.addComponent(transform)

        every { scene.children } returns mutableListOf(gameObject)

        // Act
        ragdollSystem.update(0.016f)

        // Assert
        val expectedMatrix = Matrix4f().translationRotateScale(
            1f, 2f, 3f,
            0f, 0f, 0f, 1f,
            1f, 1f, 1f
        )
        // Matrix4f equals might be strict, but here we expect exact match as we use simple values
        assertEquals(expectedMatrix, pose.globalTransforms[0])
    }

    @Test
    fun update_RagdollStateIsAnimated_SynchronizesPhysicsBodiesWithAnimation() {
        // Arrange
        val ragdollComponent = RagdollComponent().apply { state = RagdollState.ANIMATED }
        val transform = Transform()

        val rootBone = Bone(0, "Root", Matrix4f())
        val skeleton = Skeleton(rootBone, 1)
        val pose = SkeletonPose(skeleton)
        val skeletonComponent = SkeletonComponent(pose)

        // IMPORTANT: Must set globalTransforms AFTER creating SkeletonComponent 
        // because SkeletonComponent.init overwrites them with bind pose!
        pose.globalTransforms[0].translationRotateScale(
            5f, 5f, 5f,
            0f, 0f, 0f, 1f,
            1f, 1f, 1f
        )

        val mockBody = mockk<IRigidBodyHandle>(relaxed = true)
        ragdollComponent.boneBodies["Root"] = mockBody

        val gameObject = GameObject("TestGO")
        gameObject.addComponent(ragdollComponent)
        gameObject.addComponent(skeletonComponent)
        gameObject.addComponent(transform)

        every { scene.children } returns mutableListOf(gameObject)

        // Act
        ragdollSystem.update(0.016f)

        // Assert
        val capturedLoc = io.mockk.slot<Vector3f>()
        verify {
            mockBody.setLocation(capture(capturedLoc))
        }
        assertEquals(5f, capturedLoc.captured.x, 0.001f)
        assertEquals(5f, capturedLoc.captured.y, 0.001f)
        assertEquals(5f, capturedLoc.captured.z, 0.001f)
        verify { mockBody.isKinematic = true }
    }
}
