package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.models.MeshPart
import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.utils.JobSystem
import java.io.File

object AssetPool {

    private val shaderLoader = ShaderLoader(false)
    private val assimpLoader = AssimpLoader()

    private val shaders = mutableMapOf<String, Shader>()
    private val textures = mutableMapOf<String, Texture>()
    private val cubemaps = mutableMapOf<String, CubeMap>()
    private val spriteSheets = mutableMapOf<String, SpriteSheet>()
    private val sounds = mutableMapOf<String, Sound>()
    private val models = mutableMapOf<String, TexturedModel>()

    fun getShader(filePath: String): Shader {
        val file = File(filePath)
        return if(shaders.containsKey(file.absolutePath)) {
            shaders[file.absolutePath]!!
        } else{
            val shader = shaderLoader.loadShader(filePath)
            shaders[file.absolutePath] = shader
            shader
        }
    }

    fun getRawModel(filePath: String, loader: com.pafoid.skate.engine.render.VAOLoader): RawModel {
        return getModel(filePath, loader).parts[0].rawModel
    }

    fun getTextureAsync(resourceName: String, callback: (Texture) -> Unit) {
        val file = File(resourceName)
        if (textures.containsKey(file.absolutePath)) {
            callback(textures[file.absolutePath]!!)
            return
        }

        JobSystem.runIO {
            val data = Texture.loadData(resourceName)
            if (data != null) {
                JobSystem.runOnMain {
                    val texture = Texture()
                    texture.uploadToGPU(data)
                    data.free()
                    textures[file.absolutePath] = texture
                    callback(texture)
                }
            }
        }
    }

    fun getModelAsync(filePath: String, loader: com.pafoid.skate.engine.render.VAOLoader, callback: (TexturedModel) -> Unit) {
        JobSystem.runAsync {
            val preLoaded = assimpLoader.preLoadModel(filePath)
            
            // Task 0.3: Loading textures data in background
            val textureDataMap = mutableMapOf<String, TextureData>()
            preLoaded.parts.forEach { part ->
                listOfNotNull(part.material.baseColorPath, part.material.normalMapPath, 
                    part.material.metallicRoughnessPath, part.material.aoPath, part.material.emissivePath).forEach { path ->
                    if (!textureDataMap.containsKey(path)) {
                        val buffer = part.embeddedTextures[path]
                        val data = if (buffer != null) Texture.loadData(buffer) else Texture.loadData(path)
                        if (data != null) textureDataMap[path] = data
                    }
                }
            }

            JobSystem.runOnMain {
                val file = File(filePath)
                val parts = preLoaded.parts.map { p ->
                    val model = loader.loadToVAO(p.vertices, p.texCoords, p.normals, p.indices, p.vertices, p.tangents, p.colors, p.drawMode, p.texCoords1, p.joints, p.weights)
                    
                    // Assign textures on main thread
                    val mat = p.material
                    mat.baseColorPath?.let { path -> 
                        val absPath = File(path).absolutePath
                        mat.baseColorTexture = textures[absPath] ?: Texture().apply { 
                            textureDataMap[path]?.let { uploadToGPU(it) }
                            textures[absPath] = this 
                        }
                    }
                    mat.normalMapPath?.let { path -> 
                        val absPath = File(path).absolutePath
                        mat.normalMap = textures[absPath] ?: Texture().apply { 
                            textureDataMap[path]?.let { uploadToGPU(it) }
                            textures[absPath] = this 
                        }
                    }
                    mat.metallicRoughnessPath?.let { path -> 
                        val absPath = File(path).absolutePath
                        mat.metallicRoughnessTexture = textures[absPath] ?: Texture().apply { 
                            textureDataMap[path]?.let { uploadToGPU(it) }
                            textures[absPath] = this 
                        }
                    }
                    mat.aoPath?.let { path -> 
                        val absPath = File(path).absolutePath
                        mat.aoTexture = textures[absPath] ?: Texture().apply { 
                            textureDataMap[path]?.let { uploadToGPU(it) }
                            textures[absPath] = this 
                        }
                    }
                    mat.emissivePath?.let { path -> 
                        val absPath = File(path).absolutePath
                        mat.emissiveTexture = textures[absPath] ?: Texture().apply { 
                            textureDataMap[path]?.let { uploadToGPU(it) }
                            textures[absPath] = this 
                        }
                    }

                    com.pafoid.skate.engine.models.MeshPart(model, mat, p.inverseBindMatrices)
                }
                
                // Free texture data
                textureDataMap.values.forEach { it.free() }
                
                val texturedModel = TexturedModel(parts, preLoaded.skeleton, preLoaded.animations)
                models[file.absolutePath] = texturedModel
                callback(texturedModel)
            }
        }
    }

