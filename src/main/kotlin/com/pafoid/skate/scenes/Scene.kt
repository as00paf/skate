package scenes

import com.google.gson.GsonBuilder
import components.Component
import components.ComponentDeserializer
import marki.Camera
import marki.GameObject
import marki.GameObjectSerializer
import marki.Transform
import marki.renderer.Renderer
import org.joml.Vector2f
import physics2d.Physics2d
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class Scene(private val initializer: SceneInitializer, val camera: Camera = Camera(Vector2f(0f, 0f))) {

    val gameObjects = mutableListOf<GameObject>()
    val pendingObjects = mutableListOf<GameObject>()
    val renderer = Renderer()
    val physics2d = Physics2d()

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
            renderer.add(go)
            physics2d.add(go)
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

    fun <T> getGameObjectWith(clazz: Class<T>): GameObject? {
        return gameObjects.firstOrNull { it.getComponent(clazz) != null }
    }

    fun editorUpdate(dt: Float) {
        camera.adjustProjection()

        var i = 0
        while (i < gameObjects.size) {
            val go = gameObjects[i]
            go.editorUpdate(dt)
            if (go.isDead()) {
                gameObjects.removeAt(i)
                renderer.destroyGameObject(go)
                physics2d.destroyGameObject(go)
                i--
            }
            i++
        }

        pendingObjects.forEach { gameObject ->
            gameObjects.add(gameObject)
            gameObject.start()
            renderer.add(gameObject)
            physics2d.add(gameObject)
        }

        pendingObjects.clear()
    }


    fun update(dt: Float) {
        camera.adjustProjection()
        physics2d.update(dt)

        var i = 0
        while (i < gameObjects.size) {
            val go = gameObjects[i]
            go.update(dt)
            if (go.isDead()) {
                gameObjects.removeAt(i)
                renderer.destroyGameObject(go)
                physics2d.destroyGameObject(go)
                i--
            }
            i++
        }

        pendingObjects.forEach { gameObject ->
            gameObjects.add(gameObject)
            gameObject.start()
            renderer.add(gameObject)
            physics2d.add(gameObject)
        }

        pendingObjects.clear()
    }

    fun render() {
        this.renderer.render()
    }

    fun imgui() {
        initializer.imgui()
    }

    fun createGameObject(name: String): GameObject {
        val go = GameObject(name)
        go.addComponent(Transform())
        go.transform = go.getComponent(Transform::class.java)!!
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