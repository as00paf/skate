package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.assets.database.AssetGuid
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.logEditor
import com.pafoid.skate.engine.data.LogLevel
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.getComponent
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.io.File
import java.io.FileWriter

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
    private val assetDatabase: AssetDatabase? = null,
    private val systemManager: SystemManager,
) {
    private val gameObjectManager: GameObjectManager by lazy {
        systemManager.getSystem<GameObjectManager>() ?: throw RuntimeException("GameObjectManager not initialized")
    }

    fun save(scene: Scene, dirPath: String): Boolean {
        val path = dirPath + "/" + scene.name + ".scene"
        try {
            val parentDir = File(dirPath)
            if (!parentDir.exists()) parentDir.mkdirs()
            else if (!parentDir.isDirectory) {
                logger.logEditor("Failed to save scene to directory $dirPath", LogLevel.ERROR)
                return false
            }

            FileWriter(path).use { writer ->
                writer.write(serializer.encode(scene))
            }

            scene.isDirty = false
            logger.logEditor("Scene saved to $path")
        } catch (e: Exception) {
            logger.logEditor("Failed to save scene to $path: ${e.message}", LogLevel.ERROR)
            return false
        }
        return true
    }

    fun saveAs(scene: Scene) {
        val filter = MemoryUtil.memUTF8("*.scene")
        val filters = MemoryUtil.memAllocPointer(1)
        filters.put(0, filter)

        val path = try {
            TinyFileDialogs.tinyfd_saveFileDialog(scene.name, "Save Scene", filters, "Scene Files")
        } finally {
            MemoryUtil.memFree(filter)
            MemoryUtil.memFree(filters)
        }

        if (path != null) {
            scene.name = File(path).nameWithoutExtension
            save(scene, path)
        }
    }

    /*fun load(path: String): Boolean {
        var inFile = ""
        try {
            inFile = String(Files.readAllBytes(Paths.get(path)))
        } catch (e: IOException) {
            logger.logEditor("Could not find $path", LogLevel.ERROR)
            return false
        }

        if (inFile.isBlank()) return false

        val scene: Scene = try {
            serializer.decode(inFile)
        } catch (e: Exception) {
            logger.logEditor("Failed to deserialize scene from $path: ${e.message}", LogLevel.ERROR)
            return false
        }

        scene.gameObjects.forEach { it.destroy() }
        scene.gameObjects.clear()
        scene.markObjectSetChanged()

        scene.name = File(path).name

        var maxGoId = -1
        var maxCompId = -1

        if (data.sceneComponents.isNotEmpty()) {
            scene.getAllComponents().forEach { it.destroy() }
            scene.components.clear()

            data.sceneComponents.forEach { component ->
                component.init(scene)
                scene.components.add(component)
            }
        }

        scene.getAllComponents().forEach { component ->
            if (component.getUid() > maxCompId) {
                maxCompId = component.getUid()
            }
        }

        data.gameObjects.forEach { obj ->
            obj.getAllComponents().forEach { it.init(obj) }

            gameObjectManager.addGameObject(obj)

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
            scene.collectGameObjectsDepthFirst().forEach { obj ->
                resolveObjectReferences(obj)
                resolveAnimatorAnimations(obj)
            }
        }

        logger.logEditor("Scene loaded from $path")

        scene.gameObjects.forEach { go ->
            val compCount = go.getAllComponents().size
            logger.logEditor("[DIAG]   - ${go.name}: $compCount components")
        }

        return true
    }*/


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

    private fun resolveAnimatorAnimations(obj: GameObject) {
        val animator = obj.getComponent<Animator>() ?: return
        if (animator.animationPaths.isEmpty()) return

        animator.loadAnimationsFromPaths(resourceManager)
        logger.logEditor("Loaded ${animator.animationPaths.size} animations for ${obj.name}")
    }
}