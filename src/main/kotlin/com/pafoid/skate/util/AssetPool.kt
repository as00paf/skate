package com.pafoid.skate.util

import com.pafoid.skate.components.Sound
import com.pafoid.skate.components.SpriteSheet
import marki.renderer.Shader
import com.pafoid.skate.core.renderer.Texture
import java.io.File

object AssetPool {

    private val shaders = mutableMapOf<String, Shader>()
    private val textures = mutableMapOf<String, Texture>()
    private val spriteSheets = mutableMapOf<String, SpriteSheet>()
    private val sounds = mutableMapOf<String, Sound>()

    fun getShader(filePath: String): Shader {
        val file = File(filePath)
        return if(shaders.containsKey(file.absolutePath)) {
            shaders[file.absolutePath]!!
        } else{
            val shader = Shader(file.absolutePath).compile()
            shaders[file.absolutePath] = shader
            shader
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

    fun addSound(soundFile: String, loops: Boolean):Sound? {
        val file = File(soundFile)
        return if(sounds.containsKey(file.absolutePath)) {
            sounds[file.absolutePath]
        } else {
            val sound = Sound(file.absolutePath, loops)
            sounds[file.absolutePath] = sound
            sound
        }
    }

    fun getSound(soundFile: String):Sound? {
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