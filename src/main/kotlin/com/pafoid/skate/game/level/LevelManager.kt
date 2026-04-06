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
        val filter = MemoryUtil.memUTF8("*.scene")
        val filters = MemoryUtil.memAllocPointer(1)
        filters.put(0, filter)

        val path = try {
            TinyFileDialogs.tinyfd_saveFileDialog(scene.sceneData.levelPath, "Save Level", filters, "Scene Files")
        } finally {
            MemoryUtil.memFree(filter)
            MemoryUtil.memFree(filters)
        }

        if (path != null) {
            scene.sceneData.levelPath = path
            saveToFile(scene, path)
        }
    }

    fun saveToFile(scene: Scene, path: String) {
        try {
            // Ensure parent directory exists
            java.io.File(path).parentFile?.mkdirs()

            val data = LevelData(
                gameObjects = scene.gameObjectManager.gameObjects.filter { it.doSerialization() },
                sceneData = scene.sceneData,
                levelPath = path
            )

            java.io.FileWriter(path).use { writer ->
                writer.write(serializer.encode(data))
            }

            scene.isDirty = false
            logger.logEditor("Level saved to $path")
        } catch (e: IOException) {
            logger.logEngine("Failed to save level to $path: ${e.message}", LogLevel.ERROR)
        }
    }

    fun load(scene: Scene) {
        loadFromFile(scene, scene.sceneData.levelPath)
    }

    fun open(scene: Scene) {
        val filter = MemoryUtil.memUTF8("*.scene")
        val filters = MemoryUtil.memAllocPointer(1)
        filters.put(0, filter)

        val path = try {
            TinyFileDialogs.tinyfd_openFileDialog("Open Level", scene.sceneData.levelPath, filters, "Scene Files", false)
        } finally {
            MemoryUtil.memFree(filter)
            MemoryUtil.memFree(filters)
        }

        if (path != null) {
            scene.sceneData.levelPath = path
            loadFromFile(scene, path)
        }
    }

    fun loadFromFile(scene: Scene, path: String) {
        var inFile = ""
        try {
            inFile = String(Files.readAllBytes(Paths.get(path)))
        } catch (e: IOException) {
            logger.logEngine("Could not find $path", LogLevel.ERROR)
            return
        }

        if (inFile.isBlank()) return

        // Deserialize to temporary data structure first (atomic load)
        val data: LevelData = try {
            serializer.decode(inFile)
        } catch (e: Exception) {
            logger.logEngine("Failed to deserialize scene from $path: ${e.message}", LogLevel.ERROR)
            return
        }

        // Only clear the scene after successful deserialization
        scene.gameObjectManager.gameObjects.forEach { it.destroy() }
        scene.gameObjectManager.gameObjects.clear()
        scene.gameObjectManager.pendingObjects.clear()

        scene.sceneData = data.sceneData
        scene.sceneData.levelPath = path

        var maxGoId = -1
        var maxCompId = -1

        data.gameObjects.forEach { obj ->
            // Initialize deserialized components with their parent object
            // (JSON deserialization creates components without calling init)
            obj.getAllComponents().forEach { it.init(obj) }

            // Restore animation references after init (needs Koin injections)
            obj.getAllComponents().filterIsInstance<com.pafoid.skate.engine.ecs.components.Animator>()
                .forEach { it.reloadAnimations() }

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
            // Migrate legacy scenes: create GUIDs for models that don't have them
            migrateLegacyModels(scene)
        }

        logger.logEditor("Level loaded from $path")
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

    /**
     * Migrate legacy scene models to GUID-based references.
     *
     * For any RenderComponent that has a model but no modelGuid,
     * this method attempts to find the model's source path via
     * material texture paths and create/find the corresponding GUID.
     * The scene is marked dirty so it gets re-saved with GUID references.
     */
    private fun migrateLegacyModels(scene: Scene) {
        var migrationCount = 0
        scene.gameObjectManager.gameObjects.forEach { obj ->
            migrateObjectModels(obj)?.let { migrationCount += it }
            obj.children.forEach { child ->
                migrateObjectModels(child)?.let { migrationCount += it }
            }
        }
        if (migrationCount > 0) {
            logger.logEditor("Migrated $migrationCount legacy model(s) to GUID references in ${scene.sceneData.levelPath}")
            scene.isDirty = true
        }
    }

    private fun migrateObjectModels(obj: GameObject): Int? {
        val rc = obj.getComponent<RenderComponent>() ?: return null
        val model = rc.model ?: return null
        if (rc.modelGuid.isNotBlank()) return null

        // Try to find model path from material texture paths
        val modelPath = model.mesh.firstOrNull()?.material?.baseColorTexture?.filePath
            ?: model.mesh.firstOrNull()?.material?.baseColorPath
            ?: return null

        val sourceFile = java.io.File(modelPath)
        if (!sourceFile.exists()) return null

        // Try to find existing asset by path
        val absolutePath = sourceFile.absolutePath
        var asset = assetDatabase?.getByAbsolutePath(absolutePath)

        if (asset == null) {
            // Create .meta file for this model
            assetDatabase?.createMeta(sourceFile)?.getOrNull()?.let { guid ->
                asset = assetDatabase?.getByGuid(guid)
            }
        }

        if (asset != null) {
            rc.modelGuid = asset.guid.value
            return 1
        }

        return null
    }
}
