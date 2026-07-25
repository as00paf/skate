package com.pafoid.skate.editor.commands.project

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
    private val logger: LoggerService
) : ExecuteOnlyCommand {
    override fun execute() {
        val filter = MemoryUtil.memUTF8("*.scene")
        val filters = MemoryUtil.memAllocPointer(1)
        filters.put(0, filter)

        val path = try {
            TinyFileDialogs.tinyfd_saveFileDialog(scene.name, "Save Scene", filters, "Scene Files")
        } finally {
            MemoryUtil.memFree(filter)
            MemoryUtil.memFree(filters)
        }

        if (path != null) {
            scene.name = File(path).nameWithoutExtension
            try {
                File(path).writeText(serializer.encode(scene))
                logger.logEditor("Scene saved to $path")
            } catch (e: Exception) {
                logger.logEditor("Failed to save scene to $path: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    override fun undo() {
        // Save operations are not reversible
    }

    override fun getDisplayName(): String = "Save Scene As"
    override fun getTargetName(): String? = scene.name
}
