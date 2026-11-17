package com.pafoid.skate.editor.commands.animation

import com.pafoid.skate.editor.animation.controllereditor.ControllerDocument
import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.commands.ReportsFailure
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes the edited controller back to its `.controller.json` (ANI-21 §5.1). Execute-only: undo never rewrites
 * files; the edits stay undoable. Writes next to the file and then replaces it, so a failed write never leaves half
 * a controller.
 */
class SaveControllerCommand(private val document: ControllerDocument) : ExecuteOnlyCommand, ReportsFailure {
    private var failure: String? = null

    override fun execute() {
        failure = null
        val file = document.file ?: run { failure = "read-only"; return }
        val asset = document.asset
        try {
            write(file, asset.toJson())
            document.markSaved(asset)
        } catch (e: Exception) {
            failure = e.message ?: e.javaClass.simpleName
        }
    }

    override fun undo() {}
    override fun wasSuccessful(): Boolean = failure == null
    override fun getFailureReason(): String? = failure
    override val errorTitleKey: String get() = "error.animator_window.save_failed"
    override fun getDisplayName(): String = "Save Animator Controller"
    override fun getTargetName(): String = document.displayName

    companion object {
        fun write(file: File, text: String) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
