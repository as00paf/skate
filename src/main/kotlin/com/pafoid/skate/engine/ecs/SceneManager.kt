package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.events.SceneAction
import com.pafoid.skate.engine.getComponent
import java.io.File

class SceneManager(
    private val assetsManager: AssetsManager,
    private val eventSystem: EventSystem,
    private val serializer: Serializer,
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
        resolveSceneReferences(scene)
        openScenes.add(scene)
        systemManager.loadScene(scene)
        eventSystem.publish(SceneAction.Opened(scene))
        switchScene(scene)

        logger.log("Scene ${scene.name} loaded and started.", LogLevel.INFO)
    }

    fun openScenes(scenes: List<Scene>) {
        scenes.forEach { scene ->
            logger.log("Loading scenes: ${scene.name}", LogLevel.INFO)
            resolveSceneReferences(scene)
            openScenes.add(scene)
        }
        scenes.first().let { scene ->
            systemManager.loadScene(scene)
            eventSystem.publish(SceneAction.Opened(scene))
            switchScene(scene)
        }
    }

    fun resolveSceneReferences(scene: Scene) {
        scene.gameObjects.forEach { obj ->
            try {
                obj.components.forEach { it.init(obj) }
                resolveObjectReferences(obj)
            } catch (e: Exception) {
                logger.log("Error while loading object: ${obj.name}. ${e.message}", LogLevel.ERROR)
            }
        }
    }

    private fun resolveObjectReferences(obj: GameObject) {
        obj.getComponent<RenderComponent>()?.resolveModelFromPath(assetsManager)
        obj.getComponent<Animator>()?.resolveAnimationsFromPaths(assetsManager, logger)
        obj.getComponent<SpriteRenderer>()?.resolveTextureFromPaths(assetsManager, logger)

        obj.children.forEach { child ->
            resolveObjectReferences(child)
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
        } else if (activeSceneIndex >= index) {
            activeSceneIndex = (activeSceneIndex - 1).coerceAtLeast(0)
            switchScene(openScenes[activeSceneIndex])
        }
    }

    fun destroy() {
        openScenes.forEach { it.destroyScene() }
        openScenes.clear()
        activeSceneIndex = -1
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
        // Ensure directory exists
        val dir = File(dirPath)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$name.scene")
        try {
            file.writeText(serializer.encode(newScene))
            logger.log("Scene created: '$name'", LogLevel.ACTION)
        } catch (e: Exception) {
            logger.logEditor("Failed to create scene file ${file.absolutePath}: ${e.message}")
        }
        return newScene
    }

    fun saveScene(scene: Scene, dirPath: String): Boolean {
        val dir = File(dirPath)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${scene.name}.scene")
        return try {
            file.writeText(serializer.encode(scene))
            scene.isDirty = false
            logger.logEditor("Scene saved to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            logger.logEditor("Failed to save scene to ${file.absolutePath}: ${e.message}", LogLevel.ERROR)
            false
        }
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
