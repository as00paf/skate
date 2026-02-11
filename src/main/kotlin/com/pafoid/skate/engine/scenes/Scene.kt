package com.pafoid.skate.engine.scenes

import com.pafoid.skate.engine.editor.logs.LogLevel
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.physics3d.IPhysics3D
import com.pafoid.skate.engine.physics3d.Physics3D
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.DirectionalLight
import com.pafoid.skate.engine.render.Light
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.Transform
import com.pafoid.skate.engine.utils.serialization.Serializer
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyfd.TinyFileDialogs

class Scene(
    private val initializer: SceneInitializer,
    val serializer: Serializer,
    val logger: LoggerService,
    val camera: Camera = Camera()
) {

    var light: Light = Light(Vector3f(0f, 0f, 20f))
    var sun: DirectionalLight = DirectionalLight()
    var moon: DirectionalLight = DirectionalLight()
    var useSun: Boolean = true
    var useAmbient: Boolean = true
    var timeOfDay: Float = 12.0f // 0.0 to 24.0, 12.0 is noon
    var ambientLight: Vector3f = Vector3f(0.3f, 0.3f, 0.35f) // Brighter ambient light
    var skyColor: Vector3f = Vector3f(0.6f, 0.7f, 0.9f)
    var skyTint: Vector3f = Vector3f(1.0f, 1.0f, 1.0f)
    var skyExposure: Float = 1.0f
    var skyRotation: Float = 0.0f
    var fogColor: Vector3f = Vector3f(0.8f, 0.8f, 0.8f)
    var fogDensity: Float = 0.0f
    var fogGradient: Float = 1.5f
    var timeScale: Float = 1.0f
    
    var levelPath: String = "level.json"

    val gameObjects = mutableListOf<GameObject>()
    val pendingObjects = mutableListOf<GameObject>()
    val physics3d: IPhysics3D = Physics3D()

    private var isRunning = false

    suspend fun init() {
        initializer.loadResources(this)
        initializer.init(this)
    }

    fun start() {
        isRunning = true
        gameObjects.forEach { go ->
            go.start()
            physics3d.add(go)
        }
        
        // Flush any objects added during startup
        while (pendingObjects.isNotEmpty()) {
            val toAdd = mutableListOf<GameObject>()
            toAdd.addAll(pendingObjects)
            pendingObjects.clear()
            
            toAdd.forEach { go ->
                gameObjects.add(go)
                go.start()
                physics3d.add(go)
            }
        }
    }

    fun addGameObjectToScene(gameObject: GameObject) {
        if (!isRunning) {
            gameObjects.add(gameObject)
        } else {
            pendingObjects.add(gameObject)
        }
    }

    fun removeGameObject(gameObject: GameObject) {
        gameObjects.remove(gameObject)
        pendingObjects.remove(gameObject)
        physics3d.remove(gameObject)
    }

    fun getGameObject(id: Int): GameObject? {
        return gameObjects.firstOrNull { it.getUid() == id }
    }

    fun getGameObject(name: String): GameObject? {
        return gameObjects.firstOrNull { it.name == name }
    }

    fun editorUpdate(dt: Float) {
        camera.update(dt)

        val iterator = gameObjects.iterator()
        while (iterator.hasNext()) {
            val go = iterator.next()
            if (go.isDead()) {
                physics3d.remove(go)
                iterator.remove()
                continue
            }
            go.editorUpdate(dt)
            physics3d.add(go)
        }

        pendingObjects.forEach { gameObject ->
            gameObjects.add(gameObject)
            gameObject.start()
            physics3d.add(gameObject)
        }

        pendingObjects.clear()
    }

    fun update(dt: Float) {
        val scaledDt = dt * timeScale
        camera.update(scaledDt) 
        physics3d.update(scaledDt)

        val iterator = gameObjects.iterator()
        while (iterator.hasNext()) {
            val go = iterator.next()
            if (go.isDead()) {
                physics3d.remove(go)
                iterator.remove()
                continue
            }
            go.update(scaledDt)
        }

        pendingObjects.forEach { gameObject ->
            gameObjects.add(gameObject)
            gameObject.start()
            physics3d.add(gameObject)
        }

        pendingObjects.clear()
    }

    fun imgui() {
        initializer.imgui()
    }

    fun createGameObject(name: String): GameObject {
        val go = GameObject(name)
        go.addComponent(Transform())
        return go
    }

    fun save() {
        saveToFile(levelPath)
    }

    fun saveAs() {
        val filter = MemoryUtil.memUTF8("*.json")
        val filters = MemoryUtil.memAllocPointer(1)
        filters.put(0, filter)
        
        val path = TinyFileDialogs.tinyfd_saveFileDialog("Save Level", levelPath, filters, "JSON Files")
        
        MemoryUtil.memFree(filters)
        MemoryUtil.memFree(filter)

        if (path != null) {
            levelPath = path
            saveToFile(path)
        }
    }

    private fun saveToFile(path: String) {
        try {
            val writer = FileWriter(path)
            val data = LevelData(
                gameObjects = gameObjects.filter { it.doSerialization() },
                ambientLight = ambientLight,
                useAmbient = useAmbient,
                useSun = useSun,
                timeOfDay = timeOfDay,
                skyColor = skyColor,
                skyTint = skyTint,
                skyExposure = skyExposure,
                skyRotation = skyRotation,
                sunDirection = sun.direction,
                sunColor = sun.color,
                moonDirection = moon.direction,
                moonColor = moon.color,
                lightPosition = light.position,
                gravity = physics3d.getGravity(),
                fogColor = fogColor,
                fogDensity = fogDensity,
                fogGradient = fogGradient
            )
            writer.write(serializer.encode(data))
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun load() {
        loadFromFile(levelPath)
    }

    fun open() {
        val filter = MemoryUtil.memUTF8("*.json")
        val filters = MemoryUtil.memAllocPointer(1)
        filters.put(0, filter)
        
        val path = TinyFileDialogs.tinyfd_openFileDialog("Open Level", levelPath, filters, "JSON Files", false)
        
        MemoryUtil.memFree(filters)
        MemoryUtil.memFree(filter)

        if (path != null) {
            levelPath = path
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
            gameObjects.forEach { it.destroy() }
            gameObjects.clear()
            pendingObjects.clear()
            
            val data: LevelData = serializer.decode(inFile)
            
            this.ambientLight.set(data.ambientLight)
            this.useAmbient = data.useAmbient
            this.useSun = data.useSun
            this.timeOfDay = data.timeOfDay
            this.skyColor.set(data.skyColor)
            this.skyTint.set(data.skyTint)
            this.skyExposure = data.skyExposure
            this.skyRotation = data.skyRotation
            this.sun.direction.set(data.sunDirection)
            this.sun.color.set(data.sunColor)
            this.moon.direction.set(data.moonDirection)
            this.moon.color.set(data.moonColor)
            this.light.position.set(data.lightPosition)
            this.physics3d.setGravity(data.gravity)
            this.fogColor.set(data.fogColor)
            this.fogDensity = data.fogDensity
            this.fogGradient = data.fogGradient
            
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
        gameObjects.forEach { it.destroy() }
    }
}