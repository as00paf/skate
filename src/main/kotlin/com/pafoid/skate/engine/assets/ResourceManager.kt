package com.pafoid.skate.engine.assets

import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.TextureData
import com.pafoid.skate.engine.assets.data.models.BaseModel
import com.pafoid.skate.engine.assets.data.models.CharacterModel
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.assets.database.AssetGuid
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.assets.loaders.ShaderLoader
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.utils.JobSystem
import kotlinx.coroutines.Dispatchers
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
    private val assetDatabase: AssetDatabase? = null
) {

    private val textures = ConcurrentHashMap<String, Texture>()
    private val shaders = ConcurrentHashMap<String, Shader>()
    private val models = ConcurrentHashMap<String, BaseModel>()
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
                Texture.loadData(path)
            } catch (e: Exception) {
                logger.logEngine("Failed to load texture data: $path. Error: ${e.message}", LogLevel.ERROR)
                null
            }
        }

        if (data == null) {
            logger.logEngine("Texture not found: $path. Loading default texture.", LogLevel.ERROR)
            if (path == Assets.Textures.DEFAULT) throw RuntimeException("Critical Error: Default texture not found!")
            return loadTexture(Assets.Textures.DEFAULT)
        }

        return withContext(JobSystem.Main) {
            while (currentTextureMemory.get() > maxMemoryBytes && lruQueue.isNotEmpty()) {
                val oldestPath = lruQueue.firstOrNull() ?: break
                evictTexture(oldestPath)
            }
            
            val texture = Texture()
            texture.uploadToGPU(data)
            data.free()
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
    
    fun loadTextureSync(path: String?): Texture {
        val file = File(path.orEmpty())
        val absolutePath = file.absolutePath

        val data = if(path != null) {
            textures[absolutePath]?.let { return it }
            try {
                Texture.loadData(path)
            } catch (e: Exception) {
                logger.logEngine("Failed to load texture data: $path. Error: ${e.message}", LogLevel.ERROR)
                null
            }
        } else null

        if (data == null) {
            logger.logEngine("Texture not found: $path. Loading default texture.", LogLevel.ERROR)
            if (path == Assets.Textures.DEFAULT) throw RuntimeException("Critical Error: Default texture not found!")
            return loadTextureSync(Assets.Textures.DEFAULT)
        }

        val texture = Texture()
        texture.uploadToGPU(data) // Must be called on GL thread
        data.free()
        texture.filePath = path
        textures[absolutePath] = texture
        return texture
    }

    fun getTexture(path: String): Texture? {
        val absolutePath = File(path).absolutePath
        val texture = textures[absolutePath]
        if (texture != null) {
            lruQueue.remove(absolutePath)
            lruQueue.add(absolutePath)
        }
        return texture
    }
    
    private fun evictTexture(path: String) {
        if (isTextureInUse(path)) return
        
        textures.remove(path)?.let {
            val estimatedMemory = it.width * it.height * 4L * 4 / 3
            currentTextureMemory.addAndGet(-estimatedMemory)
            JobSystem.runOnMain {
                it.destroy()
            }
            logger.logEngine("Evicted texture from LRU cache: $path", LogLevel.INFO)
        }
        lruQueue.remove(path)
    }
    
    private fun watchFile(path: String) {
        if (watchService == null) {
            try {
                watchService = FileSystems.getDefault().newWatchService()
                logger.logEngine("Hot-reload watch service started", LogLevel.INFO)
            } catch (e: Exception) {
                logger.logEngine("Failed to create watch service: ${e.message}", LogLevel.ERROR)
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
                logger.logEngine("Failed to watch file: $path - ${e.message}", LogLevel.WARN)
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
                    logger.logEngine("File changed, reloading: $absolutePath", LogLevel.INFO)
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
            JobSystem.runOnMain { it.destroy() }
            lruQueue.remove(absolutePath)
        }
        
        logger.logEngine("Invalidated asset: $absolutePath", LogLevel.INFO)
    }

    suspend fun loadShader(path: String): Shader {
        val file = File(path)
        val absolutePath = file.absolutePath

        shaders[absolutePath]?.let { return it }

        return try {
            withContext(JobSystem.Main) {
                val shader = shaderLoader.loadShader(path)
                shaders[absolutePath] = shader
                shader
            }
        } catch (e: Exception) {
            logger.logEngine("Failed to load shader: $path. Error: ${e.message}", LogLevel.ERROR)
            if (path == Assets.Shaders.SHADER_3D_DEFAULT) throw RuntimeException("Critical Error: Default 3D shader not found!")
            logger.logEngine("Loading default 3D shader instead of $path", LogLevel.ERROR)
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
            logger.logEngine("Failed to load shader: $path. Error: ${e.message}", LogLevel.ERROR)
            if (path == Assets.Shaders.SHADER_3D_DEFAULT) throw RuntimeException("Critical Error: Default 3D shader not found!")
            logger.logEngine("Loading default 3D shader instead of $path", LogLevel.ERROR)
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
            logger.logEngine("Failed to load sound: $path. Error: ${e.message}", LogLevel.ERROR)
            throw e // Sounds might not have a good default fallback yet
        }
    }

    fun getSound(path: String): SoundBuffer? {
        return sounds[File(path).absolutePath]
    }

    suspend fun loadModel(path: String): BaseModel {
        val file = File(path)
        val absolutePath = file.absolutePath

        models[absolutePath]?.let { return it }

        return try {
             val preLoaded = withContext(Dispatchers.IO) {
                 assimpLoader.preLoadModel(path)
             }

             // Collect texture dependencies
             val texturePaths = mutableSetOf<String>()
             preLoaded.parts.forEach { part ->
                 listOfNotNull(
                     part.material.baseColorPath,
                     part.material.normalMapPath,
                     part.material.metallicRoughnessPath,
                     part.material.aoPath,
                     part.material.emissivePath
                 ).forEach { texturePaths.add(it) }
             }
             
             val textureDataMap = mutableMapOf<String, TextureData>()
                 withContext(Dispatchers.IO) {
                     texturePaths.forEach { texPath ->
                        if (!textureDataMap.containsKey(texPath)) {
                            val buffer = preLoaded.parts.firstNotNullOfOrNull { it.embeddedTextures[texPath] }
                            val data = if (buffer != null) Texture.loadData(buffer) else Texture.loadData(texPath)
                            if (data != null) textureDataMap[texPath] = data
                        }
                     }
                 }

                 withContext(JobSystem.Main) {
                     val parts = preLoaded.parts.map { p ->
                         val model = vaoLoader.loadToVAO(p.vertices, p.texCoords, p.normals, p.indices, p.vertices, p.tangents, p.colors, p.drawMode, p.texCoords1, p.joints, p.weights)

                         val mat = p.material
                         fun getOrCreateTex(texPath: String?): Texture? {
                             if (texPath == null) return null
                             val absTexPath = File(texPath).absolutePath
                             if (textures.containsKey(absTexPath)) return textures[absTexPath]

                             val data = textureDataMap[texPath] ?: return null
                             val tex = Texture()
                             tex.uploadToGPU(data)
                             tex.filePath = texPath
                             textures[absTexPath] = tex
                             return tex
                         }

                         mat.baseColorTexture = getOrCreateTex(mat.baseColorPath)
                         mat.normalMap = getOrCreateTex(mat.normalMapPath)
                         mat.metallicRoughnessTexture = getOrCreateTex(mat.metallicRoughnessPath)
                         mat.aoTexture = getOrCreateTex(mat.aoPath)
                         mat.emissiveTexture = getOrCreateTex(mat.emissivePath)

                         MeshPart(model, mat, p.inverseBindMatrices)
                     }

                     textureDataMap.values.forEach { it.free() }

                     val characterModel = if (preLoaded.skeleton != null) {
                         CharacterModel(parts, preLoaded.skeleton)
                     } else {
                         TexturedModel(parts)
                     }

                     modelDependencies[absolutePath] = texturePaths.toSet()
                     
                     models[absolutePath] = characterModel
                     characterModel.sourcePath = path
                     characterModel
                 }
        } catch (e: Exception) {
            logger.logEngine("Failed to load model: $path. Error: ${e.message}", LogLevel.ERROR)
            if (path == Assets.Models.CUBE) throw RuntimeException("Critical Error: Default CUBE model not found!")
            logger.logEngine("Loading default CUBE model instead of $path", LogLevel.ERROR)
            loadModel(Assets.Models.CUBE)
        }
    }
    
    fun loadModelSync(path: String): BaseModel {
        val file = File(path)
        val absolutePath = file.absolutePath

        models[absolutePath]?.let { return it as BaseModel }
        
        return try {
             val preLoaded = assimpLoader.preLoadModel(path)

             val parts = preLoaded.parts.map { p ->
                     val model = vaoLoader.loadToVAO(p.vertices, p.texCoords, p.normals, p.indices, p.vertices, p.tangents, p.colors, p.drawMode, p.texCoords1, p.joints, p.weights)
                     
                     val mat = p.material
                     fun getOrCreateTexSync(texPath: String?): Texture? {
                         if (texPath == null) return null
                         val absTexPath = File(texPath).absolutePath
                         if (textures.containsKey(absTexPath)) return textures[absTexPath]
                         
                         val buffer = p.embeddedTextures[texPath]
                         val data = if (buffer != null) Texture.loadData(buffer) else Texture.loadData(texPath)
                         
                         if (data != null) {
                             val tex = Texture()
                             tex.uploadToGPU(data)
                             tex.filePath = texPath
                             data.free()
                             textures[absTexPath] = tex
                             return tex
                         }
                         return null
                     }

                     mat.baseColorTexture = getOrCreateTexSync(mat.baseColorPath)
                     mat.normalMap = getOrCreateTexSync(mat.normalMapPath)
                     mat.metallicRoughnessTexture = getOrCreateTexSync(mat.metallicRoughnessPath)
                     mat.aoTexture = getOrCreateTexSync(mat.aoPath)
                     mat.emissiveTexture = getOrCreateTexSync(mat.emissivePath)

                     MeshPart(model, mat, p.inverseBindMatrices)
                 }

                 val characterModel = if (preLoaded.skeleton != null) {
                     CharacterModel(parts, preLoaded.skeleton)
                 } else {
                     TexturedModel(parts)
                 }
                 models[absolutePath] = characterModel
                 characterModel.sourcePath = path
                 characterModel
        } catch (e: Exception) {
            logger.logEngine("Failed to load model: $path. Error: ${e.message}", LogLevel.ERROR)
            if (path == Assets.Models.CUBE) throw RuntimeException("Critical Error: Default CUBE model not found!")
            logger.logEngine("Loading default CUBE model instead of $path", LogLevel.ERROR)
            loadModelSync(Assets.Models.CUBE)
        }
    }
    
    fun getModel(path: String): BaseModel? {
        return models[File(path).absolutePath]
    }

    suspend fun loadAnimation(path: String, skeleton: Skeleton): Animation {
        val file = File(path)
        val absolutePath = file.absolutePath

        animations[absolutePath]?.let { return it }
        return try {
            val loadedAnimation = assimpLoader.loadAnimations(path, skeleton)[0]
            animations[absolutePath] = loadedAnimation
            loadedAnimation
        } catch (e: Exception) {
            logger.logEngine("Failed to load animations from: $path. Error: ${e.message}", LogLevel.ERROR)
            throw e
        }
    }

    fun getAnimation(filePath: String): Animation? {
        val absolutePath = File(filePath).absolutePath
        return animations[absolutePath]
    }

    fun getModelDependencies(path: String): Set<String> {
        val absolutePath = File(path).absolutePath
        return modelDependencies[absolutePath] ?: emptySet()
    }

    fun isTextureInUse(texturePath: String): Boolean {
        val absolutePath = File(texturePath).absolutePath
        return modelDependencies.values.any { it.contains(absolutePath) }
    }

    fun unloadTexture(path: String) {
        val absolutePath = File(path).absolutePath
        
        if (isTextureInUse(absolutePath)) {
            return
        }
        
        textures.remove(absolutePath)?.let {
             JobSystem.runOnMain {
                 it.destroy()
             }
        }
    }

    fun unloadModel(path: String) {
        val absolutePath = File(path).absolutePath
        models.remove(absolutePath)?.let { model ->
            modelDependencies.remove(absolutePath)
             
             JobSystem.runOnMain {
                 model.mesh.forEach { part ->
                     vaoLoader.deleteVAO(part.rawModel.vaoId)
                 }
             }
        }
    }

    fun unloadShader(path: String) {
        val absolutePath = File(path).absolutePath
        shaders.remove(absolutePath)?.let {
            JobSystem.runOnMain {
                it.destroy()
            }
        }
    }

    fun clear() {
        modelDependencies.clear()

        val texKeys = textures.keys.toList()
        texKeys.forEach { unloadTexture(it) }

        val modelKeys = models.keys.toList()
        modelKeys.forEach { unloadModel(it) }

        val shaderKeys = shaders.keys.toList()
        shaderKeys.forEach { unloadShader(it) }

        animations.clear()

        sounds.values.forEach {
            it.delete()
        }
        sounds.clear()

        lruQueue.clear()
        currentTextureMemory.set(0L)

        watchService?.close()
        watchService = null
        watchedPaths.clear()
    }

    // ─── GUID-Aware Loading ───────────────────────────

    /**
     * Load a texture by its asset GUID.
     * Resolves the GUID through the AssetDatabase to find the source path.
     */
    suspend fun loadTextureByGuid(guid: AssetGuid): Texture {
        val asset = assetDatabase?.getByGuid(guid)
            ?: throw IllegalArgumentException("Asset not found: $guid")
        return loadTexture(asset.absoluteSourcePath)
    }

    /**
     * Load a texture synchronously by its asset GUID.
     */
    fun loadTextureSyncByGuid(guid: AssetGuid): Texture {
        val asset = assetDatabase?.getByGuid(guid)
            ?: throw IllegalArgumentException("Asset not found: $guid")
        return loadTextureSync(asset.absoluteSourcePath)
    }

    /**
     * Load a model by its asset GUID.
     */
    suspend fun loadModelByGuid(guid: AssetGuid): BaseModel {
        val asset = assetDatabase?.getByGuid(guid)
            ?: throw IllegalArgumentException("Asset not found: $guid")
        return loadModel(asset.absoluteSourcePath)
    }

    /**
     * Load a model synchronously by its asset GUID.
     */
    fun loadModelSyncByGuid(guid: AssetGuid): BaseModel {
        val asset = assetDatabase?.getByGuid(guid)
            ?: throw IllegalArgumentException("Asset not found: $guid")
        return loadModelSync(asset.absoluteSourcePath)
    }

    /**
     * Load a sound by its asset GUID.
     */
    fun loadSoundByGuid(guid: AssetGuid): SoundBuffer {
        val asset = assetDatabase?.getByGuid(guid)
            ?: throw IllegalArgumentException("Asset not found: $guid")
        return loadSound(asset.absoluteSourcePath)
    }
}
