package com.pafoid.skate.editor.commands.animation

import com.pafoid.skate.editor.animation.clipeditor.ClipDocument
import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.commands.ReportsFailure
import com.pafoid.skate.engine.animation.edit.SkAnimFormat

/** Writes the edited clip back to its `.skanim` file (ANI-14). Execute-only: undo never rewrites files. */
class SaveAnimationClipCommand(private val document: ClipDocument) : ExecuteOnlyCommand, ReportsFailure {
    private var failure: String? = null

    override fun execute() {
        failure = null
        val clip = document.clip
        try {
            val text = SkAnimFormat.encode(clip)
            document.file.parentFile?.mkdirs()
            // Write next to the file, then replace it, so a failed write never leaves half a clip.
            val tmp = java.io.File(document.file.parentFile, document.file.name + ".tmp")
            tmp.writeText(text)
            java.nio.file.Files.move(tmp.toPath(), document.file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            document.markSaved(clip)
        } catch (e: Exception) {
            failure = e.message ?: e.javaClass.simpleName
        }
    }

    override fun undo() {}
    override fun wasSuccessful(): Boolean = failure == null
    override fun getFailureReason(): String? = failure
    override val errorTitleKey: String get() = "error.animation_editor.save_failed"
    override fun getDisplayName(): String = "Save Animation Clip"
    override fun getTargetName(): String = document.file.name
}
