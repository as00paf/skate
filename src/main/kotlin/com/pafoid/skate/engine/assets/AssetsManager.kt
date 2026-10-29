package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.assets.loaders.ShaderLoader
import com.pafoid.skate.engine.render.shader.ShaderFeature
import com.pafoid.skate.engine.render.shader.ShaderHotReloader
import com.pafoid.skate.engine.render.shader.ShaderVariantCache
import com.pafoid.skate.engine.render.shader.ShaderVariantKey
import com.pafoid.skate.engine.assets.loaders.SoundLoader
import com.pafoid.skate.engine.assets.loaders.TextureLoader
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.JobSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.events.AssetEvent
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.utils.AssetsResolver
import com.pafoid.skate.engine.utils.Atlas
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class AssetsManager(
    private val serializer: Serializer,
    private val logger: LoggerService
) {
    val vaoLoader: VAOLoader = VAOLoader()

    val assetsResolver = AssetsResolver(serializer, logger)

    // Editor/dev runs from the repo: shader edits under resources/shaders are picked up live
    private val shaderDevRoot = File("src/main/resources").takeIf { File(it, "shaders").isDirectory }
    private val shaderLoader = ShaderLoader(false, logger, shaderDevRoot)
    private val shaderPaths = mutableMapOf<String, String>()
    private val shaderVariants = ShaderVariantCache<Shader>(
        compile = { shaderLoader.loadShader(it.path, it.defines) },
        destroy = { it.destroy() },
    )
    private val shaderReloader: ShaderHotReloader? = shaderDevRoot?.let { root ->
        ShaderHotReloader(File(root, "shaders")) { changed ->
            logger.log("Shader sources changed: ${changed.joinToString()}", LogLevel.INFO)
            reloadShaders()
        }
    }
    private val textureLoader = TextureLoader()
    private val assimpLoader = AssimpLoader(textureLoader, vaoLoader)
    private val animationLoader = AnimationLoader()
    private val soundLoader = SoundLoader(logger)

    private val textures = ResourceCache<Texture> { textureLoader.delete(it) }
    private val shaders = ResourceCache<Shader> { it.destroy() }
    private val models = ResourceCache<`3dModel`> { model -> model.mesh.forEach { vaoLoader.deleteVAO(it.vaoId) } }
    private val sounds = ResourceCache<SoundBuffer> { soundLoader.delete(it) }
    private val animations = ResourceCache<Animation> { }

    private var loadService: AssetLoadService? = null
    private var jobSystem: JobSystem? = null
    private var eventSystem: EventSystem? = null
    private val reloader = AssetReloader { key, e -> logger.log("Hot reload failed for $key: ${e.message}", LogLevel.ERROR) }
    private var lastPollNanos = 0L

    /** Enables the async API. Call once from the main thread; [JobSystem.update] must run every frame. */
    fun attachAsyncLoading(jobSystem: JobSystem, eventSystem: EventSystem?) {
        this.jobSystem = jobSystem
        this.eventSystem = eventSystem
        loadService = AssetLoadService(jobSystem, eventSystem) { logger.log(it, LogLevel.ERROR) }
    }

    /**
     * Hot reload: call every frame from the main thread. At most once per [intervalMs] it checks the files behind
     * cached textures, models and sounds and reloads changed ones in place (same object, new GPU payload),
     * publishing [AssetEvent.Reloaded]. Returns the keys whose reload was started.
     */
    fun pollHotReload(intervalMs: Long = 500, nowNanos: Long = System.nanoTime()): List<String> {
        if (nowNanos - lastPollNanos < intervalMs * 1_000_000) return emptyList()
        lastPollNanos = nowNanos
        syncWatches()
        return reloader.poll()
    }

    private fun syncWatches() {
        val live = HashSet<String>()
        fun track(keys: Set<String>, reload: (String, File) -> Unit) {
            for (key in keys) {
                val file = File(key)
                if (!file.isAbsolute || !file.isFile) continue
                live += key
                reloader.watch(key, file) { reload(key, it) }
            }
        }
        track(textures.keys, ::reloadTexture)
        track(models.keys, ::reloadModel)
        track(sounds.keys, ::reloadSound)
        reloader.watchedKeys.filter { it !in live }.forEach { reloader.unwatch(it) }
    }

    private fun reloadTexture(key: String, file: File) {
        val jobs = jobSystem ?: return
        jobs.runAsync {
            val data = try {
                textureLoader.decodeFromFile(file.path)
            } catch (e: Exception) {
                jobs.runOnMain { reloadFailed(key, e) }
                return@runAsync
            }
            jobs.runOnMain {
                val texture = textures[key]
                if (texture == null) textureLoader.discard(data) else {
                    textureLoader.replace(texture, data)
                    eventSystem?.publish(AssetEvent.Reloaded(key))
                }
            }
        }
    }

    private fun reloadModel(key: String, file: File) {
        val jobs = jobSystem ?: return
        jobs.runAsync {
            val scene = try {
                assimpLoader.importScene(file.path)
            } catch (e: Exception) {
                jobs.runOnMain { reloadFailed(key, e) }
                return@runAsync
            }
            jobs.runOnMain {
                val model = models[key]
                if (model == null) { assimpLoader.releaseScene(scene); return@runOnMain }
                try {
                    val fresh = assimpLoader.buildModel(scene, key)
                    val old = model.mesh
                    model.mesh = fresh.mesh
                    model.skeleton = fresh.skeleton
                    old.forEach { vaoLoader.deleteVAO(it.vaoId) }
                    eventSystem?.publish(AssetEvent.Reloaded(key))
                } catch (e: Exception) {
                    reloadFailed(key, e)
                }
            }
        }
    }

    private fun reloadSound(key: String, file: File) {
        val sound = sounds[key] ?: return
        val fresh = soundLoader.load(file.path)
        val oldId = sound.bufferId
        sound.bufferId = fresh.bufferId
        sound.durationInSeconds = fresh.durationInSeconds
        if (oldId != -1) org.lwjgl.openal.AL10.alDeleteBuffers(oldId)
        eventSystem?.publish(AssetEvent.Reloaded(key))
    }

    private fun reloadFailed(key: String, e: Throwable) {
        logger.log("Hot reload failed for $key: ${e.message}", LogLevel.ERROR)
        eventSystem?.publish(AssetEvent.Failed(key, e.message ?: e::class.java.simpleName))
    }

    private fun service(): AssetLoadService =
        loadService ?: throw IllegalStateException("attachAsyncLoading() was not called")

    /** Non-blocking texture request; the ticket yields the default texture until ready. Release when done. */
    fun requestTexture(path: String): AssetTicket<Texture> {
        val absolutePath = File(path).absolutePath
        val placeholder = runCatching { getBundledTexture(Assets.Bundled.DEFAULT_TEXTURE) }.getOrNull()
        return service().request(
            key = absolutePath,
            placeholder = placeholder,
            decode = { textureLoader.decodeFromFile(absolutePath) },
            upload = { textureLoader.upload(it) },
            dispose = { textureLoader.delete(it) },
            discard = { textureLoader.discard(it) }
        )
    }

    /** Non-blocking model request: the file import runs on a worker, VAO/texture creation on the main thread. */
    fun requestModel(path: String): AssetTicket<`3dModel`> {
        val absolutePath = File(path).absolutePath
        val placeholder = runCatching { loadModel(Assets.Models.CUBE) }.getOrNull()
        return service().request(
            key = absolutePath,
            placeholder = placeholder,
            decode = { assimpLoader.importScene(absolutePath) },
            upload = { assimpLoader.buildModel(it, absolutePath) },
            dispose = { model -> model.mesh.forEach { vaoLoader.deleteVAO(it.vaoId) } },
            discard = { assimpLoader.releaseScene(it) }
        )
    }

    fun <T : Any> release(ticket: AssetTicket<T>) = service().release(ticket)

    fun initAssetsResolver(assetsAtlas: Atlas, binData: ByteArray, headerOffset: Int) {
        assetsResolver.initialize(assetsAtlas, binData, headerOffset)
    }

    inline fun <reified T> resolve(path: String): T? {
        return assetsResolver.resolve<T>(path)
    }

    fun resolveModel(path: String): `3dModel`? {
        models[path]?.let { return it }
        val data = assetsResolver.resolveData(path) ?: return null
        val model = assimpLoader.loadModel(data, path)
        models[path] = model
        return model
    }

    fun resolveTexture(path: String): Texture? {
        textures[path]?.let { return it }
        val data = assetsResolver.resolveData(path) ?: return null
        val texture = textureLoader.loadFromBuffer(data)
        textures[path] = texture
        return texture
    }

    fun resolveAnimation(path: String): Animation? {
        animations[path]?.let { return it }
        val data = assetsResolver.resolveData(path) ?: return null
        val animation = animationLoader.loadAnimations(data, path)
        animations[path] = animation[0] // TODO: support multiple animations per files
        return animation[0]
    }

    fun getTexture(path: String): Texture {
        val absolutePath = File(path).absolutePath
        textures[absolutePath]?.let { return it }

        val texture = try {
            textureLoader.loadFromFile(path)
        } catch (e: Exception) {
            logger.log("Failed to load texture : $path. Error: ${e.message}. Loading default texture.", LogLevel.ERROR)
            getBundledTexture(Assets.Bundled.DEFAULT_TEXTURE)
        }
        textures[absolutePath] = texture
        return texture
    }

    fun getBundledTexture(path: String): Texture {
        textures[path]?.let { return it }
        val result = AssetsManager::class.java.getResourceAsStream(path)?.readAllBytes()
            ?: throw (MissingResourceException("Missing $path resource", "Texture", path))
        val data = MemoryUtil.memAlloc(result.size)
        data.put(result)
        data.flip()

        val texture = textureLoader.loadFromBuffer(data)
        textures[path] = texture
        return texture
    }

    // Result-returning variants: no silent fallback to default assets.
    fun loadTextureResult(path: String): AssetResult<Texture> = AssetResult.attempt(path) {
        val key = File(path).absolutePath
        textures[key] ?: textureLoader.loadFromFile(path).also { textures[key] = it }
    }

    fun loadModelResult(path: String): AssetResult<`3dModel`> = AssetResult.attempt(path) {
        val key = File(path).absolutePath
        models[key] ?: assimpLoader.loadModel(key).also { models[key] = it }
    }

    fun loadSoundResult(path: String): AssetResult<SoundBuffer> = AssetResult.attempt(path) {
        val key = File(path).absolutePath
        sounds[key] ?: soundLoader.load(path).also { sounds[key] = it }
    }

    fun loadShaderResult(path: String): AssetResult<Shader> = AssetResult.attempt(path) {
        val key = File(path).absolutePath
        shaders[key] ?: shaderLoader.loadShader(path).also { shaders[key] = it; shaderPaths[key] = path }
    }

    // Unloading frees the native resources (GL textures/VAOs/programs, AL buffers).
    fun unloadTexture(path: String): Boolean = textures.unload(File(path).absolutePath) || textures.unload(path)
    fun unloadModel(path: String): Boolean = models.unload(File(path).absolutePath) || models.unload(path)
    fun unloadSound(path: String): Boolean = sounds.unload(File(path).absolutePath) || sounds.unload(path)
    fun unloadShader(path: String): Boolean = shaders.unload(File(path).absolutePath) || shaders.unload(path)
    fun unloadAnimation(path: String): Boolean = animations.unload(File(path).absolutePath) || animations.unload(path)

    /** Frees everything this manager owns. Call on shutdown or full project unload, on the GL/AL thread. */
    fun unloadAll() {
        textures.clear(); models.clear(); sounds.clear(); shaders.clear(); animations.clear()
        shaderVariants.disposeAll(); shaderPaths.clear()
    }

    fun hasTexture(path: String): Boolean {
        return textures[path] != null
    }

    /** Returns the permutation of [path] compiled with [features], compiling it on first use. */
    fun getShaderVariant(path: String, features: Collection<ShaderFeature>): Shader {
        if (features.isEmpty()) return getShader(path)
        return shaderVariants.get(ShaderVariantKey.of(path, features))
    }

    /** Polls shader sources for edits (dev runs only); call once per frame on the GL thread. */
    fun pollShaderChanges() = shaderReloader?.poll()

    /** Recompiles all loaded shaders and variants in place; a shader that fails to compile keeps its old program. */
    fun reloadShaders() {
        for ((key, path) in shaderPaths.toMap()) {
            val old = shaders[key] ?: continue
            try {
                old.replaceWith(shaderLoader.loadShader(path))
            } catch (e: Exception) {
                logger.log("Shader reload failed for $path, keeping previous version: ${e.message}", LogLevel.ERROR)
            }
        }
        for (path in shaderVariants.allPaths()) {
            shaderVariants.reload(path) { old, fresh -> old.replaceWith(fresh) }
                .forEach { (k, e) -> logger.log("Shader variant ${k.id} reload failed: ${e.message}", LogLevel.ERROR) }
        }
    }

    fun getShader(path: String): Shader {
        val file = File(path)
        val absolutePath = file.absolutePath
        shaders[absolutePath]?.let { return it }

        return try {
            val shader = shaderLoader.loadShader(path)
            shaders[absolutePath] = shader
            shaderPaths[absolutePath] = path
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

    fun loadModel(modelData: ByteArray, filePath: String): `3dModel` {
        return try {
            val model = assimpLoader.loadModel(modelData, filePath)
            models[model.path] = model
            model
        } catch (e: Exception) {
            logger.log("Failed to load model from byte data. Error: ${e.message}", LogLevel.ERROR)
            loadModel(Assets.Models.CUBE)
        }
    }

    fun loadModel(path: String): `3dModel` {
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

    fun getModel(path: String): `3dModel`? {
        return models[File(path).absolutePath]
    }

    fun loadAnimationSync(path: String): Animation {
        val file = File(path)
        val absolutePath = file.absolutePath

        animations[absolutePath]?.let { return it }
        return try {
            val loadedAnimation = animationLoader.loadAnimations(path)[0]
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

    fun getTextures(): List<Texture> = textures.values.toList()
    fun getAnimations(): List<Animation> = animations.values.toList()
    fun get3dModels(): List<`3dModel`> = models.values.toList()
    fun getShaders(): List<Shader> = shaders.values.toList()
}
