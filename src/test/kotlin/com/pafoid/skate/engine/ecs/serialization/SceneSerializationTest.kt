package com.pafoid.skate.engine.ecs.serialization

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.NonPickable
import com.pafoid.skate.engine.ecs.components.Transform
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Unit tests for SceneSerializer and component serialization.
 */
class SceneSerializationTest {

    private lateinit var serializer: Serializer

    @BeforeEach
    fun setUp() {
        serializer = Serializer()
        // Reset ID counters for consistent tests
        GameObject.init(0)
    }

    @Test
    fun testTransformSerialization_roundTrip() {
        // Arrange
        val transform = Transform()
        transform.translation.set(1f, 2f, 3f)
        transform.rotation.set(0f, 0f, 0f)
        transform.scale.set(2f, 2f, 2f)

        // Act
        val json = serializer.encode(transform)
        val deserialized = serializer.decode<Transform>(json)

        // Assert
        assertEquals(transform.translation.x, deserialized.translation.x, 0.001f)
        assertEquals(transform.translation.y, deserialized.translation.y, 0.001f)
        assertEquals(transform.translation.z, deserialized.translation.z, 0.001f)
        assertEquals(transform.scale.x, deserialized.scale.x, 0.001f)
        assertEquals(transform.scale.y, deserialized.scale.y, 0.001f)
        assertEquals(transform.scale.z, deserialized.scale.z, 0.001f)
    }

    @Test
    fun testGameObjectSerialization_withTransform() {
        // Arrange
        val gameObject = GameObject("TestObject")
        val transform = Transform()
        transform.translation.set(5f, 10f, 0f)
        gameObject.addComponent(transform)

        // Act
        val json = serializer.encode(gameObject)
        val deserialized = serializer.decode<GameObject>(json)

        // Assert
        assertEquals("TestObject", deserialized.name)
        assertNotNull(deserialized.getComponent<Transform>())
        assertEquals(5f, deserialized.getComponent<Transform>()!!.translation.x, 0.001f)
        assertEquals(10f, deserialized.getComponent<Transform>()!!.translation.y, 0.001f)
    }

    @Test
    fun testGameObjectSerialization_childrenNotSerialized() {
        // Arrange
        val parent = GameObject("Parent")
        val child = GameObject("Child")

        parent.addChild(child)

        // Note: Parent and children references are marked as @Transient
        // They are reconstructed at runtime, not serialized

        // Act - serialize parent
        val json = serializer.encode(parent)
        val deserialized = serializer.decode<GameObject>(json)

        // Assert - parent is serialized, but children are not
        assertEquals("Parent", deserialized.name)
        assertEquals(0, deserialized.children.size) // Children not serialized
    }

    @Test
    fun testGameObjectCopy_createsUniqueInstance() {
        // Arrange
        val original = GameObject("Original")
        val transform = Transform()
        transform.translation.set(1f, 2f, 3f)
        original.addComponent(transform)
        val originalUid = original.getUid()

        // Act
        val copy = original.copy(serializer)

        // Assert
        assertNotEquals(originalUid, copy.getUid())
        assertEquals("Original", copy.name)
        assertNotNull(copy.getComponent<Transform>())
        assertEquals(1f, copy.getComponent<Transform>()!!.translation.x, 0.001f)
    }

    @Test
    fun testGameObjectSerialization_multipleComponents() {
        // Arrange
        val gameObject = GameObject("MultiComponentObject")
        val transform = Transform()
        transform.translation.set(1f, 0f, 0f)
        gameObject.addComponent(transform)

        // Add another transform (should replace the first)
        val newTransform = Transform()
        newTransform.translation.set(2f, 0f, 0f)
        gameObject.addComponent(newTransform)

        // Act
        val json = serializer.encode(gameObject)
        val deserialized = serializer.decode<GameObject>(json)

        // Assert
        assertEquals(1, deserialized.components.size)
        assertEquals(2f, deserialized.getComponent<Transform>()!!.translation.x, 0.001f)
    }

    @Test
    fun testSceneDataWrapperSerialization_basic() {
        // Arrange - create objects without parent-child relationships to avoid circular refs
        val root1 = GameObject("Root1")
        val root2 = GameObject("Root2")

        val wrapper = SceneDataWrapper(
            gameObjects = listOf(root1, root2),
            rootObjectIds = listOf(root1.getUid(), root2.getUid()),
            maxGameObjectId = 100,
            maxComponentId = 50
        )

        // Act
        val json = serializer.encode(wrapper)
        val deserialized = serializer.decode<SceneDataWrapper>(json)

        // Assert
        assertEquals(2, deserialized.gameObjects.size)
        assertEquals(2, deserialized.rootObjectIds.size)
        assertEquals(100, deserialized.maxGameObjectId)
        assertEquals(50, deserialized.maxComponentId)
    }

    @Test
    fun testSerialization_nonPickableComponent() {
        // Arrange
        val gameObject = GameObject("NonPickableObject")
        gameObject.addComponent(NonPickable())

        // Act
        val json = serializer.encode(gameObject)
        val deserialized = serializer.decode<GameObject>(json)

        // Assert
        assertNotNull(deserialized.getComponent<NonPickable>())
    }

    @Test
    fun testSerialization_fileSaveLoad() {
        // Arrange
        val testFile = File("test_scene.json")
        val gameObject = GameObject("FileTestObject")
        val transform = Transform()
        transform.translation.set(100f, 200f, 300f)
        gameObject.addComponent(transform)

        try {
            // Act - Save
            val json = serializer.encode(gameObject)
            testFile.writeText(json)

            // Act - Load
            val loadedJson = testFile.readText()
            val deserialized = serializer.decode<GameObject>(loadedJson)

            // Assert
            assertEquals("FileTestObject", deserialized.name)
            assertEquals(100f, deserialized.getComponent<Transform>()!!.translation.x, 0.001f)
            assertEquals(200f, deserialized.getComponent<Transform>()!!.translation.y, 0.001f)
            assertEquals(300f, deserialized.getComponent<Transform>()!!.translation.z, 0.001f)
        } finally {
            // Cleanup
            if (testFile.exists()) {
                testFile.delete()
            }
        }
    }
}
