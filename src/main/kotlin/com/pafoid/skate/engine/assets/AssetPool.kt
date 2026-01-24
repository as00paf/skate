package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.models.RawModel
import java.io.File

object AssetPool {

    private val shaderLoader = ShaderLoader(false)
    private val assimpLoader = AssimpLoader()

    private val shaders = mutableMapOf<String, Shader>()
    private val textures = mutableMapOf<String, Texture>()
    private val cubemaps = mutableMapOf<String, Cubemap>()
    private val spriteSheets = mutableMapOf<String, SpriteSheet>()
    private val sounds = mutableMapOf<String, Sound>()
    private val rawModels = mutableMapOf<String, RawModel>()

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
        val file = File(filePath)
        return if (rawModels.containsKey(file.absolutePath)) {
            rawModels[file.absolutePath]!!
        } else {
            val model = assimpLoader.loadModel(filePath, loader)
            rawModels[file.absolutePath] = model
            model
        }
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
        val file = File(resourceName)
        return if(textures.containsKey(file.absolutePath)) {
            textures[file.absolutePath]!!
        } else{
            val texture = Texture().init(resourceName)
            textures[file.absolutePath] = texture
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