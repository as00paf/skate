package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.project.GameplaySettings
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.editor.settings.RecentProjectInfo
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.collectGameObjectsDepthFirst
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.LightingStateComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.TimeComponent
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.IJobSystem
import kotlinx.coroutines.runBlocking
import org.joml.Vector3f
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class ProjectManager(
    private val settingsManager: SettingsManager,
    private val logger: LoggerService,
    private val assetDatabase: AssetDatabase,
    private val engineAssetCopier: EngineAssetCopier,
    private val sceneManager: SceneManager,
    private val prefabsGenerator: PrefabsGenerator,
    private val sceneSerializer: SceneSerializer,
    private val eventSystem: EventSystem,
    private val systemManager: SystemManager,
    private val jobSystem: IJobSystem,
) {

    var currentProject: Project? = null
        private set
    private val lifecycleEpoch = AtomicLong(0)

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

    fun createProject(name: String, folder: File, engineVersion: String = "v0.46.0.1.19"): Result<Project> {
        return try {
            logger.logEditor("Creating project: $name in ${folder.absolutePath}")

            val result = settingsManager.createProject(name, folder, engineVersion)

            result.onSuccess { project ->
                currentProject = project
                eventSystem.publish(ProjectEvent.Created(project))
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
        val sceneFileName = currentProject?.defaultScene?.takeIf { it.isNotBlank() } ?: "Scenes/main.scene"
        val defaultSceneFile = File(projectDir, sceneFileName)

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
        prefabsGenerator.spawnSkaterSync(scene)
        prefabsGenerator.spawnFloorSync(scene)

        logger.logEditor("Spawned ${scene.gameObjects.size} objects")

        scene.collectGameObjectsDepthFirst().forEach { obj ->
            resolveModelGuidForObject(obj)
            resolveAnimatorPathsForObject(obj)
        }

        // Add scene components
        scene.addComponent(EnvironmentComponent())
        val timeComponent = TimeComponent(timeOfDay = 12.0f, timeScale = 1.0f)
        scene.addComponent(timeComponent)
        scene.addComponent(LightingStateComponent())
        scene.addComponent(DayNightCycleComponent(cycleTime = timeComponent.timeOfDay, dayDuration = 300f))
        scene.addComponent(
            DirectionalLightComponent(
                direction = Vector3f(0f, -1f, 0f),
                color = Vector3f(1f, 0.95f, 0.8f),
                intensity = 1f,
                shadowDistance = 50f,
                autoCalculateBounds = true,
                stabilizeProjection = true,
                depthBias = 0.0f,
                slopeScaledBias = 0.0f,
                castShadows = true,
            )
        )

        // Set the level path and save
        scene.name = defaultSceneFile.name
        scene.sceneData.levelPath = defaultSceneFile.absolutePath
        sceneSerializer.saveToFile(scene, defaultSceneFile.absolutePath)

        logger.logEditor("Default scene saved to ${defaultSceneFile.absolutePath}")
    }

    private fun resolveModelGuidForObject(obj: GameObject) {
        val rc = obj.getComponent<RenderComponent>() ?: return
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
    private fun resolveTextureGuidsForObject(obj: GameObject) {
        val rc = obj.getComponent<RenderComponent>() ?: return
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

    private fun resolveAnimatorPathsForObject(obj: GameObject) {
        val animator = obj.getComponent<Animator>() ?: return
        if (animator.animationPaths.isNotEmpty()) return

        animator.getLoadedAnimations().forEach { anim ->
            if (anim.path.isNotBlank()) {
                animator.animationPaths.add(anim.path)
            }
        }

        if (animator.animationPaths.isNotEmpty()) {
            logger.logEditor("Resolved ${animator.animationPaths.size} animation paths for ${obj.name}")
        }
    }

    fun openProject(projectFile: File): Boolean {
        return try {
            if (hasProject()) {
                closeProject()
            }
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

            val loadedProject = settingsManager.loadProject(projectFile)

            if (loadedProject != null) {
                val openEpoch = lifecycleEpoch.incrementAndGet()
                currentProject = loadedProject
                eventSystem.publish(ProjectEvent.Opened(loadedProject))

                // Initialize asset database for this project
                val projectDir = getProjectDirectory()
                if (projectDir != null) {
                    assetDatabase.initialize(projectDir).fold(
                        onSuccess = {
                            // Tell PrefabsGenerator where to find engine-bundled assets
                            prefabsGenerator.setEngineDefaultsRoot(projectDir)
                            logger.logEditor("Asset database initialized for ${projectDir.name}")

                            // Scan assets off the UI path; drop stale scans if project lifecycle changes.
                            jobSystem.runIO {
                                if (openEpoch != lifecycleEpoch.get()) return@runIO
                                assetDatabase.scanAll().fold(
                                    onSuccess = {
                                        logger.logEditor("Asset scan completed for ${projectDir.name}")
                                    },
                                    onFailure = { e ->
                                        logger.logEditor("Asset scan failed for ${projectDir.name}: ${e.message}")
                                    }
                                )
                            }
                        },
                        onFailure = { e ->
                            logger.logEditor("Failed to initialize asset database: ${e.message}")
                        }
                    )
                }

                logger.logEditor("Project opened successfully: ${getProjectName()}")

                // Load the default scene if it exists
                loadDefaultScene()
                true
            } else {
                logger.logEngine("Failed to load project: ${projectFile.absolutePath}", LogLevel.ERROR)
                false
            }
        } catch (e: Exception) {
            logger.logEngine("Error opening project: ${e.message}", LogLevel.ERROR)
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
        val projectName = project.metadata.name
        logger.logEditor("Closing project: $projectName")

        // Ensure scenes/resources are fully reset before switching/closing project context.
        sceneManager.closeAllScenes()
        systemManager.resetSystemCaches()

        currentProject = null
        assetDatabase.shutdown()
        settingsManager.setLastClosedProjectPath(path)
        settingsManager.closeProject()

        eventSystem.publish(ProjectEvent.Closed(projectName))
    }

    /**
     * Load the project's default scene if it exists.
     */
    private fun loadDefaultScene() {
        val projectDir = getProjectDirectory() ?: return
        val sceneFileName = currentProject?.defaultScene?.takeIf { it.isNotBlank() } ?: "Scenes/main.scene"
        val defaultSceneFile = File(projectDir, sceneFileName)

        if (!defaultSceneFile.exists()) {
            logger.logEditor("No default scene found at ${defaultSceneFile.absolutePath}")
            return
        }

        val scene = sceneManager.currentScene ?: run {
            val sceneName = defaultSceneFile.nameWithoutExtension.ifBlank { "MainScene" }
            val newScene = runCatching {
                runBlocking { sceneManager.createScene(sceneName, defaultSceneFile.absolutePath, forceSingle = true) }
            }.onFailure { e ->
                logger.logEditor("Failed to bootstrap scene for default scene load: ${e.message}")
                return
            }.getOrNull()

            if (newScene == null) {
                logger.logEditor("Failed to bootstrap scene for default scene load")
                return
            }

            newScene
        }

        // Bind systems to the target scene before deserialization so systems used by the
        // serializer (e.g., GameObjectManager) operate on the correct scene instance.
        systemManager.loadScene(scene)
        val loaded = sceneSerializer.loadFromFile(scene, defaultSceneFile.absolutePath)
        if (!loaded) {
            logger.logEditor("Failed to load default scene from ${defaultSceneFile.absolutePath}")
            return
        }
        systemManager.loadScene(scene)

        logger.logEditor("Loaded default scene from ${defaultSceneFile.absolutePath}")
    }

    fun saveProject(): Boolean {
        val project = currentProject ?: run {
            logger.logEditor("No project to save")
            return false
        }

        logger.logEditor("Saving project: ${project.metadata.name}")
        val result = settingsManager.saveProject(project)
        if (result) {
            eventSystem.publish(ProjectEvent.Saved(project))
        }
        return result
    }

    fun updateGameplaySettings(physicsFPS: Int, gravity: Float, timeScale: Float): Boolean {
        val project = currentProject ?: run {
            logger.logEditor("No project loaded, cannot update gameplay settings", LogLevel.ERROR)
            return false
        }

        val updatedProject = project.copy(
            gameplaySettings = GameplaySettings(
                physicsFPS = physicsFPS,
                gravity = gravity,
                timeScale = timeScale
            )
        )

        val result = settingsManager.saveProject(updatedProject)
        if (!result) {
            logger.logEditor("Failed to save gameplay settings for project: ${project.metadata.name}", LogLevel.ERROR)
            return false
        }

        currentProject = updatedProject
        eventSystem.publish(ProjectEvent.Saved(updatedProject))
        logger.logEditor("Gameplay settings updated for project: ${updatedProject.metadata.name}")
        return true
    }

    fun getProjectDirectory(): File? {
        return currentProject?.getProjectDirectory()
    }
}
