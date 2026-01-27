package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.models.MeshPart
import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.utils.JobSystem
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import java.io.File

object AssetPool {

    private val shaderLoader = ShaderLoader(false)
    private val assimpLoader = AssimpLoader()

    private val shaders = mutableMapOf<String, Shader>()
    private val textures = mutableMapOf<String, Texture>()
    private val cubemaps = mutableMapOf<String, Cubemap>()
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

    fun getModelAsync(filePath: String, loader: com.pafoid.skate.engine.render.VAOLoader, callback: (TexturedModel) -> Unit) {
        JobSystem.runAsync {
            val preLoaded = assimpLoader.preLoadModel(filePath)
            
            JobSystem.runOnMain {
                val file = File(filePath)
                val parts = preLoaded.parts.map { p ->
                    val model = loader.loadToVAO(p.vertices, p.texCoords, p.normals, p.indices, p.vertices, p.tangents, p.colors, p.drawMode, p.texCoords1, p.joints, p.weights)
                    com.pafoid.skate.engine.models.MeshPart(model, p.material, p.inverseBindMatrices)
                }
                val texturedModel = TexturedModel(parts)
                models[file.absolutePath] = texturedModel
                callback(texturedModel)
            }
        }
    }

    fun getModel(filePath: String, loader: com.pafoid.skate.engine.render.VAOLoader): TexturedModel {
        val file = File(filePath)
        if (models.containsKey(file.absolutePath)) {
            return models[file.absolutePath]!!
        }

        val loadedParts = assimpLoader.loadModel(filePath, loader)
        val parts = loadedParts.map { loadedPart ->
            MeshPart(loadedPart.model, loadedPart.material, loadedPart.inverseBindMatrices)
        }

        val texturedModel = TexturedModel(parts)
        models[file.absolutePath] = texturedModel
        return texturedModel
    }

    fun getRawModelWithTexture(filePath: String, loader: com.pafoid.skate.engine.render.VAOLoader): Triple<RawModel, String?, java.nio.ByteBuffer?> {
        val model = getModel(filePath, loader)
        val firstPart = model.parts[0]
        return Triple(firstPart.rawModel, firstPart.material.baseColorTexture?.getFilePath(), null)
    }

    fun getCubemap(filePaths: Array<String>): Cubemap {
        val key = filePaths.joinToString("|")
        return if(cubemaps.containsKey(key)) {
            cubemaps[key]!!
        } else {
            val cubemap = Cubemap().init(filePaths)
            cubemaps[key] = cubemap
            cubemap
        }
    }

    fun getTexture(resourceName: String): Texture {
        if (textures.containsKey(resourceName)) {
            return textures[resourceName]!!
        }
        val file = File(resourceName)
        return if(textures.containsKey(file.absolutePath)) {
            textures[file.absolutePath]!!
        } else{
            val texture = Texture().init(resourceName)
            textures[file.absolutePath] = texture
            texture
        }
    }

    fun getTexture(resourceName: String, buffer: java.nio.ByteBuffer): Texture {
        return if(textures.containsKey(resourceName)) {
            textures[resourceName]!!
        } else {
            val texture = Texture().init(buffer)
            textures[resourceName] = texture
            texture
        }
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