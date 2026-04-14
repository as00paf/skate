package com.pafoid.skate.editor.project

import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.assets.database.AssetGuid
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.scene.addGameObjectImmediate
import kotlinx.serialization.Serializable
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Handles scene serialization — saving and loading .scene files.
 *
 * Responsible for:
 * - Saving scene state (GameObjects + SceneData) to disk
 * - Loading scene state from disk with atomic deserialization
 * - Resolving asset GUID references for RenderComponents
 * - Resolving animation references for Animator components
 */
class SceneSerializer(
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
            TinyFileDialogs.tinyfd_saveFileDialog(scene.sceneData.levelPath, "Save Scene", filters, "Scene Files")
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
            File(path).parentFile?.mkdirs()

            val data = SceneSaveData(
                gameObjects = scene.gameObjectManager.gameObjects.filter { it.doSerialization() },
                sceneData = scene.sceneData,
                scenePath = path
            )

            FileWriter(path).use { writer ->
                writer.write(serializer.encode(data))
            }

            scene.isDirty = false
            logger.logEditor("Scene saved to $path")
        } catch (e: IOException) {
            logger.logEngine("Failed to save scene to $path: ${e.message}", LogLevel.ERROR)
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
            TinyFileDialogs.tinyfd_openFileDialog("Open Scene", scene.sceneData.levelPath, filters, "Scene Files", false)
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

        val data: SceneSaveData = try {
            serializer.decode(inFile)
        } catch (e: Exception) {
            logger.logEngine("Failed to deserialize scene from $path: ${e.message}", LogLevel.ERROR)
            return
        }

        scene.gameObjectManager.gameObjects.forEach { it.destroy() }
        scene.gameObjectManager.gameObjects.clear()
        scene.gameObjectManager.pendingObjects.clear()

        scene.name = File(path).name
        scene.sceneData = data.sceneData
        scene.sceneData.levelPath = path

        var maxGoId = -1
        var maxCompId = -1

        data.gameObjects.forEach { obj ->
            obj.getAllComponents().forEach { it.init(obj) }

            scene.addGameObjectImmediate(obj)

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

        if (assetDatabase != null) {
            resolveAssetReferences(scene)
        }

        resolveAnimationReferences(scene)

        logger.logEditor("Scene loaded from $path")

        scene.gameObjectManager.gameObjects.forEach { go ->
            val compCount = go.getAllComponents().size
            logger.logEditor("[DIAG]   - ${go.name}: $compCount components")
        }
    }

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
                } catch (e: IllegalArgumentException) {
                    val path = rc.modelGuid
                    val file = File(path)
                    if (file.exists()) {
                        rc.model = resourceManager.loadModelSync(path)
                    } else {
                        logger.logEditor("Model file not found: $path on ${obj.name}")
                    }
                } catch (e: Exception) {
                    logger.logEditor("Failed to resolve model ${rc.modelGuid} on ${obj.name}: ${e.message}")
                }

                if (rc.model != null) {
                    applyTextureGuidsToObject(obj)
                }
            }
        }
    }

    private fun applyTextureGuidsToObject(obj: GameObject) {
        val rc = obj.getComponent<RenderComponent>() ?: return
        val model = rc.model ?: return

        model.mesh.forEach { meshPart ->
            val mat = meshPart.material

            if (rc.albedoTextureGuid.isNotBlank() && mat.baseColorTexture == null) {
                loadTextureFromGuidOrPath(rc.albedoTextureGuid)?.let { tex ->
                    mat.baseColorTexture = tex
                    mat.baseColorPath = tex.filePath
                }
            }
            if (rc.normalMapGuid.isNotBlank() && mat.normalMap == null) {
                loadTextureFromGuidOrPath(rc.normalMapGuid)?.let { tex ->
                    mat.normalMap = tex
                    mat.normalMapPath = tex.filePath
                }
            }
            if (rc.metallicRoughnessGuid.isNotBlank() && mat.metallicRoughnessTexture == null) {
                loadTextureFromGuidOrPath(rc.metallicRoughnessGuid)?.let { tex ->
                    mat.metallicRoughnessTexture = tex
                    mat.metallicRoughnessPath = tex.filePath
                }
            }
            if (rc.aoGuid.isNotBlank() && mat.aoTexture == null) {
                loadTextureFromGuidOrPath(rc.aoGuid)?.let { tex ->
                    mat.aoTexture = tex
                    mat.aoPath = tex.filePath
                }
            }
            if (rc.emissiveGuid.isNotBlank() && mat.emissiveTexture == null) {
                loadTextureFromGuidOrPath(rc.emissiveGuid)?.let { tex ->
                    mat.emissiveTexture = tex
                    mat.emissivePath = tex.filePath
                }
            }
        }
    }

    private fun loadTextureFromGuidOrPath(guidOrPath: String): com.pafoid.skate.engine.assets.data.Texture? {
        return try {
            val guid = AssetGuid.parse(guidOrPath)
            val asset = assetDatabase?.getByGuid(guid)
            if (asset != null) {
                resourceManager.loadTextureSync(asset.absoluteSourcePath)
            } else null
        } catch (e: IllegalArgumentException) {
            val file = File(guidOrPath)
            if (file.exists()) {
                resourceManager.loadTextureSync(guidOrPath)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveAnimationReferences(scene: Scene) {
        scene.gameObjectManager.gameObjects.forEach { obj ->
            resolveAnimatorAnimations(obj)
            obj.children.forEach { child -> resolveAnimatorAnimations(child) }
        }
    }

    private fun resolveAnimatorAnimations(obj: GameObject) {
        val animator = obj.getComponent<Animator>() ?: return
        if (animator.animationPaths.isEmpty()) return

        animator.loadAnimationsFromPaths(resourceManager)
        logger.logEditor("Loaded ${animator.animationPaths.size} animations for ${obj.name}")
    }
}

/**
 * Serializable scene save data for save/load operations.
 *
 * Replaces the old LevelData class. Stores the full scene state
 * including all GameObjects and scene-wide configuration.
 */
@Serializable
data class SceneSaveData(
    val gameObjects: List<GameObject>,
    val sceneData: com.pafoid.skate.engine.ecs.scene.SceneData,
    @kotlinx.serialization.SerialName("levelPath")
    var scenePath: String = ""
)
