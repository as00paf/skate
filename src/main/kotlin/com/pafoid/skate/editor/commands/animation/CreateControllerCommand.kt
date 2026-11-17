package com.pafoid.skate.editor.commands.animation

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.commands.ReportsFailure
import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import java.io.File

/**
 * Writes a new `.controller.json` (ANI-21 §5.2): New in the Animator window, or the project copy of an engine
 * default. Execute-only: undo never deletes files. The file is always new: callers pick a free name with [freeFile].
 */
class CreateControllerCommand(private val asset: AnimationControllerAsset, val file: File) : ExecuteOnlyCommand, ReportsFailure {
    private var failure: String? = null

    override fun execute() {
        failure = null
        try {
            if (file.exists()) error("${file.name} already exists")
            SaveControllerCommand.write(file, asset.toJson())
        } catch (e: Exception) {
            failure = e.message ?: e.javaClass.simpleName
        }
    }

    override fun undo() {}
    override fun wasSuccessful(): Boolean = failure == null
    override fun getFailureReason(): String? = failure
    override val errorTitleKey: String get() = "error.animator_window.save_failed"
    override fun getDisplayName(): String = "Create Animator Controller"
    override fun getTargetName(): String = file.name

    companion object {
        const val EXTENSION = ".controller.json"

        /** `<dir>/<base>.controller.json`, or `<base> 2.controller.json`, ... when taken. */
        fun freeFile(dir: File, base: String): File {
            val clean = base.replace(Regex("""[\\/:*?"<>|]+"""), "_").trim().ifEmpty { "Controller" }
            var file = File(dir, "$clean$EXTENSION")
            var n = 2
            while (file.exists()) file = File(dir, "$clean ${n++}$EXTENSION")
            return file
        }
    }
}
