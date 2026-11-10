package com.pafoid.skate.cli

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.utils.Atlas
import com.pafoid.skate.engine.utils.AssetsResolver
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TLG-14: every sample built by the CLI (and the MCP server) saves project-relative references, so a CLI export
 * runs from another folder. Each sample is built, exported, its project folder deleted, and the pack opened the way
 * the exported game opens it.
 */
class SampleExportTest {

    private class Sample(val name: String, val build: (File) -> File)

    private companion object {
        /** Files a sample writes that can hold asset references. */
        val SAVED_FILES = setOf("scene", "skateproject", "prefab", "skmat", "json")
    }

    private val samples = listOf(
        Sample("tetris") { TetrisProjectBuilder().build(it) },
        Sample("marble-run") { MarbleRunProjectBuilder().build(it) },
        Sample("feature-showcase") { ShowcaseProjectBuilder().build(it) },
        Sample("new-project") { SkateCliService(out = {}).createProject("Blank", it) },
    )

    @Test
    fun Samples_SaveNoAbsolutePaths() {
        withTempDir { dir ->
            samples.forEach { sample ->
                val projectFile = sample.build(File(dir, sample.name).also { it.mkdirs() })
                val saved = projectFile.parentFile.walkTopDown().filter { it.extension in SAVED_FILES }.toList()
                assertTrue(saved.size >= 2, "${sample.name}: $saved")
                saved.forEach { file ->
                    val absolute = strings(Serializer().json.parseToJsonElement(file.readText())).filter(AssetPaths::isAbsolute)
                    assertEquals(emptyList(), absolute, "${sample.name}/${file.name} has machine paths")
                }
            }
        }
    }

    @Test
    fun CliExportedSamples_LoadFromAnotherFolder() {
        withTempDir { dir ->
            samples.forEach { sample ->
                val projectFile = sample.build(File(dir, "src-${sample.name}").also { it.mkdirs() })
                val out = File(dir, "out-${sample.name}")
                SkateCliService(out = {}).export(projectFile.path, out)
                projectFile.parentFile.deleteRecursively()

                val resolver = openPack(File(out, "game.bin"))
                val paths = AssetPaths().apply { pack = resolver }
                val serializer = Serializer(paths)
                val project = serializer.decodeFile<Project>(FileKind.PROJECT, text(resolver, projectFile.name))
                assertTrue(project.scenesPath.isNotEmpty(), sample.name)
                project.scenesPath.forEach { scenePath ->
                    val sceneText = text(resolver, scenePath)
                    val scene = serializer.decodeFile<Scene>(FileKind.SCENE, sceneText)
                    assertTrue(scene.children.isNotEmpty() || sample.name == "new-project", "${sample.name}: empty $scenePath")
                    val missing = references(Serializer().json.parseToJsonElement(sceneText)).filter { paths.readBytes(it) == null && !bundled(it) }
                    assertEquals(emptyList(), missing, "${sample.name}/$scenePath references missing from the pack")
                }
                assertTrue(text(resolver, project.defaultScene).isNotEmpty(), "${sample.name}: default scene")
            }
        }
    }

    /** Engine resources shipped in the game jar (e.g. `configs/skater.controller.json`); the game reads them too. */
    private fun bundled(path: String) = javaClass.classLoader.getResource(path.replace('\\', '/').trimStart('/')) != null

    private fun text(resolver: AssetsResolver, path: String): String =
        resolver.resolveBytes(path)?.toString(Charsets.UTF_8) ?: error("$path is not in the pack (${resolver.assetsAtlas.values.flatten().map { it.path }})")

    private fun openPack(bin: File): AssetsResolver {
        val bytes = bin.readBytes()
        val header = ByteBuffer.wrap(bytes, 0, 4).int + 4
        val atlas = Serializer().decode<Atlas>(bytes.copyOfRange(4, header).toString(Charsets.UTF_8))
        return AssetsResolver(Serializer(), LoggerService()).also { it.initialize(atlas, bytes, header) }
    }

    /** Every string value in [element]. */
    private fun strings(element: JsonElement): List<String> = when (element) {
        is JsonObject -> element.values.flatMap(::strings)
        is JsonArray -> element.flatMap(::strings)
        is JsonPrimitive -> if (element.isString) listOf(element.content) else emptyList()
    }

    /** Non-blank asset references (values under [AssetPaths.PATH_KEYS]) in [element]. */
    private fun references(element: JsonElement): List<String> = when (element) {
        is JsonObject -> element.flatMap { (key, value) -> if (key in AssetPaths.PATH_KEYS) strings(value) else references(value) }
        is JsonArray -> element.flatMap(::references)
        else -> emptyList()
    }.filter { it.isNotBlank() }.distinct()

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("skate-sample-export").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
