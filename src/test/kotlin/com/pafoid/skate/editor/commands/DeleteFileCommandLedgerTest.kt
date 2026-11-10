package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.assetstore.AttributionLedger
import com.pafoid.skate.editor.assetstore.ImportEntry
import com.pafoid.skate.editor.assetstore.ImportPlan
import com.pafoid.skate.editor.assetstore.SourceFormat
import com.pafoid.skate.editor.assetstore.StoreImportRequest
import com.pafoid.skate.editor.assetstore.StoreImporter
import com.pafoid.skate.editor.assetstore.StorePaths
import com.pafoid.skate.editor.assetstore.StoreVariant
import com.pafoid.skate.editor.assetstore.cc0Details
import com.pafoid.skate.editor.commands.project.DeleteFileCommand
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.core.LoggerService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** AST-25: deleting an imported store asset keeps the attribution ledger (and so the export credits) correct. */
class DeleteFileCommandLedgerTest {
    @TempDir
    lateinit var project: File

    private val logger = mockk<LoggerService>(relaxed = true)
    private lateinit var registry: AssetRegistry
    private val folder get() = File(project, "Assets/Store/polyhaven/woodentable_01")

    @BeforeEach
    fun importStoreAsset() {
        registry = AssetRegistry(project)
        val staging = StorePaths.stagingDir(project, "polyhaven", "WoodenTable_01", "gltf-1k")
        listOf("a.gltf", "a.bin").forEach { File(staging, it).apply { parentFile.mkdirs(); writeText(it) } }
        StoreImporter(project, registry).import(StoreImportRequest(cc0Details(), StoreVariant("gltf-1k", SourceFormat.GLTF, "1k", null), staging,
            ImportPlan(listOf(ImportEntry("a.gltf", "a.gltf"), ImportEntry("a.bin", "a.bin")))))
    }

    private fun entry() = AttributionLedger.load(project).find("polyhaven", "WoodenTable_01")
    private fun id(name: String) = AssetMeta.read(File(folder, name))!!.guid.value

    @Test
    fun DeleteStoreFile_RemovesItsIdFromTheLedgerAndUndoRestores() {
        val before = entry()!!
        val command = DeleteFileCommand(File(folder, "a.gltf").absolutePath, logger, registry)
        command.execute()
        assertTrue(command.wasSuccessful())
        assertEquals(listOf(id("a.bin")), entry()!!.assetIds)
        command.undo()
        assertTrue(File(folder, "a.gltf").isFile)
        assertEquals(before, entry())
    }

    @Test
    fun DeleteStoreFolder_DropsTheEntryAndUndoRestoresIt() {
        val before = entry()!!
        val command = DeleteFileCommand(folder.absolutePath, logger, registry)
        command.execute()
        assertFalse(folder.exists())
        assertNull(entry())
        command.undo()
        assertEquals(before, entry())
        command.execute()
        assertNull(entry(), "redo forgets again")
    }

    @Test
    fun DeleteOtherFile_LeavesTheLedgerAlone() {
        val ledgerText = AttributionLedger.file(project).readText()
        val other = File(project, "Assets/notes.png").apply { writeText("x") }
        registry.register(other)
        DeleteFileCommand(other.absolutePath, logger, registry).execute()
        assertEquals(ledgerText, AttributionLedger.file(project).readText())
    }

    @Test
    fun DeleteWithoutLedgerOrRegistry_DoesNotCreateALedger() {
        AttributionLedger.file(project).delete()
        DeleteFileCommand(File(folder, "a.bin").absolutePath, logger, registry).execute()
        assertFalse(AttributionLedger.file(project).exists())
        val again = File(project, "x.png").apply { writeText("x") }
        DeleteFileCommand(again.absolutePath, logger, null).execute()
        assertFalse(AttributionLedger.file(project).exists())
    }

    @Test
    fun Ledger_ForgetAndRestoreHelpers() {
        val id = id("a.gltf")
        assertEquals(emptyList<Any>(), AttributionLedger.forgetAssets(project, emptySet()))
        val touched = AttributionLedger.forgetAssets(project, setOf(id))
        assertEquals(1, touched.size)
        assertTrue(id in touched.single().assetIds)
        AttributionLedger.restoreEntries(project, touched)
        assertTrue(id in entry()!!.assetIds)
    }
}
