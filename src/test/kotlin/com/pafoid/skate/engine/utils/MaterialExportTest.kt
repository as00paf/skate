package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.pack.PackManifest
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.render.material.MaterialAsset
import com.pafoid.skate.engine.render.material.MaterialSchema
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

/** AST-08: a scene linking a `.skmat` exports the material and its textures, and the game reads them from the pack. */
class MaterialExportTest {
    @TempDir
    lateinit var dir: File

    private lateinit var skmat: File
    private lateinit var albedo: File

    private fun project(): Project {
        listOf("Textures", "Scenes", "Assets/Materials").forEach { File(dir, it).mkdirs() }
        albedo = File(dir, "Textures/rock.png").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        File(dir, "Textures/rock_n.png").writeBytes(byteArrayOf(4, 5, 6))
        File(dir, "Textures/unused.png").writeBytes(byteArrayOf(7))
        skmat = File(dir, "Assets/Materials/rock.${MaterialAsset.EXTENSION}")
        MaterialAsset(
            name = "Rock",
            params = mapOf(MaterialSchema.Companion.Param.BASE_COLOR to listOf(0.2f, 0.4f, 0.6f, 1f)),
            textures = mapOf(
                MaterialSchema.Companion.Tex.BASE_COLOR to albedo.absolutePath,
                MaterialSchema.Companion.Tex.NORMAL to "Textures/rock_n.png", // project-relative
            ),
        ).save(skmat)
        val scene = File(dir, "Scenes/Main.scene")
        scene.writeText(buildJsonObject { put("materialPath", JsonPrimitive(skmat.absolutePath)) }.toString())
        val file = File(dir, "Game.skateproject").also { it.writeText("{}") }
        return Project(name = "Game", projectPath = file.absolutePath, defaultScene = scene.absolutePath, scenesPath = listOf(scene.absolutePath))
    }

    private fun export(): AssetsResolver {
        val options = ExportOptions(target = ExportTarget.PACK_ONLY)
        assertTrue(ProjectExporter(mockk<LoggerService>(relaxed = true), File(dir, "no-strings")).export(project(), options).success)
        val bin = File(dir, "builds/game.bin").readBytes()
        val header = ByteBuffer.wrap(bin, 0, 4).int + 4
        val atlas = Serializer().decode<Atlas>(bin.copyOfRange(4, header).toString(Charsets.UTF_8))
        return AssetsResolver(Serializer(), mockk(relaxed = true)).also { it.initialize(atlas, bin, header) }
    }

    @Test
    fun Export_IncludesTheSkmatAndTheTexturesItUses() {
        export()
        val paths = Serializer().json.decodeFromString(PackManifest.serializer(), File(dir, "builds/manifest.json").readText())
            .entries.map { it.path }
        assertTrue("Assets/Materials/rock.skmat" in paths, paths.toString())
        assertTrue("Textures/rock.png" in paths)
        assertTrue("Textures/rock_n.png" in paths, "relative texture paths inside a .skmat are followed")
        assertFalse("Textures/unused.png" in paths)
    }

    @Test
    fun PackedSkmat_LoadsWithItsTexturesFromThePack() {
        val resolver = export()
        val component = RenderComponent(materialPath = skmat.absolutePath)
        val resolved = mutableListOf<String>()
        val error = component.resolveMaterialAsset(
            { resolver.resolveBytes(it)?.toString(Charsets.UTF_8) },
            { path -> resolver.resolveBytes(path)?.let { resolved += path; Texture(filePath = path) } },
        )
        assertNull(error)
        val material = component.material!!
        assertEquals(0.4f, material.baseColor.y, 1e-6f)
        assertEquals(albedo.absolutePath, material.baseColorTexture?.filePath)
        assertEquals("Textures/rock_n.png", material.normalMap?.filePath)
        assertEquals(listOf(albedo.absolutePath, "Textures/rock_n.png").sorted(), resolved.sorted())
        assertArrayEquals(byteArrayOf(1, 2, 3), resolver.resolveBytes(albedo.absolutePath))
    }

    @Test
    fun Resolver_FindsPackedFilesBySeparatorVariantsAndProjectRelativePaths() {
        val resolver = export()
        val abs = skmat.absolutePath
        assertNotNull(resolver.find(abs))
        assertNotNull(resolver.find(abs.replace('\\', '/')))
        assertNotNull(resolver.find(File(skmat.parentFile, "../Materials/rock.skmat").path))
        assertNotNull(resolver.find("Assets/Materials/rock.skmat"))
        assertNotNull(resolver.find("Assets\\Materials\\rock.skmat"))
        assertNull(resolver.find("Assets/Materials/missing.skmat"))
        assertNull(resolver.find(File(dir.parentFile, "elsewhere/rock.skmat").absolutePath), "other absolute paths do not match by name")
        assertNull(resolver.resolveBytes("Textures/unused.png"))
    }
}
