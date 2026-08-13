package com.pafoid.skate.engine.ecs.serialization

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.NonPickable
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Unit tests for GameObject serialization (used by SceneSerializer and ClipboardService).
 *
 * Note: Scene persistence is handled by SceneSerializer which uses SceneSaveData.
 * These tests focus on GameObject-level serialization for clipboard and prefab operations.
 */
class GameObjectSerializationTest {

    private lateinit var serializer: Serializer

    @BeforeEach
    fun setUp() {
        serializer = Serializer()
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
    fun testGameObjectCopy_createsUniqueInstance() {
        // Arrange
        val original = GameObject("Original")
        val transform = Transform()
        transform.translation.set(1f, 2f, 3f)
        original.addComponent(transform)
        val originalUid = original.uId

        // Act
        val copy = original.copy(serializer)

        // Assert
        assertNotEquals(originalUid, copy.uId)
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
        val testFile = File("test_gameobject.json")
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

    @Test
    fun testGameObjectCopy_preservesComponentState() {
        // Arrange
        val original = GameObject("Original")
        val transform = Transform()
        transform.translation.set(10f, 20f, 30f)
        transform.scale.set(5f, 5f, 5f)
        original.addComponent(transform)

        // Act
        val copy = original.copy(serializer)
        val copyTransform = copy.getComponent<Transform>()

        // Assert
        assertNotNull(copyTransform)
        assertEquals(10f, copyTransform!!.translation.x, 0.001f)
        assertEquals(20f, copyTransform.translation.y, 0.001f)
        assertEquals(30f, copyTransform.translation.z, 0.001f)
        assertEquals(5f, copyTransform.scale.x, 0.001f)
        assertEquals(5f, copyTransform.scale.y, 0.001f)
        assertEquals(5f, copyTransform.scale.z, 0.001f)
    }
}
