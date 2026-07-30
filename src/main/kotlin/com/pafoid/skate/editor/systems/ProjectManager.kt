package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.events.EngineAction
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class ProjectManager(
    private val engine: Engine,
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

            val assetsDir = File(projectDir, "Assets").also { it.mkdirs() }
            val scenesDir = File(projectDir, "Scenes").also { it.mkdirs() }
            File(projectDir, "Builds").mkdirs()

            // Create default strings file
            val stringsFile = File(assetsDir, "strings.properties")
            stringsFile.writeText("project.name=$name")

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

            eventSystem.publish(EngineAction.ApplyMappings(project.gameplaySettings.inputMappings))

            saveProject()

            logger.logEditor("Project created successfully: ${project.name}")
            eventSystem.publish(ProjectEvent.Created(project))
            Result.success(project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openProjectFile(projectFile: File): Boolean { // TODO: should be on io
        if (!projectFile.exists() || !FileType.PROJECT_FILE.extensions.contains(projectFile.extension)) {
            logger.logEditor(
                "Project file does not exist or invalid extension: ${projectFile.absolutePath}",
                LogLevel.ERROR
            )
            return false
        }
        logger.logEditor("Opening project: ${projectFile.absolutePath}")
        val project = serializer.decode<Project>(projectFile.readText())

        return openProject(project)
    }

    fun openProject(project: Project): Boolean {
        return try {
            if (hasProject()) closeProject()

            currentProject = project

            eventSystem.publish(EngineAction.ApplyMappings(project.gameplaySettings.inputMappings))

            loadDefaultScene(project)

            eventSystem.publish(ProjectEvent.Opened(project))
            logger.logEditor("Project opened successfully: ${project.name}")

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
        logger.logEditor("Closing project: ${project.name}")

        try {
            val scenesDir = File(project.getProjectDirectory(), "Scenes\\${project.defaultScene}")
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

        eventSystem.publish(ProjectEvent.Closed(project))
    }

    private fun loadDefaultScene(project: Project) {
        val scenesDir = File(project.getProjectDirectory(), "Scenes")
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
        try {
            File(project.projectPath).writeText(serializer.encode(project))
            eventSystem.publish(ProjectEvent.Saved(project))
        } catch (e: Exception) {
            logger.logEditor("Error while saving project: ${e.message}", LogLevel.ERROR)
            return false
        }

        return true
    }

    fun getProjectDirectory(): File? {
        return currentProject?.getProjectDirectory()
    }

    fun hasProject(): Boolean = currentProject != null
}
