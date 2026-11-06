package com.pafoid.skate.editor.assetimport.blender

import com.pafoid.skate.editor.tools.external.BlenderInstall
import com.pafoid.skate.editor.tools.external.BlenderSource
import com.pafoid.skate.editor.tools.external.BlenderVersion
import com.pafoid.skate.editor.tools.external.FakeRunner
import com.pafoid.skate.editor.tools.external.ProcessResult
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.AssetRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** AST-19 import with a fake Blender: the "process" writes what skate_export.py would. */
class BlenderImporterTest {
    @TempDir
    lateinit var project: File

    private lateinit var registry: AssetRegistry
    private lateinit var blend: File
    private val install = BlenderInstall(File("C:/B/blender.exe"), BlenderVersion(5, 2, 3), BlenderSource.DEFAULT_DIR)

    /** Actions the fake Blender exports; null makes it fail. */
    private var actions: List<String>? = emptyList()
    private var glbContent = "model-v1"

    private val runner = FakeRunner { cmd ->
        val exported = actions ?: return@FakeRunner ProcessResult(1, listOf("Traceback ...", "RuntimeError: boom"))
        val args = Json.parseToJsonElement(File(cmd.last()).readText()).jsonObject
        val out = File(args["out_dir"]!!.jsonPrimitive.content).apply { mkdirs() }
        val name = args["name"]!!.jsonPrimitive.content
        val outputs = listOf("$name.glb") + exported.map { "$name@$it.glb" }
        outputs.forEach { File(out, it).writeText(glbContent + it) }
        val actionsJson = exported.joinToString(",") { """{"action":"$it","file":"$name@$it.glb"}""" }
        File(out, "report.json").writeText(
            """{"blender":"5.2.3 LTS","unitScale":1.0,"simplifiedMaterials":[{"material":"Toon","nodes":["ShaderNodeBsdfToon"]}],
               "actions":[$actionsJson],"outputs":[${outputs.joinToString(",") { "\"$it\"" }}]}"""
        )
        ProcessResult(0, listOf("Blender quit"))
    }

    private fun importer(blender: BlenderInstall? = install) =
        BlenderImporter(project, registry, runner, blender = { blender }, script = { File(project, "skate_export.py") })

    private fun importNow(force: Boolean = false): BlenderConversion {
        val c = importer().convert(blend, force)
        if (c is BlenderConversion.Converted) importer().apply(c)
        return c
    }

    private fun guid(file: File) = AssetMeta.read(file)!!.guid

    @BeforeEach
    fun setUp() {
        registry = AssetRegistry(project)
        blend = File(project, "Assets/Props/baseball-cap.blend").apply { parentFile.mkdirs(); writeText("blend v1") }
    }

    @Test
    fun FirstImport_WritesTheGlbNextToTheBlend_WithSidecarsAndImportRecord() {
        assertTrue(importNow() is BlenderConversion.Converted)

        val glb = File(project, "Assets/Props/baseball-cap.blend.import/baseball-cap.glb")
        assertTrue(glb.isFile)
        assertTrue(AssetMeta.sidecarOf(glb).isFile, "the GLB gets a GUID")
        assertEquals(guid(glb), registry.idOf("Assets/Props/baseball-cap.blend.import/baseball-cap.glb"))
        val meta = AssetMeta.read(blend)!!
        assertEquals("blender", meta.importer)
        assertEquals("baseball-cap.glb", meta.settings[BlenderImporter.OUTPUTS])
        assertEquals("5.2.3", meta.settings[BlenderImporter.BLENDER_VERSION])
        assertFalse(File(project, ".skate/cache/blender").listFiles().orEmpty().any { it.listFiles()?.isNotEmpty() == true }, "staging cleaned")
    }

    @Test
    fun Reimport_AfterSave_KeepsGuidsAndUpdatesContent() {
        actions = listOf("Ollie")
        importNow()
        val glb = File(BlenderOutputs.dir(blend), "baseball-cap.glb")
        val clip = File(BlenderOutputs.dir(blend), "baseball-cap@Ollie.glb")
        val before = guid(glb) to guid(clip)

        blend.writeText("blend v2")
        glbContent = "model-v2"
        assertTrue(importNow() is BlenderConversion.Converted)

        assertEquals(before, guid(glb) to guid(clip))
        assertTrue(glb.readText().startsWith("model-v2"))
    }

    @Test
    fun UnchangedBlend_IsUpToDate_WithoutRunningBlender() {
        importNow()
        val runs = runner.commands.size

        assertTrue(importer().convert(blend) is BlenderConversion.UpToDate)
        assertEquals(runs, runner.commands.size)
        assertTrue(importer().convert(blend, force = true) is BlenderConversion.Converted, "force re-exports")
    }

    @Test
    fun FailedExport_KeepsThePreviousFiles() {
        importNow()
        val glb = File(BlenderOutputs.dir(blend), "baseball-cap.glb")
        val before = glb.readText()

        blend.writeText("blend v2 (broken)")
        actions = null
        val result = importer().convert(blend)

        assertTrue(result is BlenderConversion.Failed)
        assertTrue((result as BlenderConversion.Failed).log.contains("RuntimeError"))
        assertEquals(before, glb.readText())
    }

    @Test
    fun DeletedAction_KeepsItsClipAndWarns() {
        actions = listOf("Ollie", "Kickflip")
        importNow()
        val kickflip = File(BlenderOutputs.dir(blend), "baseball-cap@Kickflip.glb")
        val id = guid(kickflip)

        blend.writeText("blend v2")
        actions = listOf("Ollie")
        val c = importer().convert(blend) as BlenderConversion.Converted
        val result = importer().apply(c)

        assertTrue(kickflip.isFile, "kept so scenes using it still load")
        assertEquals(id, guid(kickflip))
        assertEquals(listOf(kickflip), result.keptStale)
        assertTrue(result.warnings.any { "baseball-cap@Kickflip.glb" in it })
        assertTrue(result.warnings.any { "Toon" in it }, "simplified materials are reported")
    }

    @Test
    fun NoBlender_FailsWithAHint() {
        val result = importer(blender = null).convert(blend)

        assertTrue(result is BlenderConversion.Failed)
        assertTrue("4.2" in (result as BlenderConversion.Failed).reason)
        assertTrue(runner.commands.isEmpty())
    }

    @Test
    fun ImportSettings_ComeFromTheSidecar() {
        registry.register(blend)
        AssetMeta.write(blend, AssetMeta.read(blend)!!.copy(settings = mapOf(BlenderImportSettings.ACTION_MODE to "embedded", BlenderImportSettings.APPLY_MODIFIERS to "false")))

        importer().convert(blend)

        val args = Json.parseToJsonElement(File(runner.commands.single().last()).readText()).jsonObject
        assertEquals("EMBEDDED", args["action_mode"]!!.jsonPrimitive.content)
        assertEquals("false", args["apply_modifiers"]!!.jsonPrimitive.content)
    }
}
