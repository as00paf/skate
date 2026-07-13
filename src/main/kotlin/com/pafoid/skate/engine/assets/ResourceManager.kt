package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.assets.loaders.ShaderLoader
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.data.LogLevel
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.utils.DefaultJobSystem
import com.pafoid.skate.engine.utils.IJobSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages loading, caching, and unloading of engine assets.
 *
 * Features:
 * - Automatic caching by absolute path
 * - Dependency tracking between models and textures
 * - LRU cache eviction based on memory usage
 * - Hot-reloading via file watching (editor only)
 * - Async loading via coroutines
 * - Safe resource cleanup
 *
 * @param shaderLoader Shader file loader
 * @param assimpLoader Model file loader via Assimp
 * @param vaoLoader Vertex array object loader
 * @param logger Logger service
 * @param maxMemoryBytes Max memory for texture cache (default 256MB)
 * @param enableHotReload Enable file watching for hot-reload (default false)
 */
class ResourceManager(
    private val shaderLoader: ShaderLoader = ShaderLoader(false),
    private val assimpLoader: AssimpLoader = AssimpLoader(),
    private val vaoLoader: VAOLoader,
    private val logger: LoggerService,
    private val maxMemoryBytes: Long = 256 * 1024 * 1024,
    private val enableHotReload: Boolean = false,
    private val assetDatabase: AssetDatabase,
    private val jobSystem: IJobSystem = DefaultJobSystem()
) {

    private val textures = ConcurrentHashMap<String, Texture>()
    private val shaders = ConcurrentHashMap<String, Shader>()
    private val models = ConcurrentHashMap<String, TexturedModel>()
    private val sounds = ConcurrentHashMap<String, SoundBuffer>()
    private val animations = ConcurrentHashMap<String, Animation>()
    private val modelDependencies = ConcurrentHashMap<String, Set<String>>()
    private val lruQueue = CopyOnWriteArrayList<String>()
    private var currentTextureMemory = AtomicLong(0L)
    private var watchService: WatchService? = null
    private val watchedPaths = ConcurrentHashMap<String, String>()

    suspend fun loadTexture(path: String): Texture {
        val file = File(path)
        val absolutePath = file.absolutePath

        textures[absolutePath]?.let { 
            lruQueue.remove(absolutePath)
            lruQueue.add(absolutePath)
            return it 
        }

        val data = withContext(Dispatchers.IO) {
            try {
                Texture.fromFile(path)
            } catch (e: Exception) {
                logger.log("Failed to load texture data: $path. Error: ${e.message}", LogLevel.ERROR)
                null
            }
        }

        if (data == null) {
            logger.log("Texture not found: $path. Loading default texture.", LogLevel.ERROR)
            if (path == Assets.Textures.DEFAULT) throw RuntimeException("Critical Error: Default texture not found!")
            return loadTexture(Assets.Textures.DEFAULT)
        }

        return withContext(jobSystem.mainDispatcher) {
            while (currentTextureMemory.get() > maxMemoryBytes && lruQueue.isNotEmpty()) {
                val oldestPath = lruQueue.firstOrNull() ?: break
                evictTexture(oldestPath)
            }

            val texture = data
            texture.uploadToGPU()
            texture.free()
            texture.filePath = path
            textures[absolutePath] = texture
            
            val estimatedMemory = texture.width * texture.height * 4L * 4 / 3
            currentTextureMemory.addAndGet(estimatedMemory)
            
            lruQueue.add(absolutePath)
            
            if (enableHotReload) {
                watchFile(absolutePath)
            }
            
            texture
        }
    }

    fun loadTextureSync(path: String): Texture {
        val file = File(path)
        val absolutePath = file.absolutePath
        textures[absolutePath]?.let { return it }

        val texture = try {
            Texture.fromFile(path)
        } catch (e: Exception) {
            logger.log("Failed to load texture : $path. Error: ${e.message}. Loading default texture.", LogLevel.ERROR)
            Texture.fromFile(Assets.Textures.DEFAULT)
        }
        texture.uploadToGPU() // Must be called on GL thread
        texture.free()
        textures[absolutePath] = texture
        return texture
    }
    
    private fun evictTexture(path: String) {
        if (isTextureInUse(path)) return
        
        textures.remove(path)?.let {
            val estimatedMemory = it.width * it.height * 4L * 4 / 3
            currentTextureMemory.addAndGet(-estimatedMemory)
            jobSystem.runOnMain {
                it.destroy()
            }
            logger.log("Evicted texture from LRU cache: $path", LogLevel.INFO)
        }
        lruQueue.remove(path)
    }
    
    private fun watchFile(path: String) {
        if (watchService == null) {
            try {
                watchService = FileSystems.getDefault().newWatchService()
                logger.log("Hot-reload watch service started", LogLevel.INFO)
            } catch (e: Exception) {
                logger.log("Failed to create watch service: ${e.message}", LogLevel.ERROR)
                return
            }
        }
        
        val file = File(path)
        val parentPath = file.parentFile?.toPath() ?: return
        
        if (!watchedPaths.containsKey(path)) {
            try {
                parentPath.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY
                )
                watchedPaths[path] = path
            } catch (e: Exception) {
                logger.log("Failed to watch file: $path - ${e.message}", LogLevel.WARN)
            }
        }
    }
    
    fun pollHotReload() {
        if (!enableHotReload) return
        
        val service = watchService ?: return
        val key = service.poll() ?: return
        
        for (event in key.pollEvents()) {
            val context = event.context()
            if (context != null) {
                val path = key.watchable() as Path
                val absolutePath = path.resolve(context as Path).toAbsolutePath().toString()
                
                val watchedPath = watchedPaths.keys.find { it.endsWith(context.toString()) }
                if (watchedPath != null) {
                    logger.log("File changed, reloading: $absolutePath", LogLevel.INFO)
                    invalidateAsset(absolutePath)
                }
            }
        }
        key.reset()
    }
    
    fun invalidateAsset(path: String) {
        val absolutePath = File(path).absolutePath
        
        textures.remove(absolutePath)?.let {
            val estimatedMemory = it.width * it.height * 4L * 4 / 3
            currentTextureMemory.addAndGet(-estimatedMemory)
            jobSystem.runOnMain { it.destroy() }
            lruQueue.remove(absolutePath)
        }

        logger.log("Invalidated asset: $absolutePath", LogLevel.INFO)
    }

    suspend fun loadShader(path: String): Shader {
        val file = File(path)
        val absolutePath = file.absolutePath

        shaders[absolutePath]?.let { return it }

        return try {
            withContext(jobSystem.mainDispatcher) {
                val shader = shaderLoader.loadShader(path)
                shaders[absolutePath] = shader
                shader
            }
        } catch (e: Exception) {
            logger.log("Failed to load shader: $path. Error: ${e.message}", LogLevel.ERROR)
            if (path == Assets.Shaders.SHADER_3D_DEFAULT) throw RuntimeException("Critical Error: Default 3D shader not found!")
            logger.log("Loading default 3D shader instead of $path", LogLevel.ERROR)
            loadShader(Assets.Shaders.SHADER_3D_DEFAULT)
        }
    }

    fun loadShaderSync(path: String): Shader {
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
            loadShaderSync(Assets.Shaders.SHADER_3D_DEFAULT)
        }
    }
    
    fun getShader(path: String): Shader? {
        return shaders[File(path).absolutePath]
    }

    fun loadSound(path: String): SoundBuffer {
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

    fun getSound(path: String): SoundBuffer? {
        return sounds[File(path).absolutePath]
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
                            val data = if (buffer != null) Texture.fromBuffer(buffer) else Texture.fromFile(texPath)
                            if (data != null) textureDataMap[texPath] = data
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
                             texture.uploadToGPU()
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

                     textureDataMap.values.forEach { it.free() }

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
                         val texture = if (buffer != null) Texture.fromBuffer(buffer) else Texture.fromFile(texPath)

                         texture.uploadToGPU()
                         texture.free()
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

    fun isTextureInUse(texturePath: String): Boolean {
        val absolutePath = File(texturePath).absolutePath
        return modelDependencies.values.any { it.contains(absolutePath) }
    }

    fun clear(preserveNonProjectAssets: Boolean = false) {
        val preserveExternalAssets = preserveNonProjectAssets && assetDatabase.projectRoot != null
        val projectRoot = assetDatabase.projectRoot

        val texturesToDestroy = mutableListOf<Texture>()
        val modelsToDestroy = mutableListOf<TexturedModel>()
        val shadersToDestroy = mutableListOf<Shader>()

        textures.entries.toList().forEach { (path, texture) ->
            if (preserveExternalAssets && !isInProject(path, projectRoot!!)) {
                return@forEach
            }
            textures.remove(path)
            texturesToDestroy.add(texture)
        }

        models.entries.toList().forEach { (path, model) ->
            if (preserveExternalAssets && !isInProject(path, projectRoot!!)) {
                return@forEach
            }
            models.remove(path)
            modelsToDestroy.add(model)
            modelDependencies.remove(path)
        }

        shaders.entries.toList().forEach { (path, shader) ->
            if (preserveExternalAssets && !isInProject(path, projectRoot!!)) {
                return@forEach
            }
            shaders.remove(path)
            shadersToDestroy.add(shader)
        }

        sounds.entries.toList().forEach { (path, sound) ->
            if (preserveExternalAssets && !isInProject(path, projectRoot!!)) {
                return@forEach
            }
            sounds.remove(path)
            sound.delete()
        }

        if (!preserveExternalAssets) {
            modelDependencies.clear()
            animations.clear()
        } else {
            animations.entries.toList().forEach { (path, _) ->
                if (isInProject(path, projectRoot!!)) {
                    animations.remove(path)
                }
            }
            modelDependencies.entries.removeIf { (modelPath, _) -> !models.containsKey(modelPath) }
        }

        lruQueue.removeIf { texturePath -> !textures.containsKey(texturePath) }
        currentTextureMemory.set(
            textures.values.sumOf { texture ->
                texture.width * texture.height * 4L * 4 / 3
            }
        )

        if (texturesToDestroy.isNotEmpty() || modelsToDestroy.isNotEmpty() || shadersToDestroy.isNotEmpty()) {
            val destroyGpuResources = suspend {
                texturesToDestroy.forEach { it.destroy() }
                modelsToDestroy.forEach { model ->
                    model.mesh.forEach { part ->
                        part.rawModel?.let { vaoLoader.deleteVAO(it.vaoId) }
                    }
                }
                shadersToDestroy.forEach { it.destroy() }
            }

            if (jobSystem.isMainThread()) {
                runBlocking { destroyGpuResources() }
            } else {
                jobSystem.runOnMain {
                    destroyGpuResources()
                }
            }
        }

        watchService?.close()
        watchService = null
        watchedPaths.clear()
    }

    private fun isInProject(assetPath: String, projectRoot: File): Boolean {
        val normalizedAssetPath = File(assetPath).absolutePath.replace('\\', '/').lowercase()
        val normalizedProjectRoot = projectRoot.absolutePath.replace('\\', '/').trimEnd('/').lowercase()
        return normalizedAssetPath == normalizedProjectRoot || normalizedAssetPath.startsWith("$normalizedProjectRoot/")
    }
}
