package com.pafoid.skate.editor.assetimport.blender

import com.pafoid.skate.editor.assetimport.blender.BlenderImportSettings.ActionMode
import com.pafoid.skate.editor.tools.external.BlenderInstall
import com.pafoid.skate.editor.tools.external.BlenderSource
import com.pafoid.skate.editor.tools.external.BlenderTestResult
import com.pafoid.skate.editor.tools.external.BlenderVersion
import com.pafoid.skate.editor.tools.external.FakeRunner
import com.pafoid.skate.editor.tools.external.ProcessResult
import com.pafoid.skate.engine.assets.AssetId
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.AssetRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-66: inspector edits are stored in the `.blend` sidecar and used by the next (forced) import. */
class BlenderImportSettingsEdt66Test {
    @TempDir
    lateinit var project: File

    private val edited = BlenderImportSettings(
        applyModifiers = false, onlyVisible = true, exportActions = false, actionMode = ActionMode.EMBEDDED, autoReimport = false,
    )

    @Test
    fun Settings_RoundTripThroughTheSidecarKeys() {
        assertEquals(edited, BlenderImportSettings.from(edited.toSettings()))
        assertEquals(BlenderImportSettings(), BlenderImportSettings.from(BlenderImportSettings().toSettings()))
    }

    @Test
    fun ApplyTo_KeepsTheImportRecordAndOtherKeys() {
        val meta = AssetMeta(guid = AssetId.generate(), importer = "blender", settings = mapOf(
            BlenderImporter.OUTPUTS to "cap.glb", BlenderImporter.SOURCE_HASH to "abc", "other" to "x",
            BlenderImportSettings.ONLY_VISIBLE to "false",
        ))
        val out = edited.applyTo(meta)
        assertEquals(meta.guid, out.guid)
        assertEquals("cap.glb", out.settings[BlenderImporter.OUTPUTS])
        assertEquals("abc", out.settings[BlenderImporter.SOURCE_HASH])
        assertEquals("x", out.settings["other"])
        assertEquals(edited, BlenderImportSettings.from(out.settings))
    }

    @Test
    fun ImportRecord_ReadsTheLastImport_OrNullBeforeTheFirst() {
        assertNull(BlenderImportRecord.from(null))
        assertNull(BlenderImportRecord.from(AssetMeta(guid = AssetId.generate())))
        val meta = AssetMeta(guid = AssetId.generate(), settings = mapOf(
            BlenderImporter.IMPORTED_AT to "2026-11-10T10:00:00Z", BlenderImporter.BLENDER_VERSION to "5.2.3",
            BlenderImporter.OUTPUTS to "cap.glb, cap@Ollie.glb",
        ))
        assertEquals(BlenderImportRecord("5.2.3", "2026-11-10T10:00:00Z", listOf("cap.glb", "cap@Ollie.glb")), BlenderImportRecord.from(meta))
    }

    @Test
    fun SavedSettings_ReachBlender_OnTheForcedReimport() {
        val registry = AssetRegistry(project)
        val blend = File(project, "Assets/Models/cap.blend").apply { parentFile.mkdirs(); writeText("blend") }
        var lastArgs: Map<String, String> = emptyMap()
        val runner = FakeRunner { cmd ->
            val args = Json.parseToJsonElement(File(cmd.last()).readText()).jsonObject
            lastArgs = args.mapValues { it.value.jsonPrimitive.content }
            val out = File(lastArgs.getValue("out_dir")).apply { mkdirs() }
            File(out, "cap.glb").writeText("glb")
            File(out, "report.json").writeText("""{"outputs":["cap.glb"]}""")
            ProcessResult(0, emptyList())
        }
        val install = BlenderInstall(File("C:/B/blender.exe"), BlenderVersion(5, 2, 3), BlenderSource.DEFAULT_DIR)
        val importer = BlenderImporter(project, registry, runner, blender = { install }, script = { File(project, "s.py") })
        importer.apply(importer.convert(blend) as BlenderConversion.Converted)
        assertEquals("true", lastArgs["apply_modifiers"])

        // What the inspector's Apply does: write the settings, then re-import with force.
        AssetMeta.write(blend, edited.applyTo(AssetMeta.read(blend)!!))
        val again = importer.convert(blend, force = true)

        assertTrue(again is BlenderConversion.Converted, "unchanged file, still re-exported")
        assertEquals("false", lastArgs["apply_modifiers"])
        assertEquals("true", lastArgs["only_visible"])
        assertEquals("false", lastArgs["export_actions"])
        assertEquals("EMBEDDED", lastArgs["action_mode"])
    }

    @Test
    fun TestResult_TellsWhetherTheTypedPathIsTheBlenderFound() {
        val fromSetting = BlenderInstall(File("D:/Blender/blender.exe"), BlenderVersion(4, 2), BlenderSource.SETTING)
        val fromPath = BlenderInstall(File("C:/B/blender.exe"), BlenderVersion(5, 2), BlenderSource.PATH)

        assertEquals(BlenderTestResult.Found(fromSetting), BlenderTestResult.of("D:/Blender/blender.exe", fromSetting))
        assertEquals(BlenderTestResult.PathUnusable("D:/old/blender.exe", fromPath), BlenderTestResult.of("D:/old/blender.exe", fromPath))
        assertEquals(BlenderTestResult.Found(fromPath), BlenderTestResult.of("  ", fromPath), "blank = automatic")
        assertEquals(BlenderTestResult.NotFound("D:/x.exe"), BlenderTestResult.of("D:/x.exe", null))
        assertEquals(BlenderTestResult.NotFound(null), BlenderTestResult.of(null, null))
    }
}
