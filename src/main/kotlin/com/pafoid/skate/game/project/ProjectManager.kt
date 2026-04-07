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
import java.io.File

class ProjectManager(
    private val settingsManager: SettingsManager,
    private val serializer: Serializer,
    private val logger: LoggerService,
    private val assetDatabase: AssetDatabase,
    private val engineAssetCopier: EngineAssetCopier,
    private val sceneManager: SceneManager,
    private val prefabsGenerator: PrefabsGenerator,
    private val levelManager: LevelManager
) {

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
                            // Copy engine-bundled default assets BEFORE scanning
                            // so AssetDatabase can discover them and create .meta files with GUIDs
                            engineAssetCopier.copyBundledAssets(projectDir).fold(
                                onSuccess = { count ->
                                    logger.logEditor("Copied $count engine-bundled assets to project")
                                    // Tell PrefabsGenerator where to find the copied assets
                                    prefabsGenerator.setEngineDefaultsRoot(projectDir)
                                },
                                onFailure = { e ->
                                    logger.logEditor("Failed to copy engine assets: ${e.message}")
                                }
                            )
                            // Now scan — .meta files will be created for all assets
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

        // CRITICAL: Populate modelGuid on all RenderComponents before saving.
        // model is @Transient and won't be serialized — without modelGuid,
        // models won't reload when the scene is opened later.
        resolveModelGuidsInScene(scene)

        // Set the level path and save
        scene.sceneData.levelPath = defaultSceneFile.absolutePath
        levelManager.saveToFile(scene, defaultSceneFile.absolutePath)

        logger.logEditor("Default scene saved to ${defaultSceneFile.absolutePath}")
    }

    /**
     * Walk all RenderComponents in the scene and populate modelGuid from the AssetDatabase.
     * This is called before saving the default scene to ensure models can be
     * reloaded when the scene is opened later.
     */
    private fun resolveModelGuidsInScene(scene: com.pafoid.skate.engine.ecs.Scene) {
        scene.gameObjectManager.gameObjects.forEach { obj ->
            resolveModelGuidForObject(obj)
            obj.children.forEach { child -> resolveModelGuidForObject(child) }
        }
    }

    private fun resolveModelGuidForObject(obj: com.pafoid.skate.engine.ecs.GameObject) {
        val rc = obj.getComponent<com.pafoid.skate.engine.ecs.components.RenderComponent>() ?: return
        val model = rc.model ?: return
        if (rc.modelGuid.isNotBlank()) return

        val modelPath = model.sourcePath ?: run {
            logger.logEditor("WARNING: model.sourcePath is null for '${obj.name}', cannot resolve modelGuid")
            return
        }
        val modelFile = File(modelPath)

        // Always use absolute path for reliable round-trip serialization
        val absolutePath = if (modelFile.isAbsolute) modelFile.absolutePath else File(modelPath).absolutePath

        // Try to find this model in the AssetDatabase
        val asset = assetDatabase.getByAbsolutePath(absolutePath)

        if (asset != null) {
            rc.modelGuid = asset.guid.value
            logger.logEditor("Resolved model GUID for ${obj.name}: ${asset.guid.value.take(8)}... (${asset.sourcePath})")
        } else {
            // Engine-bundled asset not in AssetDatabase — store absolute path as fallback
            rc.modelGuid = absolutePath
            logger.logEditor("Stored absolute path for ${obj.name}: $absolutePath (engine-bundled asset)")
        }

        // Also resolve texture GUIDs from the model's materials
        resolveTextureGuidsForObject(obj)
    }

    /**
     * Walk all mesh part materials in the model and store texture paths as GUIDs
     * (or absolute paths for engine-bundled textures not in the AssetDatabase).
     */
    private fun resolveTextureGuidsForObject(obj: com.pafoid.skate.engine.ecs.GameObject) {
        val rc = obj.getComponent<com.pafoid.skate.engine.ecs.components.RenderComponent>() ?: return
        val model = rc.model ?: return

        model.mesh.forEach { meshPart ->
            val mat = meshPart.material
            // Resolve albedo/base color texture
            if (mat.baseColorTexture != null && rc.albedoTextureGuid.isBlank()) {
                val texPath = mat.baseColorTexture?.filePath ?: mat.baseColorPath ?: ""
                if (texPath.isNotBlank()) {
                    val texFile = File(texPath)
                    val texAbsolutePath = if (texFile.isAbsolute) texFile.absolutePath else File(texPath).absolutePath
                    val texAsset = assetDatabase.getByAbsolutePath(texAbsolutePath)
                    rc.albedoTextureGuid = texAsset?.guid?.value ?: texAbsolutePath
                }
            }
            if (mat.normalMap != null && rc.normalMapGuid.isBlank()) {
                val texPath = mat.normalMap?.filePath ?: mat.normalMapPath ?: ""
                if (texPath.isNotBlank()) {
                    val texFile = File(texPath)
                    val texAbsolutePath = if (texFile.isAbsolute) texFile.absolutePath else File(texPath).absolutePath
                    val texAsset = assetDatabase.getByAbsolutePath(texAbsolutePath)
                    rc.normalMapGuid = texAsset?.guid?.value ?: texAbsolutePath
                }
            }
            if (mat.metallicRoughnessTexture != null && rc.metallicRoughnessGuid.isBlank()) {
                val texPath = mat.metallicRoughnessTexture?.filePath ?: mat.metallicRoughnessPath ?: ""
                if (texPath.isNotBlank()) {
                    val texFile = File(texPath)
                    val texAbsolutePath = if (texFile.isAbsolute) texFile.absolutePath else File(texPath).absolutePath
                    val texAsset = assetDatabase.getByAbsolutePath(texAbsolutePath)
                    rc.metallicRoughnessGuid = texAsset?.guid?.value ?: texAbsolutePath
                }
            }
        }
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
                            // Tell PrefabsGenerator where to find engine-bundled assets
                            prefabsGenerator.setEngineDefaultsRoot(projectDir)
                            logger.logEditor("Asset database initialized and scanned for ${projectDir.name}")
                        },
                        onFailure = { e ->
                            logger.logEditor("Failed to initialize asset database: ${e.message}")
                        }
                    )
                }

                logger.logEditor("Project opened successfully: ${getProjectName()}")

                // Load the default scene if it exists
                loadDefaultScene()
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

        // Destroy all game objects from open scenes and clean up physics
        sceneManager.openScenes.toList().forEach { scene ->
            // Remove physics bodies BEFORE destroying game objects
            scene.gameObjectManager.gameObjects.forEach { go ->
                scene.physics3d.remove(go)
            }
            scene.gameObjectManager.gameObjects.forEach { it.destroy() }
            scene.gameObjectManager.gameObjects.clear()
            scene.gameObjectManager.pendingObjects.clear()

            // Reset system caches so they rebuild with new objects
            scene.systemManager.resetSystemCaches()
        }

        currentProject = null
        assetDatabase.shutdown()
        settingsManager.setLastClosedProjectPath(path)
        settingsManager.closeProject()
        onProjectClosed?.invoke()
    }

    var onProjectClosed: (() -> Unit)? = null

    /**
     * Load the project's default scene if it exists.
     */
    private fun loadDefaultScene() {
        val projectDir = getProjectDirectory() ?: return
        val defaultSceneFile = File(projectDir, "Scenes/main.scene")

        if (!defaultSceneFile.exists()) {
            logger.logEditor("No default scene found at ${defaultSceneFile.absolutePath}")
            return
        }

        val scene = sceneManager.currentScene ?: run {
            logger.logEditor("No current scene, cannot load default scene")
            return
        }

        levelManager.loadFromFile(scene, defaultSceneFile.absolutePath)

        logger.logEditor("Loaded default scene from ${defaultSceneFile.absolutePath}")
    }

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
