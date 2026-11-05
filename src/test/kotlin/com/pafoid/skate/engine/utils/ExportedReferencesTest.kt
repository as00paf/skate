package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.pack.PackManifest
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.material.MaterialAsset
import com.pafoid.skate.engine.render.material.MaterialSchema
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

/**
 * AST-08/AST-09: a project saved by the editor (project-relative references) is exported, the project folder is
 * deleted, and the game resolves controllers, `.skmat` materials with their textures, sounds and models from the
 * pack alone.
 */
class ExportedReferencesTest {
    @TempDir
    lateinit var dir: File

    private val project get() = File(dir, "Game")
    private val song = byteArrayOf(9, 8, 7, 6)

    private fun write(rel: String, bytes: ByteArray) = File(project, rel).also { it.parentFile.mkdirs(); it.writeBytes(bytes) }

    /** Creates the project files and saves the scene the way the editor does (references made project-relative). */
    private fun saveProject(): Project {
        val albedo = write("Textures/rock.png", byteArrayOf(1, 2, 3))
        write("Textures/rock_n.png", byteArrayOf(4, 5, 6))
        write("Textures/unused.png", byteArrayOf(7))
        val sky = write("Textures/sky.png", byteArrayOf(5))
        val model = write("Assets/Models/board.obj", "mtllib board.mtl".toByteArray())
        write("Assets/Models/board.mtl", "newmtl grip".toByteArray())
        val controller = write("Assets/Controllers/skater.controller.json", """{"states":[]}""".toByteArray())
        val sound = write("Assets/Music/song.ogg", song)
        val skmat = File(project, "Assets/Materials/rock.${MaterialAsset.EXTENSION}").also { it.parentFile.mkdirs() }
        val root = project.absoluteFile
        MaterialAsset(
            name = "Rock",
            params = mapOf(MaterialSchema.Companion.Param.BASE_COLOR to listOf(0.2f, 0.4f, 0.6f, 1f)),
            textures = mapOf(
                MaterialSchema.Companion.Tex.BASE_COLOR to AssetPaths.relativize(albedo.absolutePath, root),
                MaterialSchema.Companion.Tex.NORMAL to "Textures/rock_n.png",
            ),
        ).save(skmat)

        val board = GameObject("Board")
            .addComponent(RenderComponent(`3dModel`(model.absolutePath), materialPath = skmat.absolutePath))
            .addComponent(Animator().apply { controllerPath = controller.absolutePath })
            .addComponent(AudioComponent(soundFilePath = sound.absolutePath))
        val scene = Scene("Main").apply {
            addComponent(EnvironmentComponent(skyTexture = Texture(filePath = sky.absolutePath)))
            addChild(board)
        }
        val sceneFile = File(project, "Scenes/Main.scene").also { it.parentFile.mkdirs() }
        sceneFile.writeText(Serializer(AssetPaths().apply { projectRoot = root }).encodeFile(FileKind.SCENE, scene))
        val file = File(project, "Game.skateproject").also { it.writeText("{}") }
        return Project(name = "Game", projectPath = file.absolutePath, defaultScene = sceneFile.absolutePath, scenesPath = listOf(sceneFile.absolutePath))
    }

    /** Exports, moves `game.bin` out, deletes the project and opens the pack like the exported game does. */
    private fun exportAndOpenPack(): Pair<AssetPaths, AssetsResolver> {
        val options = ExportOptions(target = ExportTarget.PACK_ONLY)
        assertTrue(ProjectExporter(mockk<LoggerService>(relaxed = true), File(dir, "no-strings")).export(saveProject(), options).success)
        val bin = File(dir, "game.bin").also { File(project, "builds/game.bin").copyTo(it) }.readBytes()
        File(project, "builds/manifest.json").copyTo(File(dir, "manifest.json"))
        project.deleteRecursively()

        val header = ByteBuffer.wrap(bin, 0, 4).int + 4
        val atlas = Serializer().decode<Atlas>(bin.copyOfRange(4, header).toString(Charsets.UTF_8))
        val resolver = AssetsResolver(Serializer(), mockk(relaxed = true)).also { it.initialize(atlas, bin, header) }
        return AssetPaths().apply { pack = resolver } to resolver
    }

    @Test
    fun Export_PacksEveryReferenceUnderItsProjectRelativePath() {
        val (_, resolver) = exportAndOpenPack()
        val manifest = Serializer().json.decodeFromString(PackManifest.serializer(), File(dir, "manifest.json").readText())
        val paths = manifest.entries.map { it.path }
        listOf(
            "Assets/Materials/rock.skmat", "Textures/rock.png", "Textures/rock_n.png", "Textures/sky.png",
            "Assets/Controllers/skater.controller.json", "Assets/Music/song.ogg", "Assets/Models/board.obj", "Assets/Models/board.mtl",
        ).forEach { assertTrue(it in paths, "$it missing from $paths") }
        assertFalse("Textures/unused.png" in paths)
        assertTrue(resolver.assetsAtlas.values.flatten().none { AssetPaths.isAbsolute(it.path) }, "the pack holds no machine paths")
    }

    @Test
    fun ExportedGame_ResolvesControllersMaterialsSoundsTexturesAndModels() {
        val (paths, resolver) = exportAndOpenPack()
        val scene = Serializer(paths).decodeFile<Scene>(FileKind.SCENE, resolver.resolveBytes("Scenes/Main.scene")!!.toString(Charsets.UTF_8))
        val board = scene.children.single()

        val render = board.getComponent<RenderComponent>()!!
        assertEquals("Assets/Materials/rock.skmat", render.materialPath)
        val textures = mutableListOf<String>()
        assertNull(render.resolveMaterialAsset(paths::readText) { p -> paths.readBytes(p)?.let { textures += p; Texture(filePath = p) } })
        assertEquals(0.4f, render.material!!.baseColor.y, 1e-6f)
        assertEquals(listOf("Textures/rock.png", "Textures/rock_n.png"), textures.sorted())

        assertEquals("""{"states":[]}""", paths.readText(board.getComponent<Animator>()!!.controllerPath!!))

        val sound = File(paths.localPath(board.getComponent<AudioComponent>()!!.soundFilePath))
        assertArrayEquals(song, sound.readBytes(), "sounds are extracted to a local file for the audio streamer")

        assertArrayEquals(byteArrayOf(5), paths.readBytes(scene.getComponent<EnvironmentComponent>()!!.skyTexture!!.filePath!!))

        val model = File(paths.localPath(render.model!!.path))
        assertTrue(model.isFile && File(model.parentFile, "board.mtl").isFile, "a model is extracted with its companions")
    }

    @Test
    fun Resolver_FindsRelativePathsWrittenDifferently() {
        val (_, resolver) = exportAndOpenPack()
        assertNotNull(resolver.find("Assets/Materials/rock.skmat"))
        assertNotNull(resolver.find("Assets\\Materials\\rock.skmat"))
        assertNotNull(resolver.find("./Assets/Materials/../Materials/rock.skmat"))
        assertNull(resolver.find("Assets/Materials/missing.skmat"))
        assertNull(resolver.find(File(dir, "elsewhere/rock.skmat").absolutePath), "absolute paths do not match by name")
        assertNull(resolver.resolveBytes("Textures/unused.png"))
    }
}
