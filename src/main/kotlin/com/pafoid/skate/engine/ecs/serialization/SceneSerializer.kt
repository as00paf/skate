package com.pafoid.skate.engine.ecs.serialization

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Component
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Wrapper for serializing scene hierarchy.
 * Contains all game objects with their parent-child relationships.
 */
@Serializable
data class SceneDataWrapper(
    val gameObjects: List<GameObject>,
    val rootObjectIds: List<Int>,
    val maxGameObjectId: Int,
    val maxComponentId: Int
)

/**
 * SceneSerializer handles saving and loading scenes to/from JSON files.
 * 
 * Features:
 * - Serializes entire scene hierarchy (GameObject tree)
 * - Preserves parent-child relationships
 * - Handles polymorphic component serialization
 * - Tracks ID counters for proper deserialization
 * 
 * Usage:
 * ```kotlin
 * val serializer = SceneSerializer()
 * serializer.saveScene(scene, "path/to/scene.json")
 * val loadedScene = serializer.loadScene(scene, "path/to/scene.json")
 * ```
 */
class SceneSerializer {
    
    private val serializer = Serializer()
    
    /**
     * Saves the scene to a JSON file.
     * 
     * @param scene The scene to save
     * @param filePath Path to the output JSON file
     * @throws Exception if serialization fails
     */
    fun saveScene(scene: Scene, filePath: String) {
        try {
            // Collect all game objects
            val allGameObjects = collectAllGameObjects(scene)
            
            // Find root objects (objects without parents)
            val rootObjectIds = allGameObjects
                .filter { it.parent == null }
                .map { it.getUid() }
            
            // Get current ID counters
            val maxGameObjectId = GameObject.getIdCounter()
            val maxComponentId = Component.getIdCounter()
            
            // Create wrapper
            val wrapper = SceneDataWrapper(
                gameObjects = allGameObjects,
                rootObjectIds = rootObjectIds,
                maxGameObjectId = maxGameObjectId,
                maxComponentId = maxComponentId
            )
            
            // Serialize to JSON
            val json = serializer.encode(wrapper)
            
            // Write to file
            val file = File(filePath)
            file.parentFile?.mkdirs()
            file.writeText(json)
            
            println("Scene saved to: ${file.absolutePath}")
        } catch (e: Exception) {
            throw Exception("Failed to save scene: ${e.message}", e)
        }
    }
    
    /**
     * Loads a scene from a JSON file.
     * 
     * @param scene The scene to load into (will be populated with loaded objects)
     * @param filePath Path to the JSON file to load
     * @throws Exception if deserialization fails
     */
    fun loadScene(scene: Scene, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                throw Exception("Scene file not found: $filePath")
            }
            
            // Read JSON from file
            val json = file.readText()
            
            // Deserialize wrapper
            val wrapper = serializer.decode<SceneDataWrapper>(json)
            
            // Restore ID counters
            GameObject.init(wrapper.maxGameObjectId)
            Component.init(wrapper.maxComponentId)
            
            // Clear existing objects
            scene.gameObjectManager.gameObjects.clear()
            
            // First pass: initialize all objects and components
            wrapper.gameObjects.forEach { go ->
                go.components.forEach { component ->
                    component.init(go)
                }
            }
            
            // Second pass: restore parent-child relationships
            wrapper.gameObjects.forEach { go ->
                if (go.parent != null) {
                    val parent = wrapper.gameObjects.find { it.getUid() == go.parent?.getUid() }
                    parent?.addChild(go)
                }
            }
            
            // Third pass: add root objects to scene
            wrapper.gameObjects.forEach { go ->
                if (go.parent == null) {
                    // Root object - add directly to scene
                    scene.gameObjectManager.gameObjects.add(go)
                }
            }
            
            println("Scene loaded from: ${file.absolutePath}")
        } catch (e: Exception) {
            throw Exception("Failed to load scene: ${e.message}", e)
        }
    }
    
    /**
     * Collects all game objects in the scene using BFS traversal.
     */
    private fun collectAllGameObjects(scene: Scene): List<GameObject> {
        val allObjects = mutableListOf<GameObject>()
        val queue = ArrayDeque<GameObject>()
        
        // Add root objects
        scene.gameObjectManager.gameObjects.forEach { queue.add(it) }
        
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            allObjects.add(current)
            current.children.forEach { queue.add(it) }
        }
        
        return allObjects
    }
    
    /**
     * Serializes a single GameObject to JSON string.
     * Useful for clipboard operations or prefab system.
     */
    fun serializeGameObject(gameObject: GameObject): String {
        return serializer.encode(gameObject)
    }
    
    /**
     * Deserializes a GameObject from JSON string.
     * Useful for clipboard operations or prefab system.
     */
    fun deserializeGameObject(json: String): GameObject {
        return serializer.decode<GameObject>(json)
    }
}
