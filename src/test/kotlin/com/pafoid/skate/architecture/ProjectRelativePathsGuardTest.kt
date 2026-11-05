package com.pafoid.skate.architecture

import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.render.material.MaterialAsset
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * AST-09: saved scenes reference assets relative to the project, so projects can move and be exported. These guards
 * check the default project's scenes and keep [AssetPaths.PATH_KEYS] in sync with the serializable path fields.
 */
class ProjectRelativePathsGuardTest {
    @TempDir
    lateinit var dir: File

    private fun strings(e: JsonElement, at: String = "$"): List<Pair<String, String>> = when (e) {
        is JsonObject -> e.flatMap { (k, v) -> strings(v, "$at.$k") }
        is JsonArray -> e.flatMapIndexed { i, v -> strings(v, "$at[$i]") }
        is JsonPrimitive -> if (e.isString) listOf(at to e.content) else emptyList()
    }

    @Test
    fun DefaultProjectScenes_SaveNoAbsolutePaths() {
        val root = File(dir, "Game").absoluteFile
        val assets = File(root, "Assets").also { it.mkdirs() }
        val assetsManager = mockk<AssetsManager>(relaxed = true)
        val sceneManager = mockk<SceneManager>(relaxed = true)
        val engine = mockk<Engine>(relaxed = true).also {
            every { it.assetsManager } returns assetsManager
            every { it.sceneManager } returns sceneManager
            every { it.logger } returns LoggerService()
        }
        // Loaders keep the requested path, like the real ones, so every reference reaches the saved JSON.
        every { assetsManager.loadModel(any<String>()) } answers { `3dModel`(firstArg(), skeleton = Skeleton(Bone(0, "root"), 1)) }
        every { assetsManager.getTexture(any()) } answers { Texture(filePath = firstArg()) }
        every { assetsManager.getBundledTexture(any()) } answers { Texture(filePath = firstArg()) }
        every { assetsManager.loadAnimationSync(any()) } answers { Animation("clip", emptyList(), 1f, firstArg()) }
        every { sceneManager.createNewScene(any(), any()) } answers { Scene(firstArg()) }

        val generator = PrefabsGenerator(engine).apply { projectAssetsDir = assets.absolutePath + File.separator }
        val scenes = generator.createDefaultScenes(File(root, "Scenes").also { it.mkdirs() })
        val serializer = Serializer(AssetPaths().apply { projectRoot = root })

        val absolute = scenes.flatMap { scene ->
            strings(Json.parseToJsonElement(serializer.encodeFile(FileKind.SCENE, scene)), scene.name)
                .filter { (_, value) -> AssetPaths.isAbsolute(value) }
        }
        assertTrue(scenes.isNotEmpty())
        assertTrue(absolute.isEmpty(), "absolute paths in saved default scenes:\n" + absolute.joinToString("\n") { "${it.first} = ${it.second}" })

        val skmats = root.walkTopDown().filter { it.extension == MaterialAsset.EXTENSION }.toList()
        val skmatAbsolute = skmats.flatMap { f -> MaterialAsset.load(f).textures.values.filter(AssetPaths::isAbsolute) }
        assertTrue(skmatAbsolute.isEmpty(), "absolute texture paths in .skmat: $skmatAbsolute")
    }

    @Test
    fun PathKeys_CoverEverySerializableAssetPathField() {
        val sourceRoot = Paths.get(System.getProperty("user.dir"), "src/main/kotlin/com/pafoid/skate")
        val scanned = listOf("engine/ecs", "engine/assets/data", "engine/ui", "engine/render/material", "editor/project/Project.kt")
        val field = Regex("""(?:var|val)\s+(\w*(?:[Pp]ath|[Ff]ile|[Cc]lips)\w*)\s*:\s*(?:String\??|(?:Mutable)?List<String>)""")
        val found = scanned.flatMap { rel ->
            Files.walk(sourceRoot.resolve(rel)).use { s -> s.filter { it.isRegularFile() && it.toString().endsWith(".kt") }.toList() }
        }.filter { it.readText().contains("@Serializable") }.flatMap { file ->
            file.readText().lines()
                .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.contains("@Transient") || it.contains("private ") }
                .mapNotNull { field.find(it)?.groupValues?.get(1) }
                .map { it to sourceRoot.relativize(file).invariantSeparatorsPathString }
        }
        val missing = found.filter { (name, _) -> name !in AssetPaths.PATH_KEYS }
        assertTrue(found.isNotEmpty())
        assertTrue(missing.isEmpty(), "serialized path fields not in AssetPaths.PATH_KEYS (add them, or make them @Transient): $missing")
    }
}
