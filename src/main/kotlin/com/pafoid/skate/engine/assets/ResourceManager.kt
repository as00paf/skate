package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.models.MeshPart
import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.engine.assets.Assets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ResourceManager(
    private val shaderLoader: ShaderLoader = ShaderLoader(false),
    private val assimpLoader: AssimpLoader = AssimpLoader(),
    private val objLoader: ObjLoader = ObjLoader()
) {

    private val textures = ConcurrentHashMap<String, Texture>()
    private val shaders = ConcurrentHashMap<String, Shader>()
    private val models = ConcurrentHashMap<String, TexturedModel>()
    private val sounds = ConcurrentHashMap<String, Sound>()

    // Central VAO Loader for models managed by this manager
    private val vaoLoader = VAOLoader()
    
    // Loaders are now passed via constructor
    
    // --- Texture Loading ---

    suspend fun loadTexture(path: String): Texture {
        val file = File(path)
        val absolutePath = file.absolutePath
        
        textures[absolutePath]?.let { return it }

        val data = withContext(Dispatchers.IO) {
            Texture.loadData(path) ?: throw RuntimeException("Failed to load texture: $path")
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
    
    fun loadTextureSync(path: String): Texture {
        val file = File(path)
        val absolutePath = file.absolutePath
        
        textures[absolutePath]?.let { return it }

        val data = Texture.loadData(path) ?: throw RuntimeException("Failed to load texture: $path")
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

        return withContext(JobSystem.Main) {
            val shader = shaderLoader.loadShader(path)
            shaders[absolutePath] = shader
            shader
        }
    }

    fun loadShaderSync(path: String): Shader {
        val file = File(path)
        val absolutePath = file.absolutePath

        shaders[absolutePath]?.let { return it }

        val shader = shaderLoader.loadShader(path)
        shaders[absolutePath] = shader
        return shader
    }
    
    fun getShader(path: String): Shader? {
        return shaders[File(path).absolutePath]
    }

    // --- Model Loading ---

    suspend fun loadModel(path: String): TexturedModel {
        val file = File(path)
        val absolutePath = file.absolutePath

        models[absolutePath]?.let { return it }

        return if (path.lowercase().endsWith(".obj")) {
            withContext(JobSystem.Main) {
                val rawModel = objLoader.loadObjModel(path, vaoLoader)
                val whiteTex = loadTexture(Assets.Textures.WHITE) 
                val parts = listOf(MeshPart(rawModel, com.pafoid.skate.engine.models.Material(baseColorTexture = whiteTex), emptyList()))
                val model = TexturedModel(parts)
                models[absolutePath] = model
                model
            }
        } else {
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
        }
    }
    
    fun loadModelSync(path: String): TexturedModel {
        val file = File(path)
        val absolutePath = file.absolutePath

        models[absolutePath]?.let { return it }
        
        // Synchronous loading (blocking)
        if (path.lowercase().endsWith(".obj")) {
            val rawModel = objLoader.loadObjModel(path, vaoLoader)
            val whiteTex = loadTextureSync(Assets.Textures.WHITE) 
            val parts = listOf(MeshPart(rawModel, com.pafoid.skate.engine.models.Material(baseColorTexture = whiteTex), emptyList()))
            val model = TexturedModel(parts)
            models[absolutePath] = model
            return model
        } else {
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
             return texturedModel
        }
    }
    
    fun getModel(path: String): TexturedModel? {
        return models[File(path).absolutePath]
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
        
        sounds.clear() // TODO: Sound cleanup
    }
    
    fun getVAOLoader() = vaoLoader
}
