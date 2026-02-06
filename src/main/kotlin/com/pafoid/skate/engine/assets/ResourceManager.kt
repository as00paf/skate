package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.models.MeshPart
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.editor.logs.LogLevel
import com.pafoid.skate.engine.animation.Animation
import com.pafoid.skate.engine.models.Material
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ResourceManager(
    private val shaderLoader: ShaderLoader = ShaderLoader(false),
    private val assimpLoader: AssimpLoader = AssimpLoader(),
    private val vaoLoader:VAOLoader,
    private val logger: LoggerService
) {

    private val textures = ConcurrentHashMap<String, Texture>()
    private val shaders = ConcurrentHashMap<String, Shader>()
    private val models = ConcurrentHashMap<String, TexturedModel>()
    private val sounds = ConcurrentHashMap<String, Sound>()
    private val animations = ConcurrentHashMap<String, MutableMap<String, Animation>>()
    
    // Loaders are now passed via constructor
    
    // --- Texture Loading ---

    suspend fun loadTexture(path: String): Texture {
        val file = File(path)
        val absolutePath = file.absolutePath
        
        textures[absolutePath]?.let { return it }

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
            val texture = Texture()
            texture.uploadToGPU(data)
            data.free()
            texture.filePath = path
            textures[absolutePath] = texture
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
        return textures[File(path).absolutePath]
    }

    // --- Shader Loading ---

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

    // --- Sound Loading ---

    fun loadSound(path: String, loops: Boolean): Sound {
        val file = File(path)
        val absolutePath = file.absolutePath

        sounds[absolutePath]?.let { return it }

        return try {
            val sound = Sound(absolutePath, loops)
            sounds[absolutePath] = sound
            sound
        } catch (e: Exception) {
            logger.logEngine("Failed to load sound: $path. Error: ${e.message}", LogLevel.ERROR)
            throw e // Sounds might not have a good default fallback yet
        }
    }

    fun getSound(path: String): Sound? {
        return sounds[File(path).absolutePath]
    }

    // --- Model Loading ---

    suspend fun loadModel(path: String): TexturedModel {
        val file = File(path)
        val absolutePath = file.absolutePath

        models[absolutePath]?.let { return it }

        return try {
             val preLoaded = withContext(Dispatchers.IO) {
                 assimpLoader.preLoadModel(path)
             }

             val textureDataMap = mutableMapOf<String, TextureData>()
                 withContext(Dispatchers.IO) {
                     preLoaded.parts.forEach { part ->
                         listOfNotNull(part.material.baseColorPath, part.material.normalMapPath, 
                            part.material.metallicRoughnessPath, part.material.aoPath, part.material.emissivePath).forEach { texPath ->
                            if (!textureDataMap.containsKey(texPath)) {
                                val buffer = part.embeddedTextures[texPath]
                                val data = if (buffer != null) Texture.loadData(buffer) else Texture.loadData(texPath)
                                if (data != null) textureDataMap[texPath] = data
                            }
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

                     val texturedModel = TexturedModel(parts, preLoaded.skeleton, preLoaded.animations)
                     models[absolutePath] = texturedModel
                     texturedModel
                 }
        } catch (e: Exception) {
            logger.logEngine("Failed to load model: $path. Error: ${e.message}", LogLevel.ERROR)
            if (path == Assets.Models.CUBE) throw RuntimeException("Critical Error: Default CUBE model not found!")
            logger.logEngine("Loading default CUBE model instead of $path", LogLevel.ERROR)
            loadModel(Assets.Models.CUBE)
        }
    }
    
    fun loadModelSync(path: String): TexturedModel {
        val file = File(path)
        val absolutePath = file.absolutePath

        models[absolutePath]?.let { return it }
        
        return try {
            // Synchronous loading (blocking)
             val preLoaded = assimpLoader.preLoadModel(path)
             
             // Process immediately
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

                 val texturedModel = TexturedModel(parts, preLoaded.skeleton, preLoaded.animations)
                 models[absolutePath] = texturedModel
                 texturedModel
        } catch (e: Exception) {
            logger.logEngine("Failed to load model: $path. Error: ${e.message}", LogLevel.ERROR)
            if (path == Assets.Models.CUBE) throw RuntimeException("Critical Error: Default CUBE model not found!")
            logger.logEngine("Loading default CUBE model instead of $path", LogLevel.ERROR)
            loadModelSync(Assets.Models.CUBE)
        }
    }
    
    fun getModel(path: String): TexturedModel? {
        return models[File(path).absolutePath]
    }

    // --- Animation Loading ---

    suspend fun loadAnimations(path: String): List<Animation> {
        val file = File(path)
        val absolutePath = file.absolutePath

        animations[absolutePath]?.values?.toList()?.let { return it }

        return try {
            val loadedAnimations = withContext(Dispatchers.IO) {
                assimpLoader.loadAnimations(path)
            }
            
            val animMap = animations.getOrPut(absolutePath) { ConcurrentHashMap() }
            loadedAnimations.forEach { anim ->
                animMap[anim.name] = anim
            }
            
            loadedAnimations
        } catch (e: Exception) {
            logger.logEngine("Failed to load animations from: $path. Error: ${e.message}", LogLevel.ERROR)
            emptyList()
        }
    }

    fun getAnimation(filePath: String, animationName: String): Animation? {
        val absolutePath = File(filePath).absolutePath
        return animations[absolutePath]?.get(animationName)
    }

    fun loadAnimationsSync(path: String): List<Animation> {
        val file = File(path)
        val absolutePath = file.absolutePath

        animations[absolutePath]?.values?.toList()?.let { return it }

        return try {
            val loadedAnimations = assimpLoader.loadAnimations(path)
            
            val animMap = animations.getOrPut(absolutePath) { ConcurrentHashMap() }
            loadedAnimations.forEach { anim ->
                animMap[anim.name] = anim
            }
            
            loadedAnimations
        } catch (e: Exception) {
            logger.logEngine("Failed to load animations from: $path. Error: ${e.message}", LogLevel.ERROR)
            emptyList()
        }
    }

    fun unloadTexture(path: String) {
        val absolutePath = File(path).absolutePath
        textures.remove(absolutePath)?.let { 
             JobSystem.runOnMain { 
                 it.destroy()
             }
        }
    }

    fun unloadModel(path: String) {
        val absolutePath = File(path).absolutePath
        models.remove(absolutePath)?.let { model ->
             JobSystem.runOnMain {
                 model.parts.forEach { part ->
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
        // Unload all
        val texKeys = textures.keys.toList()
        texKeys.forEach { unloadTexture(it) }
        
        val modelKeys = models.keys.toList()
        modelKeys.forEach { unloadModel(it) }
        
        val shaderKeys = shaders.keys.toList()
        shaderKeys.forEach { unloadShader(it) }

        animations.clear()
        
        // Sound cleanup
        sounds.values.forEach { 
            it.stop()
            it.delete()
        }
        sounds.clear()
    }
    
    fun getVAOLoader() = vaoLoader
}
