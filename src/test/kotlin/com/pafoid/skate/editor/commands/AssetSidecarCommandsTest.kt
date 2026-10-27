package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.project.DeleteFileCommand
import com.pafoid.skate.editor.commands.project.MoveFileCommand
import com.pafoid.skate.editor.commands.project.RenameFileCommand
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.core.LoggerService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AssetSidecarCommandsTest {
    @TempDir
    lateinit var dir: File

    private val logger = mockk<LoggerService>(relaxed = true)

    private fun asset(name: String): Pair<File, String> {
        val f = File(dir, name).apply { parentFile.mkdirs(); writeText("data") }
        val registry = AssetRegistry(dir)
        val id = registry.register(f)
        return f to id.value
    }

    @Test
    fun AssetCommands_Rename_KeepsMetaAndGuid_AndUndoRestores() {
        val (file, guid) = asset("a.png")
        val cmd = RenameFileCommand(file.absolutePath, "b.png", logger, AssetRegistry(dir))
        cmd.execute()
        val renamed = File(dir, "b.png")
        assertTrue(cmd.wasSuccessful())
        assertFalse(AssetMeta.sidecarOf(file).exists())
        assertEquals(guid, AssetMeta.read(renamed)!!.guid.value)
        cmd.undo()
        assertEquals(guid, AssetMeta.read(file)!!.guid.value)
        assertFalse(AssetMeta.sidecarOf(renamed).exists())
    }

    @Test
    fun AssetCommands_RenameWithoutRegistry_StillMovesSidecar() {
        val (file, guid) = asset("a.png")
        RenameFileCommand(file.absolutePath, "c.png", logger).execute()
        assertEquals(guid, AssetMeta.read(File(dir, "c.png"))!!.guid.value)
    }

    @Test
    fun AssetCommands_Move_KeepsGuid_AndUndoRestores() {
        val (file, guid) = asset("a.png")
        val target = File(dir, "sub").apply { mkdirs() }
        val cmd = MoveFileCommand(file.absolutePath, target.absolutePath, logger, AssetRegistry(dir))
        cmd.execute()
        assertTrue(cmd.wasSuccessful())
        assertEquals(guid, AssetMeta.read(File(target, "a.png"))!!.guid.value)
        assertFalse(AssetMeta.sidecarOf(file).exists())
        cmd.undo()
        assertEquals(guid, AssetMeta.read(file)!!.guid.value)
    }

    @Test
    fun AssetCommands_Move_RejectsInvalidTargets() {
        val (file, _) = asset("a.png")
        assertFalse(MoveFileCommand(file.absolutePath, dir.absolutePath, logger).apply { execute() }.wasSuccessful())
        val folder = File(dir, "f").apply { mkdirs() }
        val inner = File(folder, "g").apply { mkdirs() }
        assertFalse(MoveFileCommand(folder.absolutePath, inner.absolutePath, logger).apply { execute() }.wasSuccessful())
        File(dir, "t").mkdirs()
        File(dir, "t/a.png").writeText("x")
        assertFalse(MoveFileCommand(file.absolutePath, File(dir, "t").absolutePath, logger).apply { execute() }.wasSuccessful())
        assertTrue(file.exists())
    }

    @Test
    fun AssetCommands_Delete_TrashesMetaAndUndoRestoresGuid() {
        val (file, guid) = asset("a.png")
        val cmd = DeleteFileCommand(file.absolutePath, logger, AssetRegistry(dir))
        cmd.execute()
        assertTrue(cmd.wasSuccessful())
        assertFalse(file.exists())
        assertFalse(AssetMeta.sidecarOf(file).exists())
        cmd.undo()
        assertTrue(file.exists())
        assertEquals(guid, AssetMeta.read(file)!!.guid.value)
    }
}
