package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.editor.logs.LogLevel
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.Physics3D
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.utils.serialization.Serializer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class Scene(
    private val initializer: SceneInitializer,
    private val serializer: Serializer,
    private val logger: LoggerService,
    val camera: Camera = Camera()
) {

    var sceneData: SceneData = SceneData()
    val physics3d: IPhysics3D = Physics3D()
    val gameObjectManager: GameObjectManager = GameObjectManager(physics3d)

    var isRunning: Boolean = false

    suspend fun init() {
        initializer.loadResources(this)
        initializer.init(this)
    }

    fun start() {
        isRunning = true
        gameObjectManager.gameObjects.forEach { go ->
            go.start()
            physics3d.add(go)
        }

        // Flush any objects added during startup
        while (gameObjectManager.pendingObjects.isNotEmpty()) {
            val toAdd = mutableListOf<GameObject>()
            toAdd.addAll(gameObjectManager.pendingObjects)
            gameObjectManager.pendingObjects.clear()

            toAdd.forEach { go ->
                gameObjectManager.gameObjects.add(go)
                go.start()
                physics3d.add(go)
            }
        }
    }

    fun editorUpdate(dt: Float) {
        camera.update(dt)
        gameObjectManager.editorUpdate(dt)
    }

    fun update(dt: Float) {
        val scaledDt = dt * sceneData.timeScale
        camera.update(scaledDt)
        physics3d.update(scaledDt)
        gameObjectManager.update(scaledDt)
    }

    fun imgui() {
        initializer.imgui()
    }

    fun save() {
        saveToFile(sceneData.levelPath)
    }

    fun saveAs() {
        val filter = MemoryUtil.memUTF8("*.json")
        val filters = MemoryUtil.memAllocPointer(1)
        filters.put(0, filter)

        val path = TinyFileDialogs.tinyfd_saveFileDialog("Save Level", sceneData.levelPath, filters, "JSON Files")

        MemoryUtil.memFree(filters)
        MemoryUtil.memFree(filter)

        if (path != null) {
            sceneData.levelPath = path
            saveToFile(path)
        }
    }

    private fun saveToFile(path: String) {
        try {
            val writer = FileWriter(path)
            val data = LevelData(
                gameObjects = gameObjectManager.gameObjects.filter { it.doSerialization() },
                sceneData = sceneData,
                gravity = physics3d.getGravity(),
            )
            writer.write(serializer.encode(data))
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun load() {
        loadFromFile(sceneData.levelPath)
    }

    fun open() {
        val filter = MemoryUtil.memUTF8("*.json")
        val filters = MemoryUtil.memAllocPointer(1)
        filters.put(0, filter)

        val path = TinyFileDialogs.tinyfd_openFileDialog("Open Level", sceneData.levelPath, filters, "JSON Files", false)

        MemoryUtil.memFree(filters)
        MemoryUtil.memFree(filter)

        if (path != null) {
            sceneData.levelPath = path
            loadFromFile(path)
        }
    }

    private fun loadFromFile(path: String) {
        var inFile = ""
        try {
            inFile = String(Files.readAllBytes(Paths.get(path)))
        } catch (e: IOException) {
            logger.logEngine("Could not find $path", LogLevel.ERROR)
        }

        if (inFile.isNotBlank()) {
            // Clear current scene first if loading a new one
            gameObjectManager.gameObjects.forEach { it.destroy() }
            gameObjectManager.gameObjects.clear()
            gameObjectManager.pendingObjects.clear()

            val data: LevelData = serializer.decode(inFile)

            this.sceneData = data.sceneData
            this.gameObjectManager.gameObjects.addAll(data.gameObjects)
            this.physics3d.setGravity(data.gravity)

            var maxGoId = -1
            var maxCompId = -1

            data.gameObjects.forEach { obj ->
                addGameObjectToScene(obj)

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
        }
    }

    fun destroy() {
        gameObjectManager.destroy()
    }

}