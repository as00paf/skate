package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.systems.InputSystem
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.Atlas
import java.io.File

class ProjectManager(
    private val engine: Engine,
) {
    private val engineAssetCopier = EngineAssetCopier()

    private val eventSystem = engine.eventSystem
    private val logger = engine.logger
    private val sceneManager = engine.sceneManager

    var currentProject: Project? = null
        private set

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

            // Copy assets
            val copyResult = engineAssetCopier.copyBundledAssets(projectDir)
            if (copyResult.isSuccess) {
                val count = copyResult.getOrNull() ?: 0
                logger.logEditor("Copied $count engine-bundled assets to project")
            } else {
                logger.logEditor("Failed to copy engine assets: ${copyResult.exceptionOrNull()?.message}")
            }

            // Create default scene with prefabs
            engine.prefabsGenerator.projectAssetsDir = assetsDir.absolutePath + "\\"
            val scenes = engine.prefabsGenerator.createDefaultScenes(scenesDir)

            val project = Project(
                name = name,
                projectPath = projectFile.absolutePath,
                iconPath = assetsDir.absolutePath + Assets.Textures.APP_ICON,
                defaultScene = scenesDir.path + "\\MainScene.scene",
                scenesPath = scenes.map { "${scenesDir.path}\\${it.name}.scene" }
            )
            currentProject = project
            eventSystem.publish(EngineAction.ApplyMappings(project.gameplaySettings.inputMappings))

            saveProject()

            logger.logEditor("Project created successfully: ${project.name}")
            eventSystem.publish(ProjectEvent.Created(project))
            Result.success(project)
        } catch (e: Exception) {
            logger.logEditor("Could not create project: ${e.message}", LogLevel.ERROR)
            Result.failure(e)
        }
    }

    fun openProjectFile(projectFile: File): Boolean {
        if (!projectFile.exists() || !FileType.PROJECT_FILE.extensions.contains(projectFile.extension)) {
            logger.logEditor(
                "Project file does not exist or invalid extension: ${projectFile.absolutePath}",
                LogLevel.ERROR
            )
            return false
        }
        logger.logEditor("Opening project: ${projectFile.absolutePath}")
        val project = engine.serializer.decode<Project>(projectFile.readText())

        return openProject(project)
    }

    fun openProject(
        project: Project,
        binData: ByteArray? = null,
        assetAtlas: Atlas? = null,
        headerSize: Int = 0
    ): Boolean {
        if (hasProject()) closeProject()

        currentProject = project
        engine.systemManager.getSystem<InputSystem>()?.mappings = project.gameplaySettings.inputMappings

        if (binData == null || assetAtlas == null || headerSize <= 0) {
            loadDefaultScenes(project)
        } else {
            engine.assetsManager.initAssetsResolver(assetAtlas, binData, headerSize)

            engine.assetsManager.resolve<Scene>(project.defaultScene)?.let { scene ->
                scene.getComponent<EnvironmentComponent>()?.skyTexture?.filePath?.let { path ->
                    scene.getComponent<EnvironmentComponent>()?.skyTexture = engine.assetsManager.resolveTexture(path)
                }

                scene.gameObjects.forEach { gameObject ->
                    gameObject.getComponent<RenderComponent>()?.resolveModelFromByteArray(engine.assetsManager)

                    gameObject.getComponent<Animator>()?.let { animator ->
                        val resolved = animator.animations.mapNotNull { animation ->
                            engine.assetsManager.resolveAnimation(animation.path)
                        }
                        animator.animations.clear()
                        animator.animations.addAll(resolved)
                    }
                }
                sceneManager.openScene(scene)
            } ?: run {
                logger.logEngine("Failed to load scene", LogLevel.ERROR)
                return false
            }
        }

        eventSystem.publish(ProjectEvent.Opened(project))
        logger.logEditor("Project opened successfully: ${project.name}")

        return true
    }

    fun closeProject() {
        val project = currentProject ?: run {
            logger.logEditor("No project to close")
            return
        }
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

    private fun loadDefaultScenes(project: Project) {
        val defaultSceneFile = File(project.defaultScene)

        if (!defaultSceneFile.exists()) {
            logger.logEditor("No default scene found at ${defaultSceneFile.absolutePath}", LogLevel.ERROR)
            return
        }

        val scenes = project.scenesPath.mapNotNull { path ->
            val file = File(path)
            engine.serializer.decode<Scene?>(file.readText())
        }
        if (scenes.isEmpty()) {
            logger.logEditor("Failed to load default scene from ${defaultSceneFile.absolutePath}")
            return
        }
        sceneManager.openScenes(scenes)

        logger.logEditor("Loaded default scene from ${defaultSceneFile.absolutePath}")
    }

    fun saveProject(): Boolean {
        val project = currentProject ?: run {
            logger.logEditor("No project to save")
            return false
        }

        logger.logEditor("Saving project: ${project.name}")
        try {
            File(project.projectPath).writeText(engine.serializer.encode(project))
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
