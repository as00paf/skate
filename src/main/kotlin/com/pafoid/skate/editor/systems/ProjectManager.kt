package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.settings.RecentProjectInfo
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.events.EngineAction
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class ProjectManager(
    private val engine: Engine,
    private val settingsManager: SettingsManager,
) {
    private val engineAssetCopier = EngineAssetCopier()
    private val eventSystem = engine.eventSystem
    private val logger = engine.logger
    private val prefabsGenerator = engine.prefabsGenerator
    private val sceneManager = engine.sceneManager
    private val serializer = engine.serializer

    var currentProject: Project? = null
        private set
    private val lifecycleEpoch = AtomicLong(0)

    fun init() {
        if (!loadLastProject()) {
            eventSystem.publish(WindowAction.Show("window.project_wizard"))
        } else {
            eventSystem.publish(WindowAction.ShowDefault)
        }
    }

    fun loadLastProject(): Boolean {
        val recent = settingsManager.recentProjects.firstOrNull() ?: return false
        val projectFile = File(recent.path)
        if (!projectFile.exists()) return false
        return openProject(projectFile)
    }

    fun createProject(name: String, folder: File): Result<Project> {
        logger.logEditor("Creating project: $name in ${folder.absolutePath}")
        return try {
            val projectDir = File(folder, name)
            if (!projectDir.exists()) {
                projectDir.mkdirs()
            }

            val projectFile = File(projectDir, "$name.skateproject")
            if (projectFile.exists()) {
                return Result.failure(IllegalStateException("Project '$name' already exists in this folder"))
            }

            File(projectDir, "Assets").mkdirs()
            val scenesDir = File(projectDir, "Scenes")
            scenesDir.mkdirs()
            File(projectDir, "Builds").mkdirs()

            val project = Project(name = name, projectPath = projectFile.absolutePath)

            // Copy assets
            val copyResult = engineAssetCopier.copyBundledAssets(projectDir)
            if (copyResult.isSuccess) {
                val count = copyResult.getOrNull() ?: 0
                logger.logEditor("Copied $count engine-bundled assets to project")
            } else {
                logger.logEditor("Failed to copy engine assets: ${copyResult.exceptionOrNull()?.message}")
            }

            currentProject = project

            // Create default scene with prefabs
            prefabsGenerator.createDefaultScene(scenesDir)

            settingsManager.addToRecentProjects(project.getProjectFile().absolutePath) // TODO: move to settings manager
            eventSystem.publish(EngineAction.ApplyMappings(project.gameplaySettings.inputMappings))

            saveProject()

            logger.logEditor("Project created successfully: ${project.name}")
            eventSystem.publish(ProjectEvent.Created(project))
            Result.success(project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openProject(projectFile: File): Boolean {
        return try {
            if (!projectFile.exists() || !FileType.PROJECT_FILE.extensions.contains(projectFile.extension)) {
                logger.logEditor(
                    "Project file does not exist or invalid extension: ${projectFile.absolutePath}",
                    LogLevel.ERROR
                )
                return false
            }

            if (hasProject()) closeProject()

            logger.logEditor("Opening project: ${projectFile.absolutePath}")
            val project = serializer.decode<Project>(projectFile.readText()) // TODO: should be on io
            currentProject = project

            eventSystem.publish(EngineAction.ApplyMappings(project.gameplaySettings.inputMappings))

            loadDefaultScene(project)

            settingsManager.addToRecentProjects(project.getProjectFile().absolutePath)
            eventSystem.publish(ProjectEvent.Opened(project))
            logger.logEditor("Project opened successfully: ${getProjectName()}")

            true
        } catch (e: Exception) {
            logger.logEditor("Error opening project: ${e.message}", LogLevel.ERROR)
            false
        }
    }

    fun closeProject() {
        val project = currentProject ?: run {
            logger.logEditor("No project to close")
            return
        }
        lifecycleEpoch.incrementAndGet()
        val path = project.getProjectFile().absolutePath
        val projectName = project.name
        logger.logEditor("Closing project: $projectName")

        try {
            val scenesDir = File(project.getProjectDirectory(), project.scenePaths[0])
            val openScenesCopy = sceneManager.openScenes.toList()
            openScenesCopy.forEach { scene ->
                if (scene.isDirty) {
                    val saved = sceneManager.saveScene(scene, scenesDir.path)
                    if (!saved) logger.logEditor(
                        "Warning: Failed to save dirty scene ${scene.name} before closing project",
                        LogLevel.WARN
                    )
                }
            }
        } catch (e: Exception) {
            logger.logEditor("Error while saving open scenes during close: ${e.message}", LogLevel.WARN)
        }

        sceneManager.closeAllScenes()
        engine.systemManager.resetSystemCaches()

        currentProject = null
        settingsManager.setLastClosedProjectPath(path)
        settingsManager.closeProject()

        eventSystem.publish(ProjectEvent.Closed(projectName))
    }

    private fun loadDefaultScene(project: Project) {
        val scenesDir = File(project.getProjectDirectory(), project.scenePaths[0])
        val defaultSceneFile = File(scenesDir, "${project.defaultScene}.scene")

        if (!defaultSceneFile.exists()) {
            logger.logEditor("No default scene found at ${defaultSceneFile.absolutePath}", LogLevel.ERROR)
            return
        }

        val scene = serializer.decode<Scene?>(defaultSceneFile.readText()) ?: run {
            logger.logEditor("Failed to load default scene from ${defaultSceneFile.absolutePath}")
            return
        }
        sceneManager.openScene(scene)

        logger.logEditor("Loaded default scene from ${defaultSceneFile.absolutePath}")
    }

    fun saveProject(): Boolean {
        val project = currentProject ?: run {
            logger.logEditor("No project to save")
            return false
        }

        logger.logEditor("Saving project: ${project.name}")
        val result = settingsManager.saveProject(project)
        File(project.projectPath).writeText(serializer.encode(project))
        if (result) {
            eventSystem.publish(ProjectEvent.Saved(project))
        }
        return result
    }

    fun getProjectName(): String = currentProject?.name ?: "No Project"

    fun getRecentProjects(): List<RecentProjectInfo> {
        return settingsManager.recentProjects
    }

    fun getProjectDirectory(): File? {
        return currentProject?.getProjectDirectory()
    }

    fun hasProject(): Boolean = currentProject != null
}
