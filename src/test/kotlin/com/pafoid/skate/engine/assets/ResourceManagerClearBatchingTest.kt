package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.BaseModel
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.RawModel
import com.pafoid.skate.engine.assets.data.models.TexturedModel
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import com.pafoid.skate.engine.assets.loaders.ShaderLoader
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.utils.IJobSystem
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

class ResourceManagerClearBatchingTest {

    @Test
    fun `clear batches gpu destruction into single main-thread task`() {
        val logger = mockk<LoggerService>(relaxed = true)
        val vaoLoader = mockk<VAOLoader>(relaxed = true)
        val shaderLoader = mockk<ShaderLoader>(relaxed = true)
        val assimpLoader = mockk<AssimpLoader>(relaxed = true)
        val jobSystem = RecordingJobSystem()

        val manager = ResourceManager(
            shaderLoader = shaderLoader,
            assimpLoader = assimpLoader,
            vaoLoader = vaoLoader,
            logger = logger,
            jobSystem = jobSystem
        )

        val textureA = mockk<Texture>(relaxed = true)
        val textureB = mockk<Texture>(relaxed = true)
        val shaderA = mockk<Shader>(relaxed = true)
        val shaderB = mockk<Shader>(relaxed = true)
        val soundA = mockk<SoundBuffer>(relaxed = true)
        val soundB = mockk<SoundBuffer>(relaxed = true)

        val modelA = TexturedModel(
            listOf(
                MeshPart(RawModel(11, 3), Material()),
                MeshPart(RawModel(12, 3), Material())
            )
        )
        val modelB = TexturedModel(
            listOf(
                MeshPart(RawModel(21, 3), Material())
            )
        )

        getField<ConcurrentHashMap<String, Texture>>(manager, "textures").apply {
            put("tex-a", textureA)
            put("tex-b", textureB)
        }
        getField<ConcurrentHashMap<String, Shader>>(manager, "shaders").apply {
            put("shader-a", shaderA)
            put("shader-b", shaderB)
        }
        getField<ConcurrentHashMap<String, BaseModel>>(manager, "models").apply {
            put("model-a", modelA)
            put("model-b", modelB)
        }
        getField<ConcurrentHashMap<String, SoundBuffer>>(manager, "sounds").apply {
            put("sound-a", soundA)
            put("sound-b", soundB)
        }
        getField<ConcurrentHashMap<String, Set<String>>>(manager, "modelDependencies")["model-a"] = setOf("tex-a")
        getField<CopyOnWriteArrayList<String>>(manager, "lruQueue").addAll(listOf("tex-a", "tex-b"))
        getField<AtomicLong>(manager, "currentTextureMemory").set(1_048_576L)

        manager.clear()

        assertEquals(1, jobSystem.runOnMainInvocationCount)
        assertEquals(0, getField<ConcurrentHashMap<String, Texture>>(manager, "textures").size)
        assertEquals(0, getField<ConcurrentHashMap<String, BaseModel>>(manager, "models").size)
        assertEquals(0, getField<ConcurrentHashMap<String, Shader>>(manager, "shaders").size)
        assertEquals(0, getField<ConcurrentHashMap<String, SoundBuffer>>(manager, "sounds").size)
        assertEquals(0, getField<ConcurrentHashMap<String, Set<String>>>(manager, "modelDependencies").size)
        assertEquals(0, getField<CopyOnWriteArrayList<String>>(manager, "lruQueue").size)
        assertEquals(0L, getField<AtomicLong>(manager, "currentTextureMemory").get())

        verify(exactly = 1) { soundA.delete() }
        verify(exactly = 1) { soundB.delete() }
        verify(exactly = 0) { textureA.destroy() }
        verify(exactly = 0) { shaderA.destroy() }

        jobSystem.flushMainTasks()

        verify(exactly = 1) { textureA.destroy() }
        verify(exactly = 1) { textureB.destroy() }
        verify(exactly = 1) { shaderA.destroy() }
        verify(exactly = 1) { shaderB.destroy() }
        verify(exactly = 1) { vaoLoader.deleteVAO(11) }
        verify(exactly = 1) { vaoLoader.deleteVAO(12) }
        verify(exactly = 1) { vaoLoader.deleteVAO(21) }
    }

