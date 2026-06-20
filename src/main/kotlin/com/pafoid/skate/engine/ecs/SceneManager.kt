package com.pafoid.skate.engine.ecs

import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.logEditor
import com.pafoid.skate.engine.data.LogLevel
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.events.SceneAction
import java.io.File

class SceneManager(
    private val resourceManager: ResourceManager,
    private val eventSystem: EventSystem,
    private val sceneSerializer: SceneSerializer,
    private val systemManager: SystemManager,
    private val logger: LoggerService,
) {

    val openScenes = mutableListOf<Scene>()
    var activeSceneIndex: Int = -1

    val currentScene: Scene?
        get() = openScenes.getOrNull(activeSceneIndex)

    fun openScene(scene: Scene, forceSingle: Boolean = false) {
        if (forceSingle) {
            closeAllScenes()
        }

        logger.log("Loading scene: ${scene.name}", LogLevel.INFO)
        openScenes.add(scene)
        systemManager.loadScene(scene)
        eventSystem.publish(SceneAction.Opened(scene))
        switchScene(scene)

        logger.log("Scene ${scene.name} loaded and started.", LogLevel.INFO)
    }

    fun openScenes(scenes: List<Scene>) {
        scenes.forEach { scene ->
            logger.log("Loading scenes: ${scene.name}", LogLevel.INFO)
            openScenes.add(scene)
        }
        scenes.first().let { scene ->
            systemManager.loadScene(scene)
            eventSystem.publish(SceneAction.Opened(scene))
            switchScene(scene)
        }
    }

    fun switchScene(scene: Scene) {
        val sceneIndex = openScenes.indexOf(scene)
        if (sceneIndex < 0) return

        activeSceneIndex = sceneIndex
        logger.log("Switched to scene: ${currentScene?.name}", LogLevel.ACTION)
        eventSystem.publish(SceneAction.Changed)
    }

    fun closeScene(scene: Scene) {
        val index = openScenes.indexOf(scene)
        if (index < 0) return

        val sceneToClose = openScenes[index]
        if (sceneToClose.isDirty) {
            logger.log("Warning: Closing unsaved scene ${sceneToClose.name}", LogLevel.WARN)
            // TODO: Prompt the user for confirmation.
        }

        logger.log("Destroying scene: ${sceneToClose.name}", LogLevel.ACTION)

        eventSystem.publish(SceneAction.Closing(scene))
        sceneToClose.destroyScene()
        openScenes.removeAt(index)
        eventSystem.publish(SceneAction.Closed(scene))

        // Adjust active index
        if (openScenes.isEmpty()) {
            activeSceneIndex = -1
            logger.log("All scenes closed. Clearing resource cache.", LogLevel.INFO)
            resourceManager.clear(preserveNonProjectAssets = true)
        } else if (activeSceneIndex >= index) {
            activeSceneIndex = (activeSceneIndex - 1).coerceAtLeast(0)
            switchScene(openScenes[activeSceneIndex])
        }
    }

    fun destroy() {
        openScenes.forEach { it.destroyScene() }
        openScenes.clear()
        activeSceneIndex = -1
        resourceManager.clear()
    }

    fun renameScene(scene: Scene, newName: String, dirPath: String): Boolean {
        if (!openScenes.contains(scene)) return false
        if (newName.isBlank()) return false

        scene.name = newName
        saveScene(scene, dirPath)
        logger.log("Scene renamed: '${scene.name}'", LogLevel.ACTION)
        return true
    }

    fun closeOtherScenes(keepScene: Scene) {
        if (!openScenes.contains(keepScene)) return

        val scenesToClose = openScenes.filter { it != keepScene }
        scenesToClose.forEach { closeScene(it) }
        switchScene(keepScene)
    }

    fun closeAllScenes() {
        val scenesToClose = openScenes.toList()
        scenesToClose.forEach { closeScene(it) }
    }

    fun createNewScene(name: String, dirPath: String): Scene {
        val newScene = Scene(name)
        sceneSerializer.save(newScene, dirPath)
        logger.log("Scene created: '$name'", LogLevel.ACTION)
        return newScene
    }

    fun saveScene(scene: Scene, dirPath: String): Boolean {
        return sceneSerializer.save(scene, dirPath)
    }

    fun deleteScene(projectPath: String, scene: Scene): Boolean {
        if (!openScenes.contains(scene)) return false
        val filePath = projectPath + "/" + scene.name + ".scene"
        val file = File(filePath)
        if (file.exists()) {
            file.delete()
            logger.logEditor("Deleted scene file: $filePath")
        }

        closeScene(scene)
        return true
    }
}
