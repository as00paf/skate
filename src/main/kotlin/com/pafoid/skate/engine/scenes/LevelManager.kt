package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.editor.logs.LogLevel
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.utils.serialization.Serializer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class LevelManager(
    private val serializer: Serializer,
    private val logger: LoggerService
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
                gravity = scene.physics3d.getGravity(),
                levelPath = path
            )
            writer.write(serializer.encode(data))
            writer.close()
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
            scene.physics3d.setGravity(data.gravity)
            
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
            
            logger.logEditor("Level loaded from $path")
        }
    }
}
