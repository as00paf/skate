package com.pafoid.skate.engine.scenes

import com.google.gson.GsonBuilder
import com.pafoid.skate.engine.Transform
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.assets.Cubemap
import com.pafoid.skate.engine.physics3d.Physics3D
import com.pafoid.skate.engine.render.Light
import com.pafoid.skate.engine.render.Renderer
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.ComponentDeserializer
import org.joml.Vector2f
import org.joml.Vector3f
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class Scene(private val initializer: SceneInitializer, val camera: Camera = Camera()) {

    var light: Light = Light(Vector3f(0f, 0f, 20f))
    var ambientLight: Vector3f = Vector3f(0.2f, 0.2f, 0.2f)
    var cubemap: Cubemap? = null
    val gameObjects = mutableListOf<GameObject>()
    val pendingObjects = mutableListOf<GameObject>()
    val physics3d = Physics3D()

    private var isRunning = false
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Component::class.java, ComponentDeserializer())
        .registerTypeAdapter(GameObject::class.java, GameObjectSerializer())
        .enableComplexMapKeySerialization()
        .create()

    fun init() {
        initializer.loadResources(this)
        initializer.init(this)
    }

    fun start() {
        gameObjects.forEach { go ->
            go.start()
            physics3d.add(go)
        }
        isRunning = true
    }

    fun addGameObjectToScene(gameObject: GameObject) {
        if (!isRunning) {
            gameObjects.add(gameObject)
        } else {
            pendingObjects.add(gameObject)
        }
    }

    fun getGameObject(id: Int): GameObject? {
        return gameObjects.firstOrNull { it.getUid() == id }
    }

    fun editorUpdate(dt: Float) {
        camera.update(dt)//camera.adjustProjection()

        var i = 0
        while (i < gameObjects.size) {
            val go = gameObjects[i]
            go.editorUpdate(dt)
            if (go.isDead()) {
                gameObjects.removeAt(i)
                physics3d.remove(go)
                i--
            }
            i++
        }

        pendingObjects.forEach { gameObject ->
            gameObjects.add(gameObject)
            gameObject.start()
            physics3d.add(gameObject)
        }

        pendingObjects.clear()
    }


    fun update(dt: Float) {
        camera.update(dt) 
        physics3d.update(dt)

        var i = 0
        while (i < gameObjects.size) {
            val go = gameObjects[i]
            go.update(dt)
            if (go.isDead()) {
                gameObjects.removeAt(i)
                physics3d.remove(go)
                i--
            }
            i++
        }

        pendingObjects.forEach { gameObject ->
            gameObjects.add(gameObject)
            gameObject.start()
            physics3d.add(gameObject)
        }

        pendingObjects.clear()
    }

    /*fun render() {
        this.renderer.render()
    }*/

    fun imgui() {
        initializer.imgui()
    }

    fun createGameObject(name: String): GameObject {
        val go = GameObject(name)
        go.addComponent(Transform())
        return go
    }

    fun save() {
        try {
            val writer = FileWriter("level.txt")
            writer.write(gson.toJson(gameObjects.filter { it.doSerialization() }))
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun load() {
        var inFile = ""
        try {
            inFile = String(Files.readAllBytes(Paths.get("level.txt")))
        } catch (e: IOException) {
            println("Error: Could not find level.txt")
            //e.printStackTrace()
        }

        if (inFile.isNotBlank()) {
            var maxGoId = -1
            var maxCompId = -1
            val objs: Array<GameObject> = gson.fromJson(inFile, Array<GameObject>::class.java)
            objs.forEach { obj ->
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
            //println(maxGoId)
            //println(maxCompId)
            GameObject.init(maxGoId)
            Component.init(maxCompId)
        }
    }

    fun destroy() {
        gameObjects.forEach { it.destroy() }
    }
}