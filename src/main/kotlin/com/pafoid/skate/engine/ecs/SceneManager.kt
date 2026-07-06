package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.assets.database.AssetGuid
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.logEditor
import com.pafoid.skate.engine.data.LogLevel
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.events.SceneAction
import com.pafoid.skate.engine.getComponent
import java.io.File

class SceneManager(
    private val resourceManager: ResourceManager,
    private val eventSystem: EventSystem,
    private val serializer: Serializer,
    private val systemManager: SystemManager,
    private val logger: LoggerService,
    private val assetDatabase: AssetDatabase,
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
                resolveAnimatorPathsForObject(obj)
            } catch (e: Exception) {
                logger.log("Error while loading object: ${obj.name}. ${e.message}", LogLevel.ERROR)
            }
        }
    }

    private fun resolveObjectReferences(obj: GameObject) {
        obj.getComponent<RenderComponent>()?.let { rc ->
            if (rc.model == null && rc.modelGuid.isNotBlank()) {
                try {
                    val asset = assetDatabase.getByGuid(AssetGuid(rc.modelGuid))
                    val modelPath = asset?.absoluteSourcePath ?: rc.modelGuid
                    val file = File(modelPath)
                    if (file.exists()) {
                        rc.model = resourceManager.loadModelSync(modelPath)
                        logger.log("Resolved model for '${obj.name}': $modelPath", LogLevel.INFO)
                    } else {
                        logger.log("WARNING: Model path does not exist for '${obj.name}': $modelPath", LogLevel.WARN)
                    }
                } catch (e: Exception) {
                    logger.log("Error resolving model for '${obj.name}': ${e.message}", LogLevel.ERROR)
                }
            }

            // Resolve texture GUIDs and apply them to the model's materials
            rc.model?.let { model ->
                model.mesh.forEach { meshPart ->
                    val mat = meshPart.material

                    if (rc.albedoTextureGuid.isNotBlank()) {
                        try {
                            val asset = assetDatabase.getByGuid(AssetGuid(rc.albedoTextureGuid))
                            val texPath = asset?.absoluteSourcePath ?: rc.albedoTextureGuid
                            val file = File(texPath)
                            if (file.exists()) {
                                mat.baseColorTexture = resourceManager.loadTextureSync(texPath)
                            } else {
                                logger.log(
                                    "WARNING: Albedo texture path does not exist for '${obj.name}': $texPath",
                                    LogLevel.WARN
                                )
                            }
                        } catch (e: Exception) {
                            logger.log("Error resolving albedo texture for '${obj.name}': ${e.message}", LogLevel.ERROR)
                        }
                    }

                    if (rc.normalMapGuid.isNotBlank()) {
                        try {
                            val asset = assetDatabase.getByGuid(AssetGuid(rc.normalMapGuid))
                            val texPath = asset?.absoluteSourcePath ?: rc.normalMapGuid
                            val file = File(texPath)
                            if (file.exists()) {
                                mat.normalMap = resourceManager.loadTextureSync(texPath)
                            } else {
                                logger.log(
                                    "WARNING: Normal map path does not exist for '${obj.name}': $texPath",
                                    LogLevel.WARN
                                )
                            }
                        } catch (e: Exception) {
                            logger.log("Error resolving normal map for '${obj.name}': ${e.message}", LogLevel.ERROR)
                        }
                    }

                    if (rc.metallicRoughnessGuid.isNotBlank()) {
                        try {
                            val asset = assetDatabase.getByGuid(AssetGuid(rc.metallicRoughnessGuid))
                            val texPath = asset?.absoluteSourcePath ?: rc.metallicRoughnessGuid
                            val file = File(texPath)
                            if (file.exists()) {
                                mat.metallicRoughnessTexture = resourceManager.loadTextureSync(texPath)
                            } else {
                                logger.log(
                                    "WARNING: Metallic roughness map path does not exist for '${obj.name}': $texPath",
                                    LogLevel.WARN
                                )
                            }
                        } catch (e: Exception) {
                            logger.log(
                                "Error resolving metallic roughness map for '${obj.name}': ${e.message}",
                                LogLevel.ERROR
                            )
                        }
                    }
                }
            }
        }

        obj.getComponent<Animator>()?.let { animator ->
            animator.loadAnimationsFromPaths(resourceManager)
        }

        obj.children.forEach { child ->
            resolveObjectReferences(child)
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
            prepareSceneForSaving(scene)
            file.writeText(serializer.encode(scene))
            scene.isDirty = false
            logger.logEditor("Scene saved to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            logger.logEditor("Failed to save scene to ${file.absolutePath}: ${e.message}", LogLevel.ERROR)
            false
        }
    }

    fun prepareSceneForSaving(scene: Scene) {
        scene.gameObjects.forEach { obj ->
            prepareObjectForSaving(obj)
        }
    }

    // TODO: this should not be needed, these values should be populated at appropriate time instead
    private fun prepareObjectForSaving(obj: GameObject) {
        obj.getComponent<RenderComponent>()?.let { rc ->
            val model = rc.model
            if (model != null) {
                // Resolve modelGuid if blank
                if (rc.modelGuid.isBlank()) {
                    val modelPath = model.sourcePath
                    if (modelPath != null) {
                        val modelFile = File(modelPath)
                        val absolutePath =
                            if (modelFile.isAbsolute) modelFile.absolutePath else File(modelPath).absolutePath
                        val asset = assetDatabase.getByAbsolutePath(absolutePath)
                        rc.modelGuid = asset?.guid?.value ?: absolutePath
                    }
                }

                // Resolve texture GUIDs from model's materials
                model.mesh.forEach { meshPart ->
                    val mat = meshPart.material

                    if (mat.baseColorTexture != null && rc.albedoTextureGuid.isBlank()) {
                        val texPath = mat.baseColorTexture?.filePath ?: mat.baseColorPath ?: ""
                        if (texPath.isNotBlank()) {
                            val texFile = File(texPath)
                            val texAbsolutePath =
                                if (texFile.isAbsolute) texFile.absolutePath else File(texPath).absolutePath
                            val texAsset = assetDatabase.getByAbsolutePath(texAbsolutePath)
                            rc.albedoTextureGuid = texAsset?.guid?.value ?: texAbsolutePath
                        }
                    }

                    if (mat.normalMap != null && rc.normalMapGuid.isBlank()) {
                        val texPath = mat.normalMap?.filePath ?: mat.normalMapPath ?: ""
                        if (texPath.isNotBlank()) {
                            val texFile = File(texPath)
                            val texAbsolutePath =
                                if (texFile.isAbsolute) texFile.absolutePath else File(texPath).absolutePath
                            val texAsset = assetDatabase.getByAbsolutePath(texAbsolutePath)
                            rc.normalMapGuid = texAsset?.guid?.value ?: texAbsolutePath
                        }
                    }

                    if (mat.metallicRoughnessTexture != null && rc.metallicRoughnessGuid.isBlank()) {
                        val texPath = mat.metallicRoughnessTexture?.filePath ?: mat.metallicRoughnessPath ?: ""
                        if (texPath.isNotBlank()) {
                            val texFile = File(texPath)
                            val texAbsolutePath =
                                if (texFile.isAbsolute) texFile.absolutePath else File(texPath).absolutePath
                            val texAsset = assetDatabase.getByAbsolutePath(texAbsolutePath)
                            rc.metallicRoughnessGuid = texAsset?.guid?.value ?: texAbsolutePath
                        }
                    }
                }
            }
        }

        resolveAnimatorPathsForObject(obj)

        obj.children.forEach { child ->
            prepareObjectForSaving(child)
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
