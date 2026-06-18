package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.project.GameplaySettings
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.project.ProjectMetadata
import com.pafoid.skate.editor.settings.RecentProjectInfo
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.logEditor
import com.pafoid.skate.engine.data.LogLevel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
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
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.IJobSystem
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
    private val eventSystem: EventSystem,
    private val systemManager: SystemManager,
    private val jobSystem: IJobSystem,
    private val serializer: Serializer
) {

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

    fun createProject(name: String, folder: File, engineVersion: String = "v0.46.0.1.19"): Result<Project> {
        logger.logEditor("Creating project: $name in ${folder.absolutePath}")
        return try {
            val projectFile = File(folder, "$name.skateproject")
            if (projectFile.exists()) {
                return Result.failure(IllegalStateException("Project '$name' already exists in this folder"))
            }

            val projectDir = File(folder, name)
            if (!projectDir.exists()) {
                projectDir.mkdirs()
            }

            File(projectDir, "Assets").mkdirs()
            File(projectDir, "Scenes").mkdirs()
            File(projectDir, "Builds").mkdirs()

            val project = Project(
                metadata = ProjectMetadata(name, engineVersion = engineVersion, projectPath = projectFile.absolutePath),
                defaultScene = "Scenes/main.scene",
                assetPaths = listOf("Assets"),
                scenePaths = listOf("Scenes"),
                buildPaths = listOf("Builds")
            )

            // Init DB
            assetDatabase.initialize(projectDir).onSuccess {
                engineAssetCopier.copyBundledAssets(projectDir).onSuccess { count ->
                    logger.logEditor("Copied $count engine-bundled assets to project")
                    prefabsGenerator.setEngineDefaultsRoot(projectDir)
                }.onFailure { e -> logger.logEditor("Failed to copy engine assets: ${e.message}") }
                assetDatabase.scanAll()
                logger.logEditor("Asset database initialized and scanned for ${projectDir.name}")
            }.onFailure { e ->
                logger.logEditor("Failed to initialize asset database: ${e.message}")
            }

            // Create default scene with prefabs
            createDefaultScene(projectDir)

            settingsManager.addToRecentProjects(project.getProjectFile().absolutePath) // TODO: move to settings manager
            currentProject = project
            saveProject()

            logger.logEditor("Project created successfully: ${project.metadata.name}")
            eventSystem.publish(ProjectEvent.Created(project))
            Result.success(project)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createDefaultScene(projectDir: File) {
        val sceneFileName = currentProject?.defaultScene?.takeIf { it.isNotBlank() } ?: "Scenes/main.scene"
        val defaultSceneFile = File(projectDir, sceneFileName)

        // Already exists — nothing to do
        if (defaultSceneFile.exists()) {
            return
        }

        logger.logEditor("Creating default scene with prefabs...")

        defaultSceneFile.parentFile?.mkdirs()
        val scene = Scene("MainScene")

        // Spawn prefabs synchronously — uses addGameObjectImmediate so they go into gameObjects
        prefabsGenerator.spawnSkateboardSync()
        prefabsGenerator.spawnSkaterSync()
        prefabsGenerator.spawnFloorSync()

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
        sceneManager.saveScene(scene)

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
            if (!projectFile.exists() || projectFile.extension != "skateproject") {
                logger.logEditor(
                    "Project file does not exist or invalid extension: ${projectFile.absolutePath}",
                    LogLevel.ERROR
                )
                return false
            }

            if (hasProject()) closeProject()

            logger.logEditor("Opening project: ${projectFile.absolutePath}")
            val loadedProject = serializer.decode<Project>(projectFile.readText()) // TODO: should be on io
            currentProject = loadedProject

            eventSystem.publish(EngineAction.ApplyMappings(loadedProject.gameplaySettings.inputMappings))

            getProjectDirectory()?.let { initProjectDb(it) }
            loadDefaultScene()

            eventSystem.publish(ProjectEvent.Opened(loadedProject))
            logger.logEditor("Project opened successfully: ${getProjectName()}")

            true
        } catch (e: Exception) {
            logger.logEditor("Error opening project: ${e.message}", LogLevel.ERROR)
            false
        }
    }

    private fun initProjectDb(projectDir: File) {
        val openEpoch = lifecycleEpoch.incrementAndGet()
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

    private fun loadDefaultScene() {
        val projectDir = getProjectDirectory() ?: return
        val sceneFileName = currentProject?.defaultScene?.takeIf { it.isNotBlank() } ?: "Scenes/main.scene"
        val defaultSceneFile = File(projectDir, sceneFileName)

        if (!defaultSceneFile.exists()) {
            logger.logEditor("No default scene found at ${defaultSceneFile.absolutePath}")
            return
        }

        val scene = serializer.decode<Scene?>(defaultSceneFile.readText()) ?: run {
            val sceneName = defaultSceneFile.nameWithoutExtension.ifBlank { "MainScene" }
            sceneManager.createNewScene(sceneName, defaultSceneFile.absolutePath)
        } ?: run {
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

        logger.logEditor("Saving project: ${project.metadata.name}")
        val result = settingsManager.saveProject(project)
        File(project.metadata.projectPath).writeText(serializer.encode(project))
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

    fun getProjectName(): String = currentProject?.metadata?.name ?: "No Project"

    fun getRecentProjects(): List<RecentProjectInfo> {
        return settingsManager.recentProjects
    }

    fun getProjectDirectory(): File? {
        return currentProject?.getProjectDirectory()
    }

    fun hasProject(): Boolean = currentProject != null
}
