package com.pafoid.skate.engine.physics3d.space

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.PhysicsRayTestResult
import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.physics3d.components.Collider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import com.pafoid.skate.engine.utils.JomlVector3f
import io.mockk.*
import org.joml.Vector3f
import org.junit.jupiter.api.*

/**
 * Test class for PhysicsSpaceManager following TDD protocol.
 * Tests the physics space management functionality that will be extracted from Physics3D.
 */
class PhysicsSpaceManagerTest {

    private lateinit var spaceManager: PhysicsSpaceManager
    private lateinit var mockPhysicsSpace: PhysicsSpace

    @BeforeEach
    fun setup() {
        mockPhysicsSpace = mockk<PhysicsSpace>()
        spaceManager = PhysicsSpaceManager(mockPhysicsSpace)
    }

    @AfterEach
    fun teardown() {
        // Clean up resources if needed
    }

    @Test
    fun `constructor_createsInstanceSuccessfully`() {
        // Arrange & Act
        val manager = PhysicsSpaceManager(mockPhysicsSpace)

        // Assert
        Assertions.assertNotNull(manager)
    }

    @Test
    fun `getPhysicsSpace_returnsCorrectSpace`() {
        // Arrange
        val expectedSpace = mockPhysicsSpace

        // Act
        val actualSpace = spaceManager.getPhysicsSpace()

        // Assert
        Assertions.assertEquals(expectedSpace, actualSpace)
    }

    @Test
    fun `setGravity_updatesPhysicsSpaceGravity`() {
        // Arrange
        val gravity = JomlVector3f(0f, -9.81f, 0f)

        every { mockPhysicsSpace.setGravity(any()) } returns Unit

        // Act
        spaceManager.setGravity(gravity)

        // Assert
        verify(exactly = 1) { mockPhysicsSpace.setGravity(any()) }
    }

    @Test
    fun `getGravity_returnsCurrentGravity`() {
        // Arrange
        val expectedGravity = com.jme3.math.Vector3f(0f, -9.81f, 0f)
        every { mockPhysicsSpace.getGravity(isNull()) } returns expectedGravity

        // Act
        val actualGravity = spaceManager.getGravity()

        // Assert
        Assertions.assertEquals(expectedGravity.x, actualGravity.x, 0.001f)
        Assertions.assertEquals(expectedGravity.y, actualGravity.y, 0.001f)
        Assertions.assertEquals(expectedGravity.z, actualGravity.z, 0.001f)
    }

    @Test
    fun `rayTest_performsRaycastAndReturnsResults`() {
        // Arrange
        val from = JomlVector3f(0f, 0f, 0f)
        val to = JomlVector3f(10f, 0f, 0f)
        val mockResults = listOf<PhysicsRayTestResult>(mockk())

        every { mockPhysicsSpace.rayTest(any(), any()) } returns mockResults

        // Act
        val results = spaceManager.rayTest(from, to)

        // Assert
        Assertions.assertEquals(mockResults, results)
        verify(exactly = 1) { mockPhysicsSpace.rayTest(any(), any()) }
    }

    @Test
    fun `update_stepsPhysicsSimulation`() {
        // Arrange
        val deltaTime = 1.0f / 60.0f

        every { mockPhysicsSpace.update(any(), any()) } returns Unit

        // Act
        spaceManager.update(deltaTime)

        // Assert
        verify(exactly = 1) { mockPhysicsSpace.update(any(), any()) }
    }

    @Test
    fun `addGameObject_addsRigidBodyToPhysicsSpace`() {
        // Arrange
        val gameObject = GameObject("TestObject")
        val rigidBody = RigidBody3D(1.0f)
        val mockRigidBody = mockk<PhysicsRigidBody>()
        rigidBody.rawBody = mockRigidBody
        
        gameObject.addComponent(rigidBody)
        
        every { mockPhysicsSpace.add(any<PhysicsRigidBody>()) } returns Unit

        // Act
        spaceManager.add(gameObject)

        // Assert
        verify(exactly = 1) { mockPhysicsSpace.add(mockRigidBody) }
    }

    @Test
    fun `removeGameObject_removesRigidBodyFromPhysicsSpace`() {
        // Arrange
        val gameObject = GameObject("TestObject")
        val rigidBody = RigidBody3D(1.0f)
        val mockRigidBody = mockk<PhysicsRigidBody>()
        rigidBody.rawBody = mockRigidBody
        
        gameObject.addComponent(rigidBody)
        
        every { mockPhysicsSpace.remove(any<PhysicsRigidBody>()) } returns Unit

        // Act
        spaceManager.remove(gameObject)

        // Assert
        verify(exactly = 1) { mockPhysicsSpace.remove(mockRigidBody) }
    }

    @Test
    fun `destroy_destroysPhysicsSpace`() {
        // Arrange
        every { mockPhysicsSpace.destroy() } returns Unit

        // Act
        spaceManager.destroy()

        // Assert
        verify(exactly = 1) { mockPhysicsSpace.destroy() }
    }
}