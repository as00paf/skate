package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetId
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.pack.PackBuilder
import com.pafoid.skate.engine.assets.pack.PackInput
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.RenderComponent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

/** AST-27 (AST-26 §3.1, §4.6): `engine:` items, narrower model companions, shaders and dedupe in exported packs. */
class EngineDefaultsPackingTest {
    @TempDir
    lateinit var dir: File

    private val engine get() = File(dir, "engine")
    private val projectDir get() = File(dir, "Game").also { ProjectLayout.create(it) }
    private val assets get() = ProjectLayout.assetsDir(projectDir)

    private fun engineFile(logical: String, text: String) = File(engine, "assets/$logical").apply { parentFile.mkdirs(); writeText(text) }

    /** A project whose main scene shows [models] (absolute paths or `engine:` references). */
    private fun project(vararg models: String): Project {
        val paths = AssetPaths().apply {
            projectRoot = projectDir.absoluteFile
            engineDefaults = EngineDefaultResolver(engine) { projectRoot }
        }
        val scene = Scene("Main").apply {
            models.forEachIndexed { i, m -> addChild(GameObject("M$i").addComponent(RenderComponent(`3dModel`(m)))) }
        }
        val sceneFile = File(ProjectLayout.scenesDir(projectDir), "Main.scene")
        sceneFile.writeText(Serializer(paths).encodeFile(FileKind.SCENE, scene))
        val file = File(projectDir, "Game.skateproject").apply { writeText("""{"iconPath":"${EngineDefaults.Textures.APP_ICON}"}""") }
        return Project(name = "Game", projectPath = file.absolutePath, defaultScene = sceneFile.absolutePath,
            scenesPath = listOf(sceneFile.absolutePath), iconPath = EngineDefaults.Textures.APP_ICON)
    }

    private fun packer() = AssetsPacker(LoggerService(), gameStringsDir = File(dir, "no-strings"), engineRoot = engine)

    /** Opens the written pack like the exported game does and returns its paths and a reader. */
    private fun openPack(out: File): AssetPaths {
        val bin = File(out, AssetsPacker.BIN_FILE).readBytes()
        val headerSize = ByteBuffer.wrap(bin.copyOfRange(0, Int.SIZE_BYTES)).int + Int.SIZE_BYTES
        val atlas = Serializer().decode<Atlas>(bin.copyOfRange(Int.SIZE_BYTES, headerSize).toString(Charsets.UTF_8))
        val resolver = AssetsResolver(Serializer(), LoggerService()).apply { initialize(atlas, bin, headerSize) }
        return AssetPaths().apply { pack = resolver; engineDefaults = EngineDefaultResolver(File(dir, "no-engine")) }
    }

    @Test
    fun EngineReferences_ArePackedUnderTheReference_FromTheEngineOrTheOverride() {
        engineFile("Models/cube.obj", "engine cube")
        engineFile("Models/sphere.obj", "engine sphere")
        engineFile("Textures/app_icon.png", "icon")
        val override = File(assets, "Models/props/sphere.obj").apply { parentFile.mkdirs(); writeText("my sphere") }
        AssetMeta.write(override, AssetMeta(guid = AssetId.generate(), settings = mapOf(EngineDefaults.META_SOURCE to "Models/sphere.obj")))
        val out = File(dir, "out")

        assertTrue(packer().pack(project(EngineDefaults.Models.CUBE, override.absolutePath), outputDir = out))

        val game = openPack(out)
        assertEquals("engine cube", game.readText(EngineDefaults.Models.CUBE))
        assertEquals("my sphere", game.readText(EngineDefaults.Models.SPHERE), "the override is what ships")
        assertEquals("icon", game.readText(EngineDefaults.Textures.APP_ICON))
        assertEquals("engine cube", File(game.localPath(EngineDefaults.Models.CUBE)).readText())
        val manifest = File(out, AssetsPacker.MANIFEST_FILE).readText()
        assertFalse("engine:Models/bank.obj" in manifest, "unreferenced defaults are not packed")
    }

    @Test
    fun Obj_PacksItsMtlAndMaps_NotUnrelatedImages() {
        val modelDir = File(assets, "Models/crate").apply { mkdirs() }
        val obj = File(modelDir, "crate.obj").apply { writeText("mtllib crate.mtl\nv 0 0 0\n") }
        File(modelDir, "crate.mtl").writeText("newmtl wood\nmap_Kd textures/wood.png\nmap_Bump -bm 0.5 textures/wood_n.png\n")
        File(modelDir, "textures").mkdirs()
        listOf("wood.png", "wood_n.png", "unrelated.png").forEach { File(modelDir, "textures/$it").writeText(it) }
        File(modelDir, "loose.png").writeText("x")

        val names = packer().selectFiles(project(obj.absolutePath)).map { it.name }.toSet()

        assertTrue(names.containsAll(setOf("crate.obj", "crate.mtl", "wood.png", "wood_n.png")), "$names")
        assertFalse("unrelated.png" in names || "loose.png" in names, "$names")
    }

    @Test
    fun ProjectShaders_AreAlwaysPacked() {
        File(assets, "Shaders/my_water.glsl").apply { parentFile.mkdirs(); writeText("#type vertex") }

        assertTrue("my_water.glsl" in packer().selectFiles(project()).map { it.name })
    }

    @Test
    fun IdenticalInputs_AreStoredOnce_AndBothExtractAndVerify() {
        val bytes = "same bytes, compressible compressible compressible".toByteArray()
        val result = PackBuilder.build("G", listOf(
            PackInput("/a", "Assets/Models/cube.obj", "MODEL_3D", bytes),
            PackInput("/b", "engine:Models/cube.obj", "MODEL_3D", bytes),
            PackInput("/c", "Assets/other.txt", "TEXT", "other".toByteArray()),
        ))
        val (a, b, c) = result.manifest.entries

        assertEquals(a.position, b.position)
        assertEquals(a.storedSize, b.storedSize)
        assertEquals("engine:Models/cube.obj", b.path)
        assertTrue(c.position >= a.position + a.storedSize)
        assertEquals(a.storedSize + c.storedSize, result.data.size)
        listOf(a, b).forEach { assertTrue(PackBuilder.extract(result.data, it.position, it.storedSize, it.compressed).contentEquals(bytes)) }
        assertEquals(emptyList<String>(), PackBuilder.verify(result.data, result.manifest))
    }
}