    @Test
    fun `clear preserves non-project assets when requested`() {
        val logger = mockk<LoggerService>(relaxed = true)
        val vaoLoader = mockk<VAOLoader>(relaxed = true)
        val shaderLoader = mockk<ShaderLoader>(relaxed = true)
        val assimpLoader = mockk<AssimpLoader>(relaxed = true)
        val jobSystem = RecordingJobSystem()
        val assetDatabase = mockk<AssetDatabase>(relaxed = true)
        every { assetDatabase.projectRoot } returns File("C:/Projects/Level01")

        val manager = ResourceManager(
            shaderLoader = shaderLoader,
            assimpLoader = assimpLoader,
            vaoLoader = vaoLoader,
            logger = logger,
            assetDatabase = assetDatabase,
            jobSystem = jobSystem
        )

        val projectTexture = mockk<Texture>(relaxed = true)
        projectTexture.width = 128
        projectTexture.height = 128
        val engineTexture = mockk<Texture>(relaxed = true)
        engineTexture.width = 64
        engineTexture.height = 64
        val projectShader = mockk<Shader>(relaxed = true)
        val engineShader = mockk<Shader>(relaxed = true)
        val projectModel = TexturedModel(listOf(MeshPart(RawModel(31, 3), Material())))
        val engineModel = TexturedModel(listOf(MeshPart(RawModel(41, 3), Material())))

        getField<ConcurrentHashMap<String, Texture>>(manager, "textures").apply {
            put("C:/Projects/Level01/Textures/proj.png", projectTexture)
            put("C:/Engine/assets/textures/ui.png", engineTexture)
        }
        getField<ConcurrentHashMap<String, Shader>>(manager, "shaders").apply {
            put("C:/Projects/Level01/Shaders/proj.glsl", projectShader)
            put("C:/Engine/assets/shaders/default.glsl", engineShader)
        }
        getField<ConcurrentHashMap<String, BaseModel>>(manager, "models").apply {
            put("C:/Projects/Level01/Models/proj.glb", projectModel)
            put("C:/Engine/assets/models/cube.glb", engineModel)
        }
        getField<ConcurrentHashMap<String, Set<String>>>(manager, "modelDependencies").apply {
            put("C:/Projects/Level01/Models/proj.glb", setOf("C:/Projects/Level01/Textures/proj.png"))
            put("C:/Engine/assets/models/cube.glb", setOf("C:/Engine/assets/textures/ui.png"))
        }
        getField<CopyOnWriteArrayList<String>>(manager, "lruQueue").addAll(
            listOf("C:/Projects/Level01/Textures/proj.png", "C:/Engine/assets/textures/ui.png")
        )

        manager.clear(preserveNonProjectAssets = true)
        jobSystem.flushMainTasks()

        val remainingTextures = getField<ConcurrentHashMap<String, Texture>>(manager, "textures")
        val remainingShaders = getField<ConcurrentHashMap<String, Shader>>(manager, "shaders")
        val remainingModels = getField<ConcurrentHashMap<String, BaseModel>>(manager, "models")
        assertEquals(1, remainingTextures.size)
        assertEquals(engineTexture, remainingTextures["C:/Engine/assets/textures/ui.png"])
        assertEquals(1, remainingShaders.size)
        assertEquals(engineShader, remainingShaders["C:/Engine/assets/shaders/default.glsl"])
        assertEquals(1, remainingModels.size)
        assertEquals(engineModel, remainingModels["C:/Engine/assets/models/cube.glb"])

        verify(exactly = 1) { projectTexture.destroy() }
        verify(exactly = 0) { engineTexture.destroy() }
        verify(exactly = 1) { projectShader.destroy() }
        verify(exactly = 0) { engineShader.destroy() }
        verify(exactly = 1) { vaoLoader.deleteVAO(31) }
        verify(exactly = 0) { vaoLoader.deleteVAO(41) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(target: Any, fieldName: String): T {
        val field = target::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target) as T
    }

    private class RecordingJobSystem : IJobSystem {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        private val mainTasks = mutableListOf<suspend CoroutineScope.() -> Unit>()

        var runOnMainInvocationCount: Int = 0
            private set

        override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined

        override fun isMainThread(): Boolean = false

        override fun update() = Unit

        override fun runAsync(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

        override fun runOnMain(block: suspend CoroutineScope.() -> Unit): Job {
            runOnMainInvocationCount++
            mainTasks += block
            return Job().apply { complete() }
        }

        override fun <T> runAsyncDeferred(block: suspend CoroutineScope.() -> T): Deferred<T> =
            scope.async(block = block)

        override fun runIO(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

        override fun destroy() {
            scope.cancel()
        }

        fun flushMainTasks() {
            val pending = mainTasks.toList()
            mainTasks.clear()
            pending.forEach { task ->
                runBlocking { scope.task() }
            }
        }
    }
}
