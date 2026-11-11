package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.pack.PackedGuids
import com.pafoid.skate.engine.physics3d.ModelCollisionMeshSource
import com.pafoid.skate.engine.physics3d.CollisionMeshSource
import com.pafoid.skate.engine.physics3d.CollisionMeshes
import com.pafoid.skate.engine.assets.EngineDefaults
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
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.assets.serialization.Serializer
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
import com.pafoid.skate.editor.vcs.ConflictMarkers
import org.joml.Vector2i
import java.io.File

/** Scenes decoded off the main thread (EDT-56); [failed] lists the files that could not be read, with the reason. */
class DecodedScenes(val scenes: List<Scene>, val failed: List<String>)

/** A project read and its scenes decoded in the background, ready to open on the main thread (EDT-56). */
class PreparedProject(val project: Project, val scenes: DecodedScenes)

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
    @Volatile
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
    fun createProject(
        name: String,
        folder: File,
        graphicsBackend: GraphicsBackend = GraphicsBackend.DEFAULT,
        preparedDir: File? = null,
        /** Copy the engine defaults into the project (AST-26 §4.3); false references them (basic shaders still copied). */
        copyDefaults: Boolean = true,
    ): Result<Project> {
        return try {
            val projectDir = preparedDir ?: prepareProjectFolder(name, folder, copyDefaults).getOrElse { return Result.failure(it) }
            val projectFile = File(projectDir, "$name.skateproject")

            if (hasProject()) closeProject()
            assetPaths.projectRoot = projectDir.absoluteFile // default scenes are saved project-relative (AST-09)
            val assetsDir = ProjectLayout.assetsDir(projectDir)
            val scenesDir = ProjectLayout.scenesDir(projectDir)

            // Create default scene with prefabs
            engine.prefabsGenerator.projectAssetsDir = assetsDir.absolutePath + "\\"
            val scenes = engine.prefabsGenerator.createDefaultScenes(scenesDir)

            val project = Project(
                name = name,
                projectPath = projectFile.absolutePath,
                // Saved as `engine:Textures/app_icon.png` (AST-26): the project's copy when it has one, else the engine's.
                iconPath = assetPaths.absolute(EngineDefaults.Textures.APP_ICON),
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

    /**
     * The file part of [createProject], safe on a background thread (EDT-56): the project folder and its typed
     * sub-folders, the strings file, and the engine defaults: all of them when [copyDefaults] (the default, AST-26
     * §4.3), else only the basic shaders. Fails when the project exists.
     */
    fun prepareProjectFolder(name: String, folder: File, copyDefaults: Boolean = true): Result<File> {
        logger.logEditor("Creating project: $name in ${folder.absolutePath}")
        return try {
            val projectDir = File(folder, name)
            if (!projectDir.exists()) projectDir.mkdirs()
            if (File(projectDir, "$name.skateproject").exists()) {
                return Result.failure(IllegalStateException("Project '$name' already exists in this folder"))
            }
            val assetsDir = ProjectLayout.create(projectDir)
            File(assetsDir, ProjectLayout.STRINGS_FILE).writeText("project.name=$name")

            val copyResult = engineAssetCopier.copyEngineDefaults(projectDir, copyAll = copyDefaults)
            if (copyResult.isSuccess) {
                logger.logEditor("Copied ${copyResult.getOrNull()?.copied?.size ?: 0} engine default assets to the project")
            } else {
                logger.logEditor("Failed to copy engine assets: ${copyResult.exceptionOrNull()?.message}", LogLevel.ERROR)
            }
            Result.success(projectDir)
        } catch (e: Exception) {
            logger.logEditor("Could not create project: ${e.message}", LogLevel.ERROR)
            Result.failure(e)
        }
    }

    /**
     * The file part of [openProjectFile], safe on a background thread (EDT-56): reads [projectFile] and decodes its
     * scenes. Scene references resolve in the folder the project is in, with a serializer of its own, so the open
     * project is not touched. Null (and [lastError] set) when the project file cannot be read.
     */
    fun prepareProject(projectFile: File): PreparedProject? {
        lastError = null
        val path = projectFile.absolutePath
        if (!projectFile.isFile) return failed("File not found: $path")
        if (!FileType.PROJECT_FILE.extensions.contains(projectFile.extension)) return failed("Not a .skateproject file: $path")
        logger.logEditor("Opening project: $path")
        val project = try {
            ProjectFileReader.read(engine.serializer, projectFile)
        } catch (e: Exception) {
            return failed("Could not read $path: ${e.message ?: e.javaClass.simpleName}")
        }
        val serializer = Serializer(AssetPaths().apply { projectRoot = projectFile.absoluteFile.parentFile })
        return PreparedProject(project, decodeScenes(project, serializer))
    }

    /** Opens [projectFile]; [prepared] skips the reading already done by [prepareProject]. */
    fun openProjectFile(projectFile: File, prepared: PreparedProject? = null): Boolean {
        val ready = prepared ?: prepareProject(projectFile) ?: return false
        lastError = null
        if (hasProject()) closeProject()
        // References in the project file and its scenes are relative to the folder the project is in now (AST-09).
        assetPaths.projectRoot = projectFile.absoluteFile.parentFile
        // Unchanged copies of the basic shaders follow the engine (AST-26 §5.2): refresh them silently.
        runCatching { engineAssetCopier.refreshUnchangedShaders(projectFile.absoluteFile.parentFile) }
            .onSuccess { if (it.isNotEmpty()) logger.logEditor("Updated ${it.size} unchanged engine shader copies") }
        assetPaths.engineDefaults.invalidate()
        return try {
            openProject(ready.project, scenes = ready.scenes)
        } catch (e: Exception) {
            currentProject = null
            assetPaths.projectRoot = null
            fail("Could not open ${projectFile.absolutePath}: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun fail(reason: String): Boolean {
        failed(reason)
        return false
    }

    private fun failed(reason: String): PreparedProject? {
        lastError = reason
        logger.logEditor(reason, LogLevel.ERROR)
        return null
    }

    fun openProject(
        project: Project,
        binData: ByteArray? = null,
        assetAtlas: Atlas? = null,
        headerSize: Int = 0,
        scenes: DecodedScenes? = null,
    ): Boolean {
        if (hasProject()) closeProject()

        currentProject = project
        // ECS-14: Play and the exported game draw from the project's seed (unless -Dskate.seed overrides it).
        engine.random.reseed(RandomService.resolve(project.randomSeed))
        // Editor: references resolve in the project folder. Exported game: they stay relative and come from the pack.
        assetPaths.projectRoot = if (binData == null) project.getProjectDirectory().absoluteFile else null
        engine.systemManager.getSystem<InputSystem>()?.mappings = project.gameplaySettings.inputMappings

        if (binData == null || assetAtlas == null || headerSize <= 0) {
            // PHY-24 / AST-35: meshRef GUIDs resolve through the project's registry (scanned once on the first miss).
            CollisionMeshes.source = assetRegistry()?.let { editorCollisionMeshSource(it, assetPaths) } ?: CollisionMeshSource.NONE
            loadDefaultScenes(project, scenes)
        } else {
            engine.assetsManager.initAssetsResolver(assetAtlas, binData, headerSize)
            // AST-35: an exported game resolves meshRef GUIDs through the pack's GUID table.
            CollisionMeshes.source = PackedGuids.collisionMeshSource(assetPaths)

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
        CollisionMeshes.source = CollisionMeshSource.NONE

        eventSystem.publish(ProjectEvent.Closed(project))
    }

    private fun loadDefaultScenes(project: Project, decoded: DecodedScenes? = null) {
        val defaultSceneFile = File(project.defaultScene)

        if (!defaultSceneFile.exists()) {
            logger.logEditor("No default scene found at ${defaultSceneFile.absolutePath}", LogLevel.ERROR)
            eventSystem.publish(EditorError("error.open_scene.title", defaultSceneFile.absolutePath, "error.default_scene_missing.message"))
            return
        }

        // A missing or broken scene file is reported and skipped; the others still open (EDT-10).
        val (scenes, failed) = (decoded ?: decodeScenes(project, engine.serializer)).let { it.scenes to it.failed }
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

    /** Reads and decodes every scene of [project]; touches no engine state, so it can run off the main thread. */
    private fun decodeScenes(project: Project, serializer: Serializer): DecodedScenes {
        val failed = mutableListOf<String>()
        val scenes = project.scenesPath.mapNotNull { path ->
            val file = File(path)
            try {
                serializer.decodeFile<Scene?>(FileKind.SCENE, ConflictMarkers.requireClean(file.name, file.readText()))
            } catch (e: Exception) {
                failed += "${file.absolutePath}: ${e.message ?: e.javaClass.simpleName}"
                null
            }
        }
        return DecodedScenes(scenes, failed)
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

/**
 * Collision models in the editor (PHY-24, AST-35): GUIDs (and project paths, `engine:` references) through [registry],
 * scanned once on the first miss because the editor does not scan it on open; files through [paths].
 */
internal fun editorCollisionMeshSource(registry: AssetRegistry, paths: AssetPaths) = ModelCollisionMeshSource(
    resolve = { ref -> registry.resolve(ref) ?: run { registry.scan(); registry.resolve(ref) } },
    localPath = paths::localPath,
)
