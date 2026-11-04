package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ExecutionTrackedCommand
import java.io.File

/** Writes script text to disk; undo restores the previous content (or removes a newly created file). */
class SaveScriptCommand(private val file: File, private val text: String) : ExecutionTrackedCommand {
    private var previous: String? = null
    private var existed = false
    private var succeeded = false
    private var failure: String? = null

    override fun execute() {
        succeeded = false
        failure = null
        try {
            existed = file.exists()
            previous = if (existed) file.readText() else null
            file.parentFile?.mkdirs()
            file.writeText(text)
            succeeded = true
        } catch (e: Exception) {
            failure = e.message ?: "write failed"
        }
    }

    override fun undo() {
        if (!succeeded) return
        if (existed) file.writeText(previous.orEmpty()) else file.delete()
    }

    override fun wasSuccessful(): Boolean = succeeded
    override fun getFailureReason(): String? = failure
    override fun getDisplayName(): String = "Save Script ${file.name}"
    override fun getTargetName(): String? = file.name
}
