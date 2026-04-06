package com.pafoid.skate.game.project

import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.settings.ProjectSettings
import com.pafoid.skate.engine.settings.RecentProjectInfo
import com.pafoid.skate.game.level.LevelManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class ProjectManager(
    private val settingsManager: SettingsManager,
    private val serializer: Serializer,
    private val logger: LoggerService,
    private val assetDatabase: AssetDatabase,
    private val sceneManager: SceneManager,
    private val prefabsGenerator: PrefabsGenerator,
    private val levelManager: LevelManager
) : KoinComponent {

    var currentProject: ProjectSettings? = null
        private set

    fun hasProject(): Boolean = currentProject != null

    fun getProjectName(): String = currentProject?.metadata?.name ?: "No Project"

    fun getRecentProjects(): List<RecentProjectInfo> {
        return settingsManager.recentProjects
    }

    fun loadLastProject(): Boolean {
        val recent = settingsManager.recentProjects.firstOrNull() ?: return false
        val projectFile = File(recent.path)
        if (!projectFile.exists()) return false
        return openProject(projectFile)
    }

    fun createProject(name: String, folder: File, engineVersion: String = "v0.46.0.1.19"): Result<ProjectSettings> {
        return try {
            logger.logEditor("Creating project: $name in ${folder.absolutePath}")

            val result = settingsManager.createProject(name, folder, engineVersion)

            result.onSuccess { project ->
                currentProject = project
                logger.logEditor("Project created successfully: ${project.metadata.name}")

                // Initialize asset database for this new project
                val projectDir = getProjectDirectory()
                if (projectDir != null) {
                    assetDatabase.initialize(projectDir).fold(
                        onSuccess = {
                            assetDatabase.scanAll()
                            logger.logEditor("Asset database initialized and scanned for ${projectDir.name}")
                        },
                        onFailure = { e ->
                            logger.logEditor("Failed to initialize asset database: ${e.message}")
                        }
                    )
                }

                // Create default scene with prefabs
                createDefaultScene()
            }

            result.onFailure { error ->
                logger.logEngine("Failed to create project: ${error.message}", LogLevel.ERROR)
            }

            result
        } catch (e: Exception) {
            logger.logEngine("Error creating project: ${e.message}", LogLevel.ERROR)
            Result.failure(e)
        }
    }

    /**
     * Create the default scene with prefabs when a new project is created.
     * This is the project manager's responsibility — it owns project lifecycle.
     */
    private fun createDefaultScene() {
        val projectDir = getProjectDirectory() ?: run {
            logger.logEditor("No project directory, cannot create default scene")
            return
        }
        val defaultSceneFile = File(projectDir, "Scenes/main.scene")

        // Already exists — nothing to do
        if (defaultSceneFile.exists()) {
            return
        }

        logger.logEditor("Creating default scene with prefabs...")

        // Ensure parent directory exists
        defaultSceneFile.parentFile?.mkdirs()

        // Get the current scene from scene manager
        val scene = sceneManager.currentScene ?: run {
            logger.logEditor("No current scene, cannot create default scene")
            return
        }

        // Spawn prefabs synchronously — uses addGameObjectImmediate so they go into gameObjects
        prefabsGenerator.spawnSkateboardSync(scene)
        prefabsGenerator.spawnSkaterSync(scene = scene)
        prefabsGenerator.spawnFloorSync(scene)

        logger.logEditor("Spawned ${scene.gameObjectManager.gameObjects.size} objects")

        // Set the level path and save
        scene.sceneData.levelPath = defaultSceneFile.absolutePath
        levelManager.saveToFile(scene, defaultSceneFile.absolutePath)

        logger.logEditor("Default scene saved to ${defaultSceneFile.absolutePath}")
    }

    fun openProject(projectFile: File): Boolean {
        return try {
            settingsManager.setLastClosedProjectPath(null)

            logger.logEditor("Opening project: ${projectFile.absolutePath}")

            if (!projectFile.exists()) {
                logger.logEngine("Project file does not exist: ${projectFile.absolutePath}", LogLevel.ERROR)
                return false
            }

            if (projectFile.extension != "skateproject") {
                logger.logEngine("Invalid project file extension: ${projectFile.name}", LogLevel.ERROR)
                return false
            }

            val success = settingsManager.loadProject(projectFile)

            if (success) {
                currentProject = settingsManager.project

                // Load asset registry from project file if present
                currentProject?.assetRegistry?.let { registryData ->
                    assetDatabase.importRegistryData(registryData)
                    logger.logEditor("Asset registry loaded from project file (${registryData.assets.size} assets)")
                }

                // Initialize asset database for this project
                val projectDir = getProjectDirectory()
                if (projectDir != null) {
                    assetDatabase.initialize(projectDir).fold(
                        onSuccess = {
                            assetDatabase.scanAll()
                            logger.logEditor("Asset database initialized and scanned for ${projectDir.name}")
                        },
                        onFailure = { e ->
                            logger.logEditor("Failed to initialize asset database: ${e.message}")
                        }
                    )
                }

                logger.logEditor("Project opened successfully: ${getProjectName()}")
            } else {
                logger.logEngine("Failed to load project: ${projectFile.absolutePath}", LogLevel.ERROR)
            }

            success
        } catch (e: Exception) {
            logger.logEngine("Error opening project: ${e.message}", LogLevel.ERROR)
            false
        }
    }

    fun closeProject() {
        val path = currentProject?.getProjectFile()?.absolutePath
        logger.logEditor("Closing project: ${getProjectName()}")

        // Export registry and embed in project file before closing
        currentProject?.let { project ->
            val registryData = assetDatabase.exportRegistryData()
            // Update the project settings with the registry
            settingsManager.updateProjectAssetRegistry(project, registryData)
        }

        currentProject = null
        assetDatabase.shutdown()
        settingsManager.setLastClosedProjectPath(path)
        settingsManager.closeProject()
        onProjectClosed?.invoke()
    }

    var onProjectClosed: (() -> Unit)? = null

    fun saveProject(): Boolean {
        val project = currentProject ?: run {
            logger.logEditor("No project to save")
            return false
        }

        logger.logEditor("Saving project: ${project.metadata.name}")
        return settingsManager.saveProject(project)
    }

    fun getProjectDirectory(): File? {
        return currentProject?.getProjectDirectory()
    }

    fun getAssetsDirectory(): File? {
        return getProjectDirectory()?.let { projectDir ->
            File(projectDir, "Assets")
        }
    }

    fun getScenesDirectory(): File? {
        return getProjectDirectory()?.let { projectDir ->
            File(projectDir, "Scenes")
        }
    }
}
