package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.ecs.random.RandomService
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.events.EditorError
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.project.ProjectFileReader
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.AssetRegistry
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
import org.joml.Vector2i
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

    /** Why the last open or save failed, for the error dialog (EDT-10); null after a success. */
    var lastError: String? = null
        private set

    private var assetRegistry: AssetRegistry? = null

    /** Registry rooted at the current project folder (not scanned), or null when no project is open. */
    fun assetRegistry(): AssetRegistry? {
        val root = currentProject?.getProjectFile()?.absoluteFile?.parentFile ?: return null
        if (assetRegistry?.root != root) assetRegistry = AssetRegistry(root)
        return assetRegistry
    }

    private val assetPaths get() = engine.serializer.assetPaths

    /** Creates [name] in [folder]; [graphicsBackend] is what its exported games start with (EDT-43). */
    fun createProject(name: String, folder: File, graphicsBackend: GraphicsBackend = GraphicsBackend.DEFAULT): Result<Project> {
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

            if (hasProject()) closeProject()
            assetPaths.projectRoot = projectDir.absoluteFile // default scenes are saved project-relative (AST-09)

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
                logger.logEditor("Failed to copy engine assets: ${copyResult.exceptionOrNull()?.message}", LogLevel.ERROR)
            }

            // Create default scene with prefabs
            engine.prefabsGenerator.projectAssetsDir = assetsDir.absolutePath + "\\"
            val scenes = engine.prefabsGenerator.createDefaultScenes(scenesDir)

            val project = Project(
                name = name,
                projectPath = projectFile.absolutePath,
                iconPath = assetsDir.absolutePath + Assets.Textures.APP_ICON,
                defaultScene = scenesDir.path + "\\MainScene.scene",
                scenesPath = scenes.map { "${scenesDir.path}\\${it.name}.scene" },
                resolution = Vector2i(1920, 1080),
                graphicsBackend = graphicsBackend,
                randomSeed = RandomService.newSeed(),
            )
            currentProject = project
            engine.random.reseed(RandomService.resolve(project.randomSeed))
            eventSystem.publish(EngineAction.ApplyMappings(project.gameplaySettings.inputMappings))

            saveProject()
            // Open the new project's scenes so the editor (hierarchy, viewport) shows them immediately.
            loadDefaultScenes(project)

            logger.logEditor("Project created successfully: ${project.name}")
            eventSystem.publish(ProjectEvent.Created(project))
            Result.success(project)
        } catch (e: Exception) {
            logger.logEditor("Could not create project: ${e.message}", LogLevel.ERROR)
            Result.failure(e)
        }
    }

    fun openProjectFile(projectFile: File): Boolean {
        lastError = null
        val path = projectFile.absolutePath
        if (!projectFile.isFile) return fail("File not found: $path")
        if (!FileType.PROJECT_FILE.extensions.contains(projectFile.extension)) return fail("Not a .skateproject file: $path")
        logger.logEditor("Opening project: $path")
        val project = try {
            ProjectFileReader.read(engine.serializer, projectFile)
        } catch (e: Exception) {
            return fail("Could not read $path: ${e.message ?: e.javaClass.simpleName}")
        }
        if (hasProject()) closeProject()
        // References in the project file and its scenes are relative to the folder the project is in now (AST-09).
        assetPaths.projectRoot = projectFile.absoluteFile.parentFile
        return try {
            openProject(project)
        } catch (e: Exception) {
            currentProject = null
            assetPaths.projectRoot = null
            fail("Could not open $path: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun fail(reason: String): Boolean {
        lastError = reason
        logger.logEditor(reason, LogLevel.ERROR)
        return false
    }

    fun openProject(
        project: Project,
        binData: ByteArray? = null,
        assetAtlas: Atlas? = null,
        headerSize: Int = 0
    ): Boolean {
        if (hasProject()) closeProject()

        currentProject = project
        // ECS-14: Play and the exported game draw from the project's seed (unless -Dskate.seed overrides it).
        engine.random.reseed(RandomService.resolve(project.randomSeed))
        // Editor: references resolve in the project folder. Exported game: they stay relative and come from the pack.
        assetPaths.projectRoot = if (binData == null) project.getProjectDirectory().absoluteFile else null
        engine.systemManager.getSystem<InputSystem>()?.mappings = project.gameplaySettings.inputMappings

        if (binData == null || assetAtlas == null || headerSize <= 0) {
            loadDefaultScenes(project)
        } else {
            engine.assetsManager.initAssetsResolver(assetAtlas, binData, headerSize)

            engine.assetsManager.resolve<Scene>(project.defaultScene)?.let { scene ->
                scene.getComponent<EnvironmentComponent>()?.skyTexture?.filePath?.let { path ->
                    scene.getComponent<EnvironmentComponent>()?.skyTexture = engine.assetsManager.resolveTexture(path)
                }

                scene.children.forEach { gameObject ->
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
        // Stops Play and restores the pre-play scene, so runtime state is not saved below (EDT-45).
        eventSystem.publish(ProjectEvent.Closing(project))

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
        assetPaths.projectRoot = null

        eventSystem.publish(ProjectEvent.Closed(project))
    }

    private fun loadDefaultScenes(project: Project) {
        val defaultSceneFile = File(project.defaultScene)

        if (!defaultSceneFile.exists()) {
            logger.logEditor("No default scene found at ${defaultSceneFile.absolutePath}", LogLevel.ERROR)
            eventSystem.publish(EditorError("error.open_scene.title", defaultSceneFile.absolutePath, "error.default_scene_missing.message"))
            return
        }

        // A missing or broken scene file is reported and skipped; the others still open (EDT-10).
        val failed = mutableListOf<String>()
        val scenes = project.scenesPath.mapNotNull { path ->
            val file = File(path)
            try {
                engine.serializer.decodeFile<Scene?>(FileKind.SCENE, file.readText())
            } catch (e: Exception) {
                failed += "${file.absolutePath}: ${e.message ?: e.javaClass.simpleName}"
                null
            }
        }
        if (failed.isNotEmpty()) {
            failed.forEach { logger.logEditor("Could not load scene $it", LogLevel.ERROR) }
            eventSystem.publish(EditorError("error.open_scene.title", failed.joinToString("\n")))
        }
        if (scenes.isEmpty()) {
            logger.logEditor("Failed to load default scene from ${defaultSceneFile.absolutePath}", LogLevel.ERROR)
            return
        }
        sceneManager.openScenes(scenes)

        logger.logEditor("Loaded default scene from ${defaultSceneFile.absolutePath}")
    }

    fun saveProject(): Boolean {
        lastError = null
        val project = currentProject ?: return fail("No project is open.")

        logger.logEditor("Saving project: ${project.name}")
        try {
            File(project.projectPath).writeText(engine.serializer.encodeFile(FileKind.PROJECT, project))
            eventSystem.publish(ProjectEvent.Saved(project))
        } catch (e: Exception) {
            return fail("Could not write ${project.projectPath}: ${e.message ?: e.javaClass.simpleName}")
        }

        return true
    }

    fun getProjectDirectory(): File? {
        return currentProject?.getProjectDirectory()
    }

    fun hasProject(): Boolean = currentProject != null
}
