package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.audio.WaterAudio
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.ecs.components.WaterMaterials
import com.pafoid.skate.engine.ecs.prefab.PrefabService
import com.pafoid.skate.engine.assets.PrefabStore
import com.pafoid.skate.engine.render.water.WaterMaterial
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

/** AST-37: built-in water materials are never packed (nor missing), and water scenes ship the water sounds. */
class WaterPackingTest {
    @TempDir
    lateinit var dir: File

    private val engineRoot = File(System.getProperty("user.dir")) // the repo's assets/Sounds/Water clips
    private val projectDir get() = File(dir, "Game").also { if (!it.isDirectory) ProjectLayout.create(it) }

    private fun serializer() = Serializer(AssetPaths().apply {
        projectRoot = projectDir.absoluteFile
        engineDefaults = EngineDefaultResolver(engineRoot) { projectRoot }
    })

    private fun project(water: Boolean, inPrefab: Boolean = false): Project {
        val ser = serializer()
        val pool = GameObject("Pool").addComponent(Transform()).addComponent(WaterComponent(material = WaterMaterials.POOL))
        val scene = Scene("Main")
        if (water && !inPrefab) scene.addChild(pool)
        if (water && inPrefab) {
            // A prefab the scene names: the water lives only in the prefab file.
            val prefab = File(projectDir, "Assets/Prefabs/pool.prefab")
            PrefabStore(ser).save(PrefabService(ser).createAsset("pool", pool), prefab)
            scene.addChild(GameObject("Ref").addComponent(Transform()).also { it.tag = prefab.absolutePath })
        }
        val sceneFile = File(ProjectLayout.scenesDir(projectDir), "Main.scene").apply { writeText(ser.encodeFile(FileKind.SCENE, scene)) }
        val file = File(projectDir, "Game.skateproject")
        val p = Project(name = "Game", projectPath = file.absolutePath, defaultScene = sceneFile.absolutePath, scenesPath = listOf(sceneFile.absolutePath))
        file.writeText(Serializer().encodeFile(FileKind.PROJECT, p))
        return p
    }

    private fun packer() = AssetsPacker(LoggerService(), gameStringsDir = File(dir, "no-strings"), engineRoot = engineRoot)

    private fun packedPaths(out: File): Set<String> {
        val bin = File(out, AssetsPacker.BIN_FILE).readBytes()
        val header = ByteBuffer.wrap(bin, 0, 4).int + 4
        return Serializer().decode<Atlas>(bin.copyOfRange(4, header).toString(Charsets.UTF_8)).values.flatten().map { it.path }.toSet()
    }

    @Test
    fun WaterScene_PacksTheWaterSounds_AndNoFileForTheBuiltInMaterial() {
        val p = project(water = true)
        val sceneText = File(p.defaultScene).readText()
        assertTrue(WaterMaterials.POOL in sceneText, "the scene names the built-in pool material")
        val out = File(dir, "out")
        assertTrue(packer().pack(p, outputDir = out))
        val paths = packedPaths(out)
        assertTrue(paths.containsAll(EngineDefaults.Sounds.WATER), "water sounds: $paths")
        assertFalse(paths.any { it in EngineDefaults.BUILT_IN }, "built-in materials have no file to pack")
        // What the lifecycle check asserts: every engine: reference the scene names is packed or built in.
        val refs = Regex("\"(engine:[^\"]+)\"").findAll(sceneText).map { it.groupValues[1] }.toSet()
        assertEquals(emptyList<String>(), (refs - paths).filterNot(EngineDefaults::isBuiltIn))
    }

    @Test
    fun WaterInAPrefab_AlsoBringsTheSounds() {
        val out = File(dir, "out")
        assertTrue(packer().pack(project(water = true, inPrefab = true), onlyReferenced = true, outputDir = out))
        assertTrue(packedPaths(out).containsAll(EngineDefaults.Sounds.WATER))
    }

    @Test
    fun SceneWithoutWater_PacksNoWaterSounds() {
        val out = File(dir, "out")
        assertTrue(packer().pack(project(water = false), outputDir = out))
        assertTrue(packedPaths(out).none { it in EngineDefaults.Sounds.WATER })
    }

    @Test
    fun BuiltInMaterials_ResolveInTheGameWithoutAFile() {
        assertEquals(WaterMaterials.ALL.toSet(), EngineDefaults.BUILT_IN)
        WaterMaterials.ALL.forEach { ref ->
            val resolved = WaterMaterial.resolve(ref, readText = { null }, resolveTexture = { null })
            val expected = WaterMaterial.resolve(WaterMaterials.DEFAULT, readText = { null }, resolveTexture = { null })
            if (ref != WaterMaterials.DEFAULT) assertFalse(resolved.name == expected.name, "$ref falls back to its own preset, not the default")
        }
        assertTrue(EngineDefaults.Sounds.WATER.all { it in EngineDefaults.CATALOG }, "water clips are catalog defaults")
        assertEquals(listOf(WaterAudio.DEFAULT_AMBIENCE) + WaterAudio.DEFAULT_SPLASHES, EngineDefaults.Sounds.WATER)
        assertTrue(EngineDefaults.Sounds.WATER.all { File(engineRoot, "assets/" + EngineDefaults.logicalPath(it)).isFile })
    }
}
