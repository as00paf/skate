package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.engine.ecs.Scene
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CreateSceneCommandTest {

    private lateinit var sceneInitializer: LevelEditorSceneInitializer
    private lateinit var sceneSerializer: SceneSerializer

    @BeforeEach
    fun setup() {
        sceneSerializer = mockk(relaxed = true)
        sceneInitializer = mockk(relaxed = true)
        coEvery { sceneInitializer.loadResources(any()) } returns Unit
        coEvery { sceneInitializer.init(any()) } returns Unit
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `execute_createsSceneAndSetsFilePath`() {
        // Arrange
        val expectedPath = "C:\\workspace\\Scenes\\TestScene.scene"
        val command = CreateSceneCommand(
            name = "TestScene",
            sceneInitializer = sceneInitializer,
            sceneSerializer = sceneSerializer,
            filePath = expectedPath
        )

        // Act
        command.execute()

        // Assert
        val createdScene = command.createdScene
        assertNotNull(createdScene)
        assertEquals("TestScene", createdScene!!.name)
        assertEquals(expectedPath, createdScene.sceneData.levelPath)
    }

    @Test
    fun `execute_callsSceneSerializer_saveToFile()`() {
        // Arrange
        val expectedPath = "C:\\workspace\\Scenes\\TestScene.scene"
        val command = CreateSceneCommand(
            name = "TestScene",
            sceneInitializer = sceneInitializer,
            sceneSerializer = sceneSerializer,
            filePath = expectedPath
        )

        // Act
        command.execute()

        // Assert
        val savedSceneSlot = slot<Scene>()
        val savedPathSlot = slot<String>()
        verify { sceneSerializer.saveToFile(capture(savedSceneSlot), capture(savedPathSlot)) }
        assertEquals(expectedPath, savedPathSlot.captured)
        assertEquals("TestScene", savedSceneSlot.captured.name)
    }

    @Test
    fun `execute_callsSceneInit_withInitializer`() {
        // Arrange
        var loadResourcesCalled = false
        var initCalled = false

        coEvery { sceneInitializer.loadResources(any()) } answers {
            loadResourcesCalled = true
        }
        coEvery { sceneInitializer.init(any()) } answers {
            initCalled = true
        }

        val command = CreateSceneCommand(
            name = "TestScene",
            sceneInitializer = sceneInitializer,
            sceneSerializer = sceneSerializer,
            filePath = "test.scene"
        )

        // Act
        command.execute()

        // Assert
        assertTrue(loadResourcesCalled, "loadResources should be called during Scene.init()")
        assertTrue(initCalled, "init should be called during Scene.init()")
    }

    @Test
    fun `execute_setsCreatedSceneProperty()`() {
        // Arrange
        val command = CreateSceneCommand(
            name = "TestScene",
            sceneInitializer = sceneInitializer,
            sceneSerializer = sceneSerializer,
            filePath = "test.scene"
        )

        // Act - before execute
        assertNull(command.createdScene)

        // Act - execute
        command.execute()

        // Assert
        assertNotNull(command.createdScene)
        assertEquals("TestScene", command.createdScene!!.name)
    }

    @Test
    fun `undo_isNoOp`() {
        // Arrange
        val command = CreateSceneCommand(
            name = "TestScene",
            sceneInitializer = sceneInitializer,
            sceneSerializer = sceneSerializer,
            filePath = "test.scene"
        )

        // Act
        command.execute()
        command.undo()

        // Verify saveToFile was called once (from execute), undo doesn't add another call
        verify(exactly = 1) { sceneSerializer.saveToFile(any<Scene>(), any<String>()) }
    }

    @Test
    fun `getDisplayName_returnsCorrectString`() {
        // Arrange
        val command = CreateSceneCommand(
            name = "TestScene",
            sceneInitializer = mockk(),
            sceneSerializer = mockk(),
            filePath = "test.scene"
        )

        // Assert
        assertEquals("Create Scene", command.getDisplayName())
    }

    @Test
    fun `getTargetName_returnsSceneName`() {
        // Arrange
        val command = CreateSceneCommand(
            name = "MyCustomScene",
            sceneInitializer = mockk(),
            sceneSerializer = mockk(),
            filePath = "test.scene"
        )

        // Assert
        assertEquals("MyCustomScene", command.getTargetName())
    }
}
