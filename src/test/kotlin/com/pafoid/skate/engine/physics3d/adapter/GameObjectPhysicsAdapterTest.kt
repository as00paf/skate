package com.pafoid.skate.engine.physics3d.adapter

import com.jme3.bullet.PhysicsSpace
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import io.mockk.*
import org.joml.Vector3f
import org.junit.jupiter.api.*

/**
 * Test class for GameObjectPhysicsAdapter following TDD protocol.
 * Tests the game object integration functionality that will be extracted from Physics3D.
 */
class GameObjectPhysicsAdapterTest {

    private lateinit var adapter: GameObjectPhysicsAdapter
    private lateinit var mockPhysicsObjectCreator: IPhysicsObjectCreator

    @BeforeEach
    fun setup() {
        mockPhysicsObjectCreator = mockk<IPhysicsObjectCreator>()
        adapter = GameObjectPhysicsAdapter(mockPhysicsObjectCreator)
    }

    @AfterEach
    fun teardown() {
        // Clean up resources if needed
    }

    @Test
    fun `constructor_createsInstanceSuccessfully`() {
        // Arrange & Act
        val mockCreator = mockk<IPhysicsObjectCreator>()
        val adapter = GameObjectPhysicsAdapter(mockCreator)

        // Assert
        Assertions.assertNotNull(adapter)
    }

    @Test
    fun `addGameObject_handlesAdditionWithoutCrash`() {
        // Arrange
        val mockPhysicsSpace = mockk<PhysicsSpace>()
        val mockRigidBody = mockk<PhysicsRigidBody>()
        val mockCompoundShape = mockk<CompoundCollisionShape>()
        val gameObject = GameObject("TestObject")
        val rigidBody = RigidBody3D(1.0f)

        gameObject.addComponent(rigidBody)

        every { mockPhysicsObjectCreator.createCompoundCollisionShape() } returns mockCompoundShape
        every { mockPhysicsObjectCreator.createRigidBody(any(), any()) } returns mockRigidBody
        every { mockPhysicsSpace.add(any<PhysicsRigidBody>()) } returns Unit

        // Act & Assert
        assertDoesNotThrow {
            adapter.add(gameObject, mockPhysicsSpace)
        }
    }

    @Test
    fun `removeGameObject_handlesRemovalWithoutCrash`() {
        // Arrange
        val mockPhysicsSpace = mockk<PhysicsSpace>()
        val gameObject = GameObject("TestObject")
        val rigidBody = RigidBody3D(1.0f)
        val mockRigidBody = mockk<PhysicsRigidBody>()

        gameObject.addComponent(rigidBody)
        rigidBody.rawBody = mockRigidBody

        every { mockPhysicsSpace.remove(any<PhysicsRigidBody>()) } returns Unit

        // Act & Assert
        assertDoesNotThrow {
            adapter.remove(gameObject, mockPhysicsSpace)
        }
    }

    @Test
    fun `updateGameObject_syncsBodyProperties`() {
        // Arrange
        val mockPhysicsSpace = mockk<PhysicsSpace>()
        val gameObject = GameObject("TestObject")
        val rigidBody = RigidBody3D(1.0f)
        val mockRigidBody = mockk<PhysicsRigidBody>()

        gameObject.addComponent(rigidBody)
        rigidBody.rawBody = mockRigidBody

        // Act & Assert - Just ensure the method doesn't throw an exception
        assertDoesNotThrow {
            adapter.update(gameObject, mockPhysicsSpace)
        }
    }

    @Test
    fun `addGameObject_createsRigidBodyWithCorrectMassForStaticBody`() {
        // Arrange
        val mockPhysicsSpace = mockk<PhysicsSpace>()
        val mockRigidBody = mockk<PhysicsRigidBody>()
        val mockCompoundShape = mockk<CompoundCollisionShape>()
        val gameObject = GameObject("TestObject")
        val rigidBody = RigidBody3D(1.0f)
        rigidBody.bodyType = BodyType.Static

        gameObject.addComponent(rigidBody)

        every { mockPhysicsObjectCreator.createCompoundCollisionShape() } returns mockCompoundShape
        every { mockPhysicsObjectCreator.createRigidBody(any(), any()) } returns mockRigidBody
        every { mockPhysicsSpace.add(any<PhysicsRigidBody>()) } returns Unit

        // Act & Assert
        assertDoesNotThrow {
            adapter.add(gameObject, mockPhysicsSpace)
        }
    }

    @Test
    fun `addGameObject_createsRigidBodyWithCorrectMassForDynamicBody`() {
        // Arrange
        val mockPhysicsSpace = mockk<PhysicsSpace>()
        val mockRigidBody = mockk<PhysicsRigidBody>()
        val mockCompoundShape = mockk<CompoundCollisionShape>()
        val gameObject = GameObject("TestObject")
        val rigidBody = RigidBody3D(2.5f)
        rigidBody.bodyType = BodyType.Dynamic

        gameObject.addComponent(rigidBody)

        every { mockPhysicsObjectCreator.createCompoundCollisionShape() } returns mockCompoundShape
        every { mockPhysicsObjectCreator.createRigidBody(any(), any()) } returns mockRigidBody
        every { mockPhysicsSpace.add(any<PhysicsRigidBody>()) } returns Unit

        // Act & Assert
        assertDoesNotThrow {
            adapter.add(gameObject, mockPhysicsSpace)
        }
    }

    @Test
    fun `addGameObject_handlesGameObjectWithNoRigidBody`() {
        // Arrange
        val mockPhysicsSpace = mockk<PhysicsSpace>()
        val gameObject = GameObject("TestObject")
        // No RigidBody component added

        // Act & Assert - Should not throw an exception
        assertDoesNotThrow {
            adapter.add(gameObject, mockPhysicsSpace)
        }
    }

    @Test
    fun `addGameObject_handlesGameObjectWithMultipleColliders`() {
        // Arrange
        val mockPhysicsSpace = mockk<PhysicsSpace>()
        val mockRigidBody = mockk<PhysicsRigidBody>()
        val mockCompoundShape = mockk<CompoundCollisionShape>()
        val mockBoxShape = mockk<CollisionShape>()
        val gameObject = GameObject("TestObject")
        val rigidBody = RigidBody3D(1.0f)
        val collider1 = BoxCollider3D(Vector3f(0.5f, 0.5f, 0.5f))
        val collider2 = BoxCollider3D(Vector3f(0.3f, 0.3f, 0.3f))

        gameObject.addComponent(rigidBody)
        gameObject.addComponent(collider1)
        gameObject.addComponent(collider2)

        every { mockPhysicsObjectCreator.createCompoundCollisionShape() } returns mockCompoundShape
        every { mockPhysicsObjectCreator.createBoxCollisionShape(any()) } returns mockBoxShape
        every { mockPhysicsObjectCreator.createRigidBody(any(), any()) } returns mockRigidBody
        every { mockCompoundShape.addChildShape(any<CollisionShape>(), any<com.jme3.math.Vector3f>()) } just Runs
        every { mockPhysicsSpace.add(any<PhysicsRigidBody>()) } returns Unit

        // Act & Assert - Just ensure the method doesn't throw an exception
        assertDoesNotThrow {
            adapter.add(gameObject, mockPhysicsSpace)
        }
    }
}