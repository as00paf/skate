package com.pafoid.skate.game.level

import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.assets.database.AssetGuid
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.scene.addGameObjectToScene
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class LevelManager(
    private val serializer: Serializer,
    private val logger: LoggerService,
    private val resourceManager: ResourceManager,
    private val assetDatabase: AssetDatabase? = null
) {

    fun save(scene: Scene) {
        saveToFile(scene, scene.sceneData.levelPath)
    }

    fun saveAs(scene: Scene) {
        val filter = MemoryUtil.memUTF8("*.json")
        val filters = MemoryUtil.memAllocPointer(1)
        filters.put(0, filter)

        val path = TinyFileDialogs.tinyfd_saveFileDialog("Save Level", scene.sceneData.levelPath, filters, "JSON Files")

        MemoryUtil.memFree(filters)
        MemoryUtil.memFree(filter)

        if (path != null) {
            scene.sceneData.levelPath = path
            saveToFile(scene, path)
        }
    }

    private fun saveToFile(scene: Scene, path: String) {
        try {
            val writer = FileWriter(path)
            val data = LevelData(
                gameObjects = scene.gameObjectManager.gameObjects.filter { it.doSerialization() },
                sceneData = scene.sceneData,
                levelPath = path
            )
            writer.write(serializer.encode(data))
            writer.close()
            scene.isDirty = false
            logger.logEditor("Level saved to $path")
        } catch (e: IOException) {
            e.printStackTrace()
            logger.logEngine("Failed to save level to $path", LogLevel.ERROR)
        }
    }

    fun load(scene: Scene) {
        loadFromFile(scene, scene.sceneData.levelPath)
    }

    fun open(scene: Scene) {
        val filter = MemoryUtil.memUTF8("*.json")
        val filters = MemoryUtil.memAllocPointer(1)
        filters.put(0, filter)

        val path = TinyFileDialogs.tinyfd_openFileDialog("Open Level", scene.sceneData.levelPath, filters, "JSON Files", false)

        MemoryUtil.memFree(filters)
        MemoryUtil.memFree(filter)

        if (path != null) {
            scene.sceneData.levelPath = path
            loadFromFile(scene, path)
        }
    }

    private fun loadFromFile(scene: Scene, path: String) {
        var inFile = ""
        try {
            inFile = String(Files.readAllBytes(Paths.get(path)))
        } catch (e: IOException) {
            logger.logEngine("Could not find $path", LogLevel.ERROR)
            return
        }

        if (inFile.isNotBlank()) {
            // Clear current scene first if loading a new one
            scene.gameObjectManager.gameObjects.forEach { it.destroy() }
            scene.gameObjectManager.gameObjects.clear()
            scene.gameObjectManager.pendingObjects.clear()

            val data: LevelData = serializer.decode(inFile)

            scene.sceneData = data.sceneData

            // Ensure the correct path is kept even if the loaded file has an old one
            scene.sceneData.levelPath = path

            var maxGoId = -1
            var maxCompId = -1

            data.gameObjects.forEach { obj ->
                scene.addGameObjectToScene(obj)

                obj.getAllComponents().forEach { component ->
                    if (component.getUid() > maxCompId) {
                        maxCompId = component.getUid()
                    }
                }

                if (obj.getUid() > maxGoId) {
                    maxGoId = obj.getUid()
                }
            }

            maxGoId++
            maxCompId++
            GameObject.init(maxGoId)
            Component.init(maxCompId)

            // Resolve GUID references for RenderComponents
            if (assetDatabase != null) {
                resolveAssetReferences(scene)
            }

            logger.logEditor("Level loaded from $path")
        }
    }

    /**
     * Resolve GUID references in RenderComponents after scene deserialization.
     * Loads models by GUID and assigns them to the RenderComponent's model field.
     */
    private fun resolveAssetReferences(scene: Scene) {
        scene.gameObjectManager.gameObjects.forEach { obj ->
            resolveObjectReferences(obj)
            obj.children.forEach { child -> resolveObjectReferences(child) }
        }
    }

    private fun resolveObjectReferences(obj: GameObject) {
        obj.getComponent<RenderComponent>()?.let { rc ->
            if (rc.modelGuid.isNotBlank() && rc.model == null) {
                try {
                    val guid = AssetGuid.parse(rc.modelGuid)
                    val asset = assetDatabase?.getByGuid(guid)
                    if (asset != null) {
                        rc.model = resourceManager.loadModelSync(asset.absoluteSourcePath)
                    } else {
                        logger.logEditor("Asset not found for GUID: ${rc.modelGuid} on ${obj.name}")
                    }
                } catch (e: Exception) {
                    logger.logEditor("Failed to resolve model GUID ${rc.modelGuid} on ${obj.name}: ${e.message}")
                }
            }
        }
    }
}
