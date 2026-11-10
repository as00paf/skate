package com.pafoid.skate.editor.assetstore

import com.pafoid.skate.editor.commands.CommandCategory
import com.pafoid.skate.editor.commands.assetstore.ImportRunner
import com.pafoid.skate.editor.commands.assetstore.ImportStoreAssetCommand
import com.pafoid.skate.editor.commands.assetstore.ImportStoreAssetCommand.Change
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.core.EventSystem
import io.mockk.mockk
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ImportStoreAssetCommandTest {
    @TempDir
    lateinit var project: File

    private lateinit var registry: AssetRegistry
    private lateinit var importer: StoreImporter
    private val changes = mutableListOf<Change>()
    private var projectOpen = true
    private val folder = "Assets/Store/polyhaven/woodentable_01"

    /** IO and main phases inline; the job is already complete, as after `runOnMain(...).join()`. */
    private val inline = ImportRunner { io, main -> io(); main(); Job().apply { complete() } }

    @BeforeEach
    fun setUp() {
        registry = AssetRegistry(project)
        importer = StoreImporter(project, registry)
    }

    private fun command(license: LicenseInfo = LicenseCatalog.CC0, runner: ImportRunner = inline): ImportStoreAssetCommand {
        val staging = StorePaths.stagingDir(project, "polyhaven", "WoodenTable_01", "gltf-1k")
        File(staging, "a.gltf").apply { parentFile.mkdirs(); writeText("gltf") }
        val request = StoreImportRequest(cc0Details(license = license), StoreVariant("gltf-1k", SourceFormat.GLTF, "1k", null), staging,
            ImportPlan(listOf(ImportEntry("a.gltf", "a.gltf"))))
        return ImportStoreAssetCommand(importer, request, runner, { projectOpen }) { _, change -> changes += change }
    }

    @Test
    fun Command_IsAsyncWithTranslatedName() {
        val c = command()
        assertEquals(CommandCategory.ASYNC, c.getCategory())
        assertEquals("assetstore.command.import", c.displayNameKey)
        assertEquals("assetstore.error.import.title", c.errorTitleKey)
        assertEquals("Wooden Table 01", c.getTargetName())
    }

    @Test
    fun Command_ExecuteUndoRedo_NotifiesEveryChange() {
        val c = command()
        c.execute()
        assertTrue(c.didCompleteSuccessfully())
        assertTrue(c.getCompletionJob()!!.isCompleted)
        assertEquals(File(project, folder), c.importedFolder)
        c.undo()
        assertFalse(File(project, folder).exists())
        assertTrue(AttributionLedger.load(project).entries.isEmpty())
        assertEquals(File(project, folder), c.importedFolder, "kept so views can refresh after undo")
        c.execute()
        assertNotNull(registry.idOf("$folder/a.gltf"))
        assertEquals(listOf(Change.IMPORTED, Change.UNDONE, Change.IMPORTED), changes)
    }

    @Test
    fun Command_CopyRunsInIoPhase_RegistryInMainPhase() {
        var copiedBeforeMain = false
        var registeredBeforeMain = true
        val split = ImportRunner { io, main ->
            io()
            copiedBeforeMain = File(project, "$folder/a.gltf").isFile
            registeredBeforeMain = registry.idOf("$folder/a.gltf") != null
            main()
            Job().apply { complete() }
        }
        command(runner = split).execute()
        assertTrue(copiedBeforeMain)
        assertFalse(registeredBeforeMain)
        assertNotNull(registry.idOf("$folder/a.gltf"))
    }

    @Test
    fun Command_Failure_IsReported() {
        val c = command(license = LicenseCatalog.UNKNOWN)
        c.execute()
        assertFalse(c.wasSuccessful())
        assertTrue(c.getFailureReason()!!.contains("license", ignoreCase = true))
        assertEquals(listOf(Change.FAILED), changes)
        c.undo()
        assertEquals(listOf(Change.FAILED), changes)
    }

    @Test
    fun Command_ProjectSwitchedDuringCopy_DiscardsImport() {
        val switching = ImportRunner { io, main -> io(); projectOpen = false; main(); Job().apply { complete() } }
        val c = command(runner = switching)
        c.execute()
        assertFalse(c.wasSuccessful())
        assertFalse(File(project, folder).exists())
        assertFalse(AttributionLedger.file(project).exists())
    }

    @Test
    fun Command_UndoAfterProjectSwitch_IsNoOp() {
        val c = command()
        c.execute()
        projectOpen = false
        c.undo()
        assertTrue(File(project, "$folder/a.gltf").isFile)
    }

    @Test
    fun Command_ThroughUndoRedoManager_GoesToHistoryAndUndoes() {
        val manager = UndoRedoManager(EventSystem(mockk(relaxed = true)), mockk(relaxed = true))
        manager.executeCommand(command())
        assertEquals(1, manager.getUndoCount())
        manager.undo()
        assertFalse(File(project, folder).exists())
        manager.redo()
        assertTrue(File(project, "$folder/a.gltf").isFile)
        assertEquals(1, manager.getUndoCount())
    }
}
