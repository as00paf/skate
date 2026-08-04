package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.assets.loaders.ShaderLoader
import com.pafoid.skate.engine.assets.loaders.SoundLoader
import com.pafoid.skate.engine.assets.loaders.TextureLoader
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.utils.AssetsResolver
import com.pafoid.skate.engine.utils.Atlas
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class AssetsManager(
    private val serializer: Serializer,
    private val logger: LoggerService
) {
    val vaoLoader: VAOLoader = VAOLoader()

    val assetsResolver = AssetsResolver(serializer, logger)

    private val shaderLoader = ShaderLoader(false)
    private val textureLoader = TextureLoader()
    private val assimpLoader = AssimpLoader(textureLoader, vaoLoader)
    private val animationLoader = AnimationLoader()
    private val soundLoader = SoundLoader(logger)

    private val textures = ConcurrentHashMap<String, Texture>()
    private val shaders = ConcurrentHashMap<String, Shader>()
    private val models = ConcurrentHashMap<String, TexturedModel>()
    private val sounds = ConcurrentHashMap<String, SoundBuffer>()
    private val animations = ConcurrentHashMap<String, Animation>()

    fun initAssetsResolver(assetsAtlas: Atlas, binData: ByteArray, headerOffset: Int) {
        assetsResolver.initialize(assetsAtlas, binData, headerOffset)
    }

    inline fun <reified T> resolve(path: String): T? {
        return assetsResolver.resolve<T>(path)
    }

    fun resolveModel(path: String): TexturedModel? {
        val data = assetsResolver.resolveData(path) ?: return null
        val model = assimpLoader.loadModel(data, path)
        models[path] = model
        return model
    }

    fun resolveTexture(path: String): Texture? {
        val data = assetsResolver.resolveData(path) ?: return null
        val texture = textureLoader.loadFromBuffer(data)
        textures[path] = texture
        return texture
    }

    fun getTexture(path: String): Texture {
        val absolutePath = File(path).absolutePath
        textures[absolutePath]?.let { return it }

        val texture = try {
            textureLoader.loadFromFile(path)
        } catch (e: Exception) {
            logger.log("Failed to load texture : $path. Error: ${e.message}. Loading default texture.", LogLevel.ERROR)
            textureLoader.loadFromFile((Assets.Textures.DEFAULT))// TODO: should be bundled asset
        }
        textures[absolutePath] = texture
        return texture
    }

    fun getBundledTexture(path: String): Texture? {
        val result = AssetsManager::class.java.getResourceAsStream(path)?.readAllBytes() ?: return null
        val data = MemoryUtil.memAlloc(result.size)
        data.put(result)
        data.flip()

        val texture = textureLoader.loadFromBuffer(data)
        textures[path] = texture
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
            val sound = soundLoader.load(path)
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

    fun loadModel(modelData: ByteArray, filePath: String): TexturedModel {
        return try {
            val model = assimpLoader.loadModel(modelData, filePath)
            models[model.path] = model
            model
        } catch (e: Exception) {
            logger.log("Failed to load model from byte data. Error: ${e.message}", LogLevel.ERROR)
            loadModel(Assets.Models.CUBE)
        }
    }

    fun loadModel(path: String): TexturedModel {
        val file = File(path)
        val absolutePath = file.absolutePath

        models[absolutePath]?.let { return it }
        
        return try {
            val model = assimpLoader.loadModel(absolutePath)
            models[absolutePath] = model
            model
        } catch (e: Exception) {
            logger.log("Failed to load model: $path. Error: ${e.message}", LogLevel.ERROR)
            if (path == Assets.Models.CUBE) throw RuntimeException("Critical Error: Default CUBE model not found!")
            logger.log("Loading default CUBE model instead of $path", LogLevel.ERROR)
            loadModel(Assets.Models.CUBE)
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
            val loadedAnimation = animationLoader.loadAnimations(path, skeleton)[0]
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
