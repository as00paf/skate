package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ReportsFailure
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.Scene
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.io.File

class SaveSceneAsCommand(
    private val scene: Scene,
    private val serializer: Serializer,
    private val logger: LoggerService,
    /** Translated title and file-type description of the native save dialog. */
    private val dialogTitle: String,
    private val filterDescription: String,
) : ExecuteOnlyCommand, ReportsFailure {
    override val errorTitleKey = "error.save_scene.title"
    private var failure: String? = null

    override fun wasSuccessful(): Boolean = failure == null
    override fun getFailureReason(): String? = failure

    override fun execute() {
        failure = null
        val filter = MemoryUtil.memUTF8("*.scene")
        val filters = MemoryUtil.memAllocPointer(1)
        filters.put(0, filter)

        val path = try {
            TinyFileDialogs.tinyfd_saveFileDialog(dialogTitle, "${scene.name}.scene", filters, filterDescription)
        } finally {
            MemoryUtil.memFree(filter)
            MemoryUtil.memFree(filters)
        }

        if (path != null) {
            val oldName = scene.name
            scene.name = File(path).nameWithoutExtension
            try {
                File(path).writeText(serializer.encodeFile(FileKind.SCENE, scene))
                logger.logEditor("Scene saved to $path")
            } catch (e: Exception) {
                // The scene keeps its old name when the new file could not be written.
                scene.name = oldName
                failure = "$path: ${e.message ?: e.javaClass.simpleName}"
                logger.logEditor("Failed to save scene to $failure", LogLevel.ERROR)
            }
        }
    }

    override fun undo() {
        // Save operations are not reversible
    }

    override fun getDisplayName(): String = "Save Scene As"
    override fun getTargetName(): String? = scene.name
}
