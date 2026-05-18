package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.project.GameplaySettings
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.project.ProjectMetadata
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.assets.database.AssetRegistryData
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.SystemManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class ProjectManagerLifecycleTest {

    @Test
    fun `closeProject closes all scenes and resets system caches`() {
        val settingsManager = mockk<SettingsManager>(relaxed = true)
        val logger = mockk<LoggerService>(relaxed = true)
        val assetDatabase = mockk<AssetDatabase>(relaxed = true)
        val sceneManager = mockk<SceneManager>(relaxed = true)
        val prefabsGenerator = mockk<PrefabsGenerator>(relaxed = true)
        val sceneSerializer = mockk<SceneSerializer>(relaxed = true)
        val eventSystem = mockk<EventSystem>(relaxed = true)
        val systemManager = mockk<SystemManager>(relaxed = true)

        every { assetDatabase.exportRegistryData() } returns AssetRegistryData(projectPath = "C:/tmp/OldProject")

        val manager = ProjectManager(
            settingsManager = settingsManager,
            logger = logger,
            assetDatabase = assetDatabase,
            engineAssetCopier = EngineAssetCopier(),
            sceneManager = sceneManager,
            prefabsGenerator = prefabsGenerator,
            sceneSerializer = sceneSerializer,
            eventSystem = eventSystem,
            systemManager = systemManager
        )
        setCurrentProject(manager, project("OldProject", "C:/tmp/OldProject/OldProject.skateproject"))

        manager.closeProject()

        assertFalse(manager.hasProject())
        verify(exactly = 1) { sceneManager.closeAllScenes() }
        verify(exactly = 1) { systemManager.resetSystemCaches() }
        verify(exactly = 1) { settingsManager.closeProject() }
    }

    @Test
    fun `openProject closes existing project before opening new one`() {
        val settingsManager = mockk<SettingsManager>(relaxed = true)
        val logger = mockk<LoggerService>(relaxed = true)
        val assetDatabase = mockk<AssetDatabase>(relaxed = true)
        val sceneManager = mockk<SceneManager>(relaxed = true)
        val prefabsGenerator = mockk<PrefabsGenerator>(relaxed = true)
        val sceneSerializer = mockk<SceneSerializer>(relaxed = true)
        val eventSystem = mockk<EventSystem>(relaxed = true)
        val systemManager = mockk<SystemManager>(relaxed = true)

        val tempDir = Files.createTempDirectory("project-manager-open-lifecycle").toFile()
        val projectFile = File(tempDir, "NewProject.skateproject")
        projectFile.writeText("{}")
        val loadedProject = project("NewProject", projectFile.absolutePath)

        every { assetDatabase.exportRegistryData() } returns AssetRegistryData(projectPath = "C:/tmp/OldProject")
        every { settingsManager.loadProject(projectFile) } returns loadedProject
        every { assetDatabase.initialize(any()) } returns Result.success(Unit)
        every { sceneManager.currentScene } returns null

        val manager = ProjectManager(
            settingsManager = settingsManager,
            logger = logger,
            assetDatabase = assetDatabase,
            engineAssetCopier = EngineAssetCopier(),
            sceneManager = sceneManager,
            prefabsGenerator = prefabsGenerator,
            sceneSerializer = sceneSerializer,
            eventSystem = eventSystem,
            systemManager = systemManager
        )
        setCurrentProject(manager, project("OldProject", "C:/tmp/OldProject/OldProject.skateproject"))

        val opened = manager.openProject(projectFile)

        assertTrue(opened)
        verify(exactly = 1) { sceneManager.closeAllScenes() }
        verify(exactly = 1) { settingsManager.closeProject() }
        tempDir.deleteRecursively()
    }

    @Test
    fun `openProject_InvalidExtension_ClosesExistingProjectBeforeFailing`() {
        val settingsManager = mockk<SettingsManager>(relaxed = true)
        val logger = mockk<LoggerService>(relaxed = true)
        val assetDatabase = mockk<AssetDatabase>(relaxed = true)
        val sceneManager = mockk<SceneManager>(relaxed = true)
        val prefabsGenerator = mockk<PrefabsGenerator>(relaxed = true)
        val sceneSerializer = mockk<SceneSerializer>(relaxed = true)
        val eventSystem = mockk<EventSystem>(relaxed = true)
        val systemManager = mockk<SystemManager>(relaxed = true)

        val tempDir = Files.createTempDirectory("project-manager-open-invalid-extension").toFile()
        val invalidProjectFile = File(tempDir, "NotAProject.txt").apply { writeText("invalid") }

        every { assetDatabase.exportRegistryData() } returns AssetRegistryData(projectPath = "C:/tmp/OldProject")

        val manager = ProjectManager(
            settingsManager = settingsManager,
            logger = logger,
            assetDatabase = assetDatabase,
            engineAssetCopier = EngineAssetCopier(),
            sceneManager = sceneManager,
            prefabsGenerator = prefabsGenerator,
            sceneSerializer = sceneSerializer,
            eventSystem = eventSystem,
            systemManager = systemManager
        )
        setCurrentProject(manager, project("OldProject", "C:/tmp/OldProject/OldProject.skateproject"))

        val opened = manager.openProject(invalidProjectFile)

        assertFalse(opened)
        assertFalse(manager.hasProject())
        verify(exactly = 1) { sceneManager.closeAllScenes() }
        verify(exactly = 1) { systemManager.resetSystemCaches() }
        verify(exactly = 1) { settingsManager.closeProject() }
        tempDir.deleteRecursively()
    }

    @Test
    fun `updateGameplaySettings persists and updates current project on success`() {
        val settingsManager = mockk<SettingsManager>(relaxed = true)
        val logger = mockk<LoggerService>(relaxed = true)
        val assetDatabase = mockk<AssetDatabase>(relaxed = true)
        val sceneManager = mockk<SceneManager>(relaxed = true)
        val prefabsGenerator = mockk<PrefabsGenerator>(relaxed = true)
        val sceneSerializer = mockk<SceneSerializer>(relaxed = true)
        val eventSystem = mockk<EventSystem>(relaxed = true)
        val systemManager = mockk<SystemManager>(relaxed = true)

        every { settingsManager.saveProject(any()) } returns true

        val manager = ProjectManager(
            settingsManager = settingsManager,
            logger = logger,
            assetDatabase = assetDatabase,
            engineAssetCopier = EngineAssetCopier(),
            sceneManager = sceneManager,
            prefabsGenerator = prefabsGenerator,
            sceneSerializer = sceneSerializer,
            eventSystem = eventSystem,
            systemManager = systemManager
        )
        setCurrentProject(manager, project("Skate", "C:/tmp/Skate/Skate.skateproject"))

        val updated = manager.updateGameplaySettings(120, -12.5f, 0.75f)

        assertTrue(updated)
        assertEquals(120, manager.currentProject?.gameplaySettings?.physicsFPS)
        assertEquals(-12.5f, manager.currentProject?.gameplaySettings?.gravity)
        assertEquals(0.75f, manager.currentProject?.gameplaySettings?.timeScale)
        verify(exactly = 1) {
            settingsManager.saveProject(
                match {
                    it.gameplaySettings.physicsFPS == 120 &&
                        it.gameplaySettings.gravity == -12.5f &&
                        it.gameplaySettings.timeScale == 0.75f
                }
            )
        }
        verify(exactly = 1) {
            eventSystem.publish(
                match { event ->
                    event is ProjectEvent.Saved &&
                        event.project.gameplaySettings.physicsFPS == 120 &&
                        event.project.gameplaySettings.gravity == -12.5f &&
                        event.project.gameplaySettings.timeScale == 0.75f
                }
            )
        }
    }

    @Test
    fun `updateGameplaySettings does not mutate project when save fails`() {
        val settingsManager = mockk<SettingsManager>(relaxed = true)
        val logger = mockk<LoggerService>(relaxed = true)
        val assetDatabase = mockk<AssetDatabase>(relaxed = true)
        val sceneManager = mockk<SceneManager>(relaxed = true)
        val prefabsGenerator = mockk<PrefabsGenerator>(relaxed = true)
        val sceneSerializer = mockk<SceneSerializer>(relaxed = true)
        val eventSystem = mockk<EventSystem>(relaxed = true)
        val systemManager = mockk<SystemManager>(relaxed = true)

        every { settingsManager.saveProject(any()) } returns false

        val manager = ProjectManager(
            settingsManager = settingsManager,
            logger = logger,
            assetDatabase = assetDatabase,
            engineAssetCopier = EngineAssetCopier(),
            sceneManager = sceneManager,
            prefabsGenerator = prefabsGenerator,
            sceneSerializer = sceneSerializer,
            eventSystem = eventSystem,
            systemManager = systemManager
        )
        val original = project("Skate", "C:/tmp/Skate/Skate.skateproject")
        setCurrentProject(manager, original)

        val updated = manager.updateGameplaySettings(120, -12.5f, 0.75f)

        assertFalse(updated)
        assertEquals(original.gameplaySettings, manager.currentProject?.gameplaySettings)
        verify(exactly = 0) { eventSystem.publish(match { it is ProjectEvent.Saved }) }
    }

    private fun project(name: String, path: String): Project {
        return Project(
            metadata = ProjectMetadata(
                name = name,
                engineVersion = "v-test",
                projectPath = path
            ),
            gameplaySettings = GameplaySettings()
        )
    }

    private fun setCurrentProject(manager: ProjectManager, project: Project) {
        val field = ProjectManager::class.java.getDeclaredField("currentProject")
        field.isAccessible = true
        field.set(manager, project)
    }
}
