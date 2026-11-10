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
import com.pafoid.skate.engine.render.shader.ShaderLibrary
import com.pafoid.skate.engine.render.shader.ShaderSourceResolver
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

    /** Resolves project-relative references (editor) and packed ones (exported game); see [AssetPaths]. */
    val paths: AssetPaths get() = serializer.assetPaths

    /** Text of the asset at [path] from the pack or the project, or null. */
    fun readText(path: String): String? = paths.readText(path)

    // Shaders (AST-26 §5, REN-31): engine:Shaders/... with project overrides, includes resolved against their
    // includer, one reference-keyed cache, and hot reload of the files each program was built from.
    private val shaderSources = ShaderSourceResolver(
        engineDefaults = { paths.engineDefaults },
        readPacked = { path -> paths.pack?.takeIf { it.initialized && it.find(path) != null }?.resolveBytes(path) },
        projectFile = { path -> File(paths.absolute(path)) },
        onError = { logger.log(it, LogLevel.ERROR) },
    )
    private val shaderLoader = ShaderLoader(false, logger, shaderSources)
    private val shaders = ShaderLibrary<Shader>(
        load = { ref, defines -> shaderLoader.load(ref, defines).let { ShaderLibrary.Loaded(it.shader, it.dependencies) } },
        loadEngine = { ref, defines -> shaderLoader.loadEngine(ref, defines).let { ShaderLibrary.Loaded(it.shader, it.dependencies) } },
        swap = { old, fresh -> old.replaceWith(fresh) },
        destroy = { it.destroy() },
        log = { logger.log(it, LogLevel.ERROR) },
    )
    private val shaderReloader = ShaderHotReloader(
        fingerprint = shaderSources::fingerprint,
        folderSignature = ::shaderOverrideFolderSignature,
        onFolderChanged = { paths.engineDefaults.invalidate() },
    ) { changed ->
        logger.log("Shader sources changed: ${changed.joinToString()}", LogLevel.INFO)
        shaders.reload(changed)
    }
    private var watchedShaderVersion = -1
    private val textureLoader = TextureLoader()
    private val assimpLoader = AssimpLoader(textureLoader, vaoLoader)
    private val animationLoader = AnimationLoader()
    private val soundLoader = SoundLoader(logger)

    private val textures = ResourceCache<Texture> { textureLoader.delete(it) }
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
    fun requestTexture(requested: String): AssetTicket<Texture> {
        val path = paths.localPath(requested)
        val absolutePath = File(path).absolutePath
        val placeholder = runCatching { getBundledTexture(Assets.Classpath.DEFAULT_TEXTURE) }.getOrNull()
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
    fun requestModel(requested: String): AssetTicket<`3dModel`> {
        val path = paths.localPath(requested)
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
        paths.pack = assetsResolver
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

    fun getTexture(requested: String): Texture {
        val path = paths.localPath(requested)
        val absolutePath = File(path).absolutePath
        textures[absolutePath]?.let { return it }

        val texture = try {
            textureLoader.loadFromFile(path)
        } catch (e: Exception) {
            logger.log("Failed to load texture : $path. Error: ${e.message}. Loading default texture.", LogLevel.ERROR)
            getBundledTexture(Assets.Classpath.DEFAULT_TEXTURE)
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
        @Suppress("NAME_SHADOWING") val path = paths.localPath(path)
        val key = File(path).absolutePath
        textures[key] ?: textureLoader.loadFromFile(path).also { textures[key] = it }
    }

    fun loadModelResult(path: String): AssetResult<`3dModel`> = AssetResult.attempt(path) {
        @Suppress("NAME_SHADOWING") val path = paths.localPath(path)
        val key = File(path).absolutePath
        models[key] ?: assimpLoader.loadModel(key).also { models[key] = it }
    }

    fun loadSoundResult(path: String): AssetResult<SoundBuffer> = AssetResult.attempt(path) {
        @Suppress("NAME_SHADOWING") val path = paths.localPath(path)
        val key = File(path).absolutePath
        sounds[key] ?: soundLoader.load(path).also { sounds[key] = it }
    }

    fun loadShaderResult(path: String): AssetResult<Shader> = AssetResult.attempt(path) { shaders.get(path) }

    // Unloading frees the native resources (GL textures/VAOs/programs, AL buffers).
    fun unloadTexture(path: String): Boolean = textures.unload(File(path).absolutePath) || textures.unload(path)
    fun unloadModel(path: String): Boolean = models.unload(File(path).absolutePath) || models.unload(path)
    fun unloadSound(path: String): Boolean = sounds.unload(File(path).absolutePath) || sounds.unload(path)
    fun unloadShader(path: String): Boolean = shaders.unload(path)
    fun unloadAnimation(path: String): Boolean = animations.unload(File(path).absolutePath) || animations.unload(path)

    /** Frees everything this manager owns. Call on shutdown or full project unload, on the GL/AL thread. */
    fun unloadAll() {
        textures.clear(); models.clear(); sounds.clear(); animations.clear()
        shaders.disposeAll()
    }

    fun hasTexture(path: String): Boolean {
        return textures[path] != null
    }

    /**
     * The permutation of shader [path] compiled with [features], compiled on first use. [path] is an engine shader
     * (`engine:Shaders/x.glsl`, or the legacy `/shaders/x.glsl`) or a project shader path (AST-26 §5.3).
     */
    fun getShaderVariant(path: String, features: Collection<ShaderFeature>): Shader =
        shaders.get(path, features.mapTo(sortedSetOf()) { it.name })

    /**
     * Checks the files the loaded shaders were built from and recompiles, in place, the programs whose sources
     * changed (AST-26 §5.5). Call once per frame on the GL thread; it does its file checks at most every 500 ms. An
     * exported game reads its shaders from the pack and does not watch.
     */
    fun pollShaderChanges() {
        if (paths.pack?.initialized == true) return
        if (watchedShaderVersion != shaders.dependencyVersion) {
            val ids = shaders.dependencies()
            shaderReloader.retain(ids)
            shaderReloader.watch(ids)
            watchedShaderVersion = shaders.dependencyVersion
        }
        shaderReloader.poll()
    }

    /** Re-resolves and recompiles every loaded shader in place (a project was opened: its overrides changed). */
    fun reloadShaders() {
        paths.engineDefaults.invalidate()
        shaders.reloadAll()
    }

    /**
     * The shader [path] (see [getShaderVariant]). A broken engine shader override falls back to the engine's version
     * of the same shader, with an error in the log; a missing or broken project shader throws.
     */
    fun getShader(path: String): Shader = shaders.get(path)

    /** True when engine shader [path] runs the engine's version because its project override failed or is stale. */
    fun shaderUsesEngineFallback(path: String): Boolean = shaders.usesFallback(path)

    /** `.meta` files in the project's `Assets/Shaders`: a new one may be a new override (no registry events yet). */
    private fun shaderOverrideFolderSignature(): String {
        val dir = paths.projectRoot?.let { File(ProjectLayout.assetsDir(it), ShaderSourceResolver.SHADERS_DIR) } ?: return ""
        val metas = dir.listFiles { f -> f.isFile && AssetMeta.isSidecar(f) } ?: return ""
        return metas.sortedBy { it.name }.joinToString("|") { "${it.name}@${it.lastModified()}" }
    }

    fun getSound(requested: String): SoundBuffer {
        val path = paths.localPath(requested)
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

    fun loadModel(requested: String): `3dModel` {
        val path = paths.localPath(requested)
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
        return models[File(paths.localPath(path)).absolutePath]
    }

    fun loadAnimationSync(requested: String): Animation {
        val path = paths.localPath(requested)
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
        val absolutePath = File(paths.localPath(filePath)).absolutePath
        return animations[absolutePath]
    }

    fun hasAnimation(filePath: String): Boolean {
        return animations[filePath] != null
    }

    fun getTextures(): List<Texture> = textures.values.toList()
    fun getAnimations(): List<Animation> = animations.values.toList()
    fun get3dModels(): List<`3dModel`> = models.values.toList()
    fun getShaders(): List<Shader> = shaders.programs()
}