    fun getModel(filePath: String, loader: com.pafoid.skate.engine.render.VAOLoader): TexturedModel {
        val absolutePath = File(filePath).absolutePath
        if (models.containsKey(absolutePath)) {
            return models[absolutePath]!!
        }

        val texturedModel = if (filePath.lowercase().endsWith(".obj")) {
            val rawModel = ObjLoader().loadObjModel(filePath, loader)
            val parts = listOf(MeshPart(rawModel, com.pafoid.skate.engine.models.Material(baseColorTexture = getTexture(Texture.WHITE)), emptyList()))
            TexturedModel(parts)
        } else {
            val preLoaded = assimpLoader.preLoadModel(filePath)
            val parts = preLoaded.parts.map { p ->
                val model = loader.loadToVAO(p.vertices, p.texCoords, p.normals, p.indices, p.vertices, p.tangents, p.colors, p.drawMode, p.texCoords1, p.joints, p.weights)
                
                val mat = p.material
                mat.baseColorPath?.let { mat.baseColorTexture = getTexture(it, p.embeddedTextures[it]) }
                mat.normalMapPath?.let { mat.normalMap = getTexture(it, p.embeddedTextures[it]) }
                mat.metallicRoughnessPath?.let { mat.metallicRoughnessTexture = getTexture(it, p.embeddedTextures[it]) }
                mat.aoPath?.let { mat.aoTexture = getTexture(it, p.embeddedTextures[it]) }
                mat.emissivePath?.let { mat.emissiveTexture = getTexture(it, p.embeddedTextures[it]) }
                
                MeshPart(model, mat, p.inverseBindMatrices)
            }
            TexturedModel(parts, preLoaded.skeleton, preLoaded.animations)
        }

        models[absolutePath] = texturedModel
        return texturedModel
    }

    fun getRawModelWithTexture(filePath: String, loader: com.pafoid.skate.engine.render.VAOLoader): Triple<RawModel, String?, java.nio.ByteBuffer?> {
        val model = getModel(filePath, loader)
        val firstPart = model.parts[0]
        return Triple(firstPart.rawModel, firstPart.material.baseColorTexture?.filePath, null)
    }

    fun getCubemap(filePaths: Array<String>): CubeMap {
        val key = filePaths.joinToString("|")
        return if(cubemaps.containsKey(key)) {
            cubemaps[key]!!
        } else {
            val cubemap = CubeMap().init(filePaths)
            cubemaps[key] = cubemap
            cubemap
        }
    }

    fun getTexture(resourceName: String, buffer: java.nio.ByteBuffer? = null): Texture {
        val absolutePath = if (resourceName.startsWith("Embedded::")) resourceName else File(resourceName).absolutePath
        if (textures.containsKey(absolutePath)) {
            return textures[absolutePath]!!
        }
        
        val data = if (buffer != null) Texture.loadData(buffer) else Texture.loadData(resourceName)
        if (data != null) {
            val texture = Texture()
            texture.uploadToGPU(data)
            data.free()
            texture.filePath = resourceName // Store original path for identification
            textures[absolutePath] = texture
            return texture
        }
        
        throw RuntimeException("Failed to load texture: $resourceName")
    }

    fun addSpriteSheet(resourceName: String, spriteSheet: SpriteSheet) {
        val file = File(resourceName)
        if(!spriteSheets.containsKey(file.absolutePath)) {
            spriteSheets[file.absolutePath] = spriteSheet
        }
    }

    fun getSpriteSheet(resourceName: String): SpriteSheet? {
        val file = File(resourceName)
        if(!spriteSheets.containsKey(file.absolutePath)) {
            assert(false) { "Error: Tried to access SpriteSheet '$resourceName' without adding it first" }
        }
        return spriteSheets[file.absolutePath]
    }

    fun addSound(soundFile: String, loops: Boolean): Sound? {
        val file = File(soundFile)
        return if(sounds.containsKey(file.absolutePath)) {
            sounds[file.absolutePath]
        } else {
            val sound = Sound(file.absolutePath, loops)
            sounds[file.absolutePath] = sound
            sound
        }
    }

    fun getSound(soundFile: String): Sound? {
        val file = File(soundFile)
        if(sounds.containsKey(file.absolutePath)) {
            return sounds[file.absolutePath]
        } else {
            assert(false) {"Sound file not added $soundFile"}
        }

        return null
    }

    fun getAllSounds() = sounds.values
}