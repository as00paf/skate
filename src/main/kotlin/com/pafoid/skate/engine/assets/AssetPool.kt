package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.models.RawModel
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.utils.JobSystem
import java.io.File

object AssetPool {

    val resourceManager = ResourceManager() // Exposed for direct access if needed
    
    // Legacy maps kept for compatibility if direct map access was used (it wasn't public)
    private val cubeMapCache = mutableMapOf<String, CubeMap>()
    private val spriteSheets = mutableMapOf<String, SpriteSheet>()
    private val sounds = mutableMapOf<String, Sound>()

    fun getShader(filePath: String): Shader {
        return resourceManager.loadShaderSync(filePath)
    }

    fun getRawModel(filePath: String, loader: VAOLoader): RawModel {
        // Ignores loader! Uses ResourceManager's loader.
        // This behavior ensures centralized management.
        return resourceManager.loadModelSync(filePath).parts[0].rawModel
    }

    fun getTextureAsync(resourceName: String, callback: (Texture) -> Unit) {
        JobSystem.runAsync {
            val texture = resourceManager.loadTexture(resourceName)
            JobSystem.runOnMain {
                callback(texture)
            }
        }
    }

    fun getModelAsync(filePath: String, loader: VAOLoader, callback: (TexturedModel) -> Unit) {
        JobSystem.runAsync {
            val model = resourceManager.loadModel(filePath)
            JobSystem.runOnMain {
                callback(model)
            }
        }
    }

    fun getModel(filePath: String, loader: VAOLoader): TexturedModel {
        return resourceManager.loadModelSync(filePath)
    }

    fun getRawModelWithTexture(filePath: String, loader: VAOLoader): Triple<RawModel, String?, java.nio.ByteBuffer?> {
        val model = getModel(filePath, loader)
        val firstPart = model.parts[0]
        return Triple(firstPart.rawModel, firstPart.material.baseColorTexture?.filePath, null)
    }

    fun getCubeMap(filePaths: Array<String>): CubeMap {
        val key = filePaths.joinToString("|")
        return cubeMapCache.getOrPut(key) {
            CubeMap().init(filePaths)
        }
    }

    fun getTexture(resourceName: String, buffer: java.nio.ByteBuffer? = null): Texture {
        if (buffer != null) {
             val data = Texture.loadData(buffer) ?: throw RuntimeException("Failed to load texture from buffer")
             val texture = Texture()
             texture.uploadToGPU(data)
             data.free()
             return texture
        }
        
        return resourceManager.loadTextureSync(resourceName)
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
            // assert(false) { "Error: Tried to access SpriteSheet '$resourceName' without adding it first" }
            return null
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
        return sounds[file.absolutePath]
    }

    fun getAllSounds() = sounds.values
    
    fun clear() {
        resourceManager.clear()
        cubeMapCache.clear()
        spriteSheets.clear()
        sounds.clear()
    }
}