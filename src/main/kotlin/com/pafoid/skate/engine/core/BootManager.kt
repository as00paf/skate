package com.pafoid.skate.engine.core

import com.pafoid.skate.app.SplashScreen
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.renderer.Renderer
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.game.level.LevelManager
import com.pafoid.skate.game.project.ProjectManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class BootManager(
    private val sceneManager: SceneManager,
    private val renderer: Renderer,
    private val logger: LoggerService,
    private val splashScreen: SplashScreen,
    private val audioEngine: AudioEngine,
    private val sceneInitializer: LevelEditorSceneInitializer,
    private val projectManager: ProjectManager,
    private val prefabsGenerator: PrefabsGenerator,
    private val levelManager: LevelManager,
    private val mainDispatcher: CoroutineDispatcher = JobSystem.Main
) {

    suspend fun boot(engineState: AtomicReference<EngineState>) = withContext(mainDispatcher) {
        logger.logEngine("Initializing Engine...")
        splashScreen.init()

        engineState.set(EngineState.LOADING)

        initRenderSystem()

        val scene = initScene()

        // Defer default scene creation until project is loaded
        projectManager.onProjectLoaded = {
            createDefaultSceneIfNew(scene)
            projectManager.onProjectLoaded = null
        }

        engineState.set(EngineState.RUNNING)
        splashScreen.loadingProgress.set(1.0f)
        logger.logEngine("Engine initialization complete.")

        sceneManager.openScene(scene, forceSingle = true)
    }

    private suspend fun initRenderSystem() {
        logger.logEngine("Initializing render system...")
        splashScreen.increaseLoadingProgress("Initializing Render System...", 0f)

        renderer.initialize()
        renderer.useFbo = true

        logger.logEngine("Renderer initialized.")
    }

    private suspend fun initScene(): Scene {
        sceneInitializer.onProgress = { progress, message ->
            splashScreen.increaseLoadingProgress(message, progress)
        }
        val scene = Scene("LevelEditorScene", sceneInitializer)
        scene.init()
        return scene
    }

    /**
     * Create and save the default scene with prefabs if it doesn't already exist.
     * Called after a project is loaded/created.
     */
    private fun createDefaultSceneIfNew(scene: Scene) {
        val projectDir = projectManager.getProjectDirectory() ?: return
        val defaultSceneFile = File(projectDir, "Scenes/main.scene")

        // Already exists — nothing to do
        if (defaultSceneFile.exists()) {
            return
        }

        logger.logEditor("Creating default scene with prefabs...")

        // Ensure parent directory exists
        defaultSceneFile.parentFile?.mkdirs()

        // Spawn prefabs synchronously into the scene
        prefabsGenerator.spawnSkateboardSync(scene)
        prefabsGenerator.spawnSkaterSync(scene = scene)
        prefabsGenerator.spawnFloorSync(scene)

        logger.logEditor("Spawned ${scene.gameObjectManager.gameObjects.size} objects")

        // Set the level path and save
        scene.sceneData.levelPath = defaultSceneFile.absolutePath
        levelManager.saveToFile(scene, defaultSceneFile.absolutePath)

        logger.logEditor("Default scene saved to ${defaultSceneFile.absolutePath}")
    }
}
