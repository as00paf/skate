package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.utils.AssetsPacker
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-58: the editor camera is remembered per scene, survives a restart, and never ships in an exported game. */
class EditorCameraMemoryTest {

    @TempDir
    lateinit var projectDir: File

    private val camera = CameraComponent()
    private val transform = Transform(Vector3f(0f, 5f, 20f))
    private var dir: File? = null

    private fun memory() = EditorCameraMemory({ camera }, { transform }, { dir })

    private fun moveCamera(x: Float, yaw: Float, ortho: Boolean) {
        transform.translation.set(x, 2f, 3f)
        transform.rotation.set(-10f, yaw, 0f)
        camera.isOrthographic = ortho
    }

    @Test
    fun SwitchingScenes_RestoresEachScenesCamera() {
        dir = projectDir
        val memory = memory()
        memory.switchTo("Level1")
        moveCamera(10f, 45f, ortho = false)
        memory.switchTo("Level2")
        moveCamera(-7f, 90f, ortho = true)

        memory.switchTo("Level1")
        assertEquals(Vector3f(10f, 2f, 3f), transform.translation)
        assertEquals(Vector3f(-10f, 45f, 0f), transform.rotation)
        assertFalse(camera.isOrthographic)

        memory.switchTo("Level2")
        assertEquals(Vector3f(-7f, 2f, 3f), transform.translation)
        assertEquals(90f, transform.rotation.y)
        assertTrue(camera.isOrthographic)
    }

    @Test
    fun SameScene_AgainKeepsTheLiveCamera() {
        dir = projectDir
        val memory = memory()
        memory.switchTo("Level1")
        moveCamera(4f, 30f, ortho = false)
        // Play's reset publishes Changed for a copy of the same scene.
        memory.switchTo("Level1")
        assertEquals(4f, transform.translation.x)
    }

    @Test
    fun EditorRestart_RestoresTheCamera_SavedOnShutdownOrClose() {
        dir = projectDir
        val first = memory()
        first.switchTo("Level1")
        moveCamera(12f, 60f, ortho = true)
        first.flush()
        first.switchTo("Level2")
        moveCamera(3f, 15f, ortho = false)
        first.onClosing("Level2")

        moveCamera(0f, 0f, ortho = false)
        val restarted = memory()
        restarted.switchTo("Level1")
        assertEquals(Vector3f(12f, 2f, 3f), transform.translation)
        assertEquals(60f, transform.rotation.y)
        assertTrue(camera.isOrthographic)
        restarted.switchTo("Level2")
        assertEquals(3f, transform.translation.x)
    }

    @Test
    fun WithoutProject_KeepsTheCamera_AndWritesNothing() {
        val memory = memory()
        moveCamera(5f, 0f, ortho = false)
        memory.switchTo("Level1")
        memory.flush()
        assertEquals(5f, transform.translation.x)
        assertFalse(File(projectDir, EditorCameraMemory.DIR).exists())
    }

    @Test
    fun AnotherProject_DoesNotGetThePreviousProjectsCameras() {
        dir = projectDir
        val memory = memory()
        memory.switchTo("Level1")
        moveCamera(9f, 0f, ortho = false)
        memory.onClosing("Level1")
        memory.reset()

        dir = File(projectDir, "other").also { it.mkdirs() }
        memory.switchTo("Level1")
        assertNull(memory.stateOf("Level1"))
        dir = projectDir
        assertEquals(9f, memory.stateOf("Level1")!!.position[0])
    }

    @Test
    fun CameraFile_IsEditorOnly_AndNeverExported() {
        dir = projectDir
        memory().apply { switchTo("Level1"); flush() }
        val saved = File(File(projectDir, EditorCameraMemory.DIR), EditorCameraMemory.FILE_NAME)
        assertTrue(saved.isFile)

        val scene = File(projectDir, "Scenes/Level1.scene").apply { parentFile.mkdirs(); writeText("{}") }
        val project = Project(
            name = "P", projectPath = File(projectDir, "P.skateproject").path,
            defaultScene = scene.path, scenesPath = listOf(scene.path),
        )
        val packed = AssetsPacker(LoggerService()).selectFiles(project, onlyReferenced = false)
        assertTrue(packed.any { it.name == "Level1.scene" }, "the scene itself is packed: $packed")
        assertFalse(packed.any { it.canonicalFile == saved.canonicalFile }, "editor cameras must not ship: $packed")
    }
}
