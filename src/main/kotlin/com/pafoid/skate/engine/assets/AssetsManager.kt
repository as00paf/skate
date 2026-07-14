package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.assets.loaders.ShaderLoader
import com.pafoid.skate.engine.assets.loaders.TextureLoader
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.data.LogLevel
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.utils.DefaultJobSystem
import com.pafoid.skate.engine.utils.IJobSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages loading, caching, and unloading of engine assets.
 *
 * Features:
 * - Automatic caching by absolute path
 * - Dependency tracking between models and textures
 * - Hot-reloading via file watching (editor only)
 * - Safe resource cleanup
 *
 * @param logger Logger service
 */
class AssetsManager(
    private val vaoLoader: VAOLoader,
    private val logger: LoggerService,
    private val jobSystem: IJobSystem = DefaultJobSystem()
) {
    private val shaderLoader: ShaderLoader = ShaderLoader(false)
    private val assimpLoader: AssimpLoader = AssimpLoader()
    private val textureLoader = TextureLoader()

    private val textures = ConcurrentHashMap<String, Texture>()
    private val shaders = ConcurrentHashMap<String, Shader>()
    private val models = ConcurrentHashMap<String, TexturedModel>()
    private val sounds = ConcurrentHashMap<String, SoundBuffer>()
    private val animations = ConcurrentHashMap<String, Animation>()
    private val modelDependencies = ConcurrentHashMap<String, Set<String>>()

    fun getTexture(path: String): Texture {
        val absolutePath = File(path).absolutePath
        textures[absolutePath]?.let { return it }

        val texture = try {
            textureLoader.loadFromFile(path)
        } catch (e: Exception) {
            logger.log("Failed to load texture : $path. Error: ${e.message}. Loading default texture.", LogLevel.ERROR)
            textureLoader.loadFromFile((Assets.Textures.DEFAULT))
        }
        textures[absolutePath] = texture
        return texture
    }

    fun hasTexture(path: String): Boolean {
        return textures[path] != null
    }

    fun getShader(path: String): Shader {
        val file = File(path)
        val absolutePath = file.absolutePath
        shaders[absolutePath]?.let { return it }

        return try {
            val shader = shaderLoader.loadShader(path)
            shaders[absolutePath] = shader
            shader
        } catch (e: Exception) {
            logger.log("Failed to load shader: $path. Error: ${e.message}", LogLevel.ERROR)
            if (path == Assets.Shaders.SHADER_3D_DEFAULT) throw RuntimeException("Critical Error: Default 3D shader not found!")
            logger.log("Loading default 3D shader instead of $path", LogLevel.ERROR)
            getShader(Assets.Shaders.SHADER_3D_DEFAULT)
        }
    }

    fun getSound(path: String): SoundBuffer {
        val file = File(path)
        val absolutePath = file.absolutePath

        sounds[absolutePath]?.let { return it }

        return try {
            val sound = SoundBuffer(absolutePath)
            sounds[absolutePath] = sound
            sound
        } catch (e: Exception) {
            logger.log("Failed to load sound: $path. Error: ${e.message}", LogLevel.ERROR)
            throw e // Sounds might not have a good default fallback yet
        }
    }

    fun hasSound(path: String): Boolean {
        return sounds[path] != null
    }

    suspend fun loadModel(path: String): TexturedModel {
        val file = File(path)
        val absolutePath = file.absolutePath

        models[absolutePath]?.let { return it }

        return try {
             val preLoaded = withContext(Dispatchers.IO) {
                 assimpLoader.preLoadModel(path)
             }

             // Collect texture dependencies
             val texturePaths = mutableSetOf<String>()
            preLoaded.mesh.forEach { part ->
                 listOfNotNull(
                     part.material.baseColorPath,
                     part.material.normalMapPath,
                     part.material.metallicRoughnessPath,
                     part.material.aoPath,
                     part.material.emissivePath
                 ).forEach { texturePaths.add(it) }
             }

            val textureDataMap = mutableMapOf<String, Texture>()
                 withContext(Dispatchers.IO) {
                     texturePaths.forEach { texPath ->
                        if (!textureDataMap.containsKey(texPath)) {
                            val buffer = preLoaded.mesh.firstNotNullOfOrNull { it.embeddedTextures[texPath] }
                            val data =
                                if (buffer != null) textureLoader.loadFromBuffer(buffer) else textureLoader.loadFromFile(
                                    texPath
                                )
                            textureDataMap[texPath] = data
                        }
                     }
                 }

            withContext(jobSystem.mainDispatcher) {
                val parts = preLoaded.mesh.map { p ->
                         val model = vaoLoader.loadToVAO(p.vertices, p.texCoords, p.normals, p.indices, p.vertices, p.tangents, p.colors, p.drawMode, p.texCoords1, p.joints, p.weights)

                         val mat = p.material
                         fun getOrCreateTex(texPath: String?): Texture? {
                             if (texPath == null) return null
                             val absTexPath = File(texPath).absolutePath
                             if (textures.containsKey(absTexPath)) return textures[absTexPath]

                             val texture = textureDataMap[texPath] ?: return null
                             textures[absTexPath] = texture
                             return texture
                         }

                         mat.baseColorTexture = getOrCreateTex(mat.baseColorPath)
                         mat.normalMap = getOrCreateTex(mat.normalMapPath)
                         mat.metallicRoughnessTexture = getOrCreateTex(mat.metallicRoughnessPath)
                         mat.aoTexture = getOrCreateTex(mat.aoPath)
                         mat.emissiveTexture = getOrCreateTex(mat.emissivePath)

                         MeshPart(rawModel = model, material = mat, inverseBindMatrices = p.inverseBindMatrices)
                     }

                val characterModel = TexturedModel(mesh = parts, skeleton = preLoaded.skeleton, path = path)
                     modelDependencies[absolutePath] = texturePaths.toSet()
                     models[absolutePath] = characterModel
                     characterModel
                 }
        } catch (e: Exception) {
            logger.log("Failed to load model: $path. Error: ${e.message}", LogLevel.ERROR)
            if (path == Assets.Models.CUBE) throw RuntimeException("Critical Error: Default CUBE model not found!")
            logger.log("Loading default CUBE model instead of $path", LogLevel.ERROR)
            loadModel(Assets.Models.CUBE)
        }
    }

    fun loadModelSync(path: String): TexturedModel {
        val file = File(path)
        val absolutePath = file.absolutePath

        models[absolutePath]?.let { return it }
        
        return try {
             val preLoaded = assimpLoader.preLoadModel(path)

            val parts = preLoaded.mesh.map { p ->
                     val model = vaoLoader.loadToVAO(p.vertices, p.texCoords, p.normals, p.indices, p.vertices, p.tangents, p.colors, p.drawMode, p.texCoords1, p.joints, p.weights)
                     
                     val mat = p.material
                     fun getOrCreateTexSync(texPath: String?): Texture? {
                         if (texPath == null) return null
                         val absTexPath = File(texPath).absolutePath
                         if (textures.containsKey(absTexPath)) return textures[absTexPath]
                         
                         val buffer = p.embeddedTextures[texPath]
                         val texture =
                             if (buffer != null) textureLoader.loadFromBuffer(buffer) else textureLoader.loadFromFile(
                                 texPath
                             )

                         textures[absTexPath] = texture
                         return texture
                     }

                     mat.baseColorTexture = getOrCreateTexSync(mat.baseColorPath)
                     mat.normalMap = getOrCreateTexSync(mat.normalMapPath)
                     mat.metallicRoughnessTexture = getOrCreateTexSync(mat.metallicRoughnessPath)
                     mat.aoTexture = getOrCreateTexSync(mat.aoPath)
                     mat.emissiveTexture = getOrCreateTexSync(mat.emissivePath)

                 MeshPart(rawModel = model, material = mat, inverseBindMatrices = p.inverseBindMatrices)
                 }

            val characterModel = TexturedModel(mesh = parts, path = path, skeleton = preLoaded.skeleton)
                 models[absolutePath] = characterModel
                 characterModel
        } catch (e: Exception) {
            logger.log("Failed to load model: $path. Error: ${e.message}", LogLevel.ERROR)
            if (path == Assets.Models.CUBE) throw RuntimeException("Critical Error: Default CUBE model not found!")
            logger.log("Loading default CUBE model instead of $path", LogLevel.ERROR)
            loadModelSync(Assets.Models.CUBE)
        }
    }

    fun getModel(path: String): TexturedModel? {
        return models[File(path).absolutePath]
    }

    fun loadAnimationSync(path: String, skeleton: Skeleton): Animation {
        val file = File(path)
        val absolutePath = file.absolutePath

        animations[absolutePath]?.let { return it }
        return try {
            val loadedAnimation = assimpLoader.loadAnimations(path, skeleton)[0]
            animations[absolutePath] = loadedAnimation
            loadedAnimation
        } catch (e: Exception) {
            logger.log("Failed to load animations from: $path. Error: ${e.message}", LogLevel.ERROR)
            throw e
        }
    }

    fun getAnimation(filePath: String): Animation? {
        val absolutePath = File(filePath).absolutePath
        return animations[absolutePath]
    }

    fun hasAnimation(filePath: String): Boolean {
        return animations[filePath] != null
    }
}
