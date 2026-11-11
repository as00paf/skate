package com.pafoid.skate.editor.commands.project

import com.pafoid.skate.editor.commands.ReportsFailure
import java.io.File

/**
 * Applies the Properties window's edits to a config file (`*.character.json`, `*.controller.json`, GPL-26). Undo
 * restores the previous text; a failed write opens the error dialog ([ReportsFailure]).
 */
class SaveConfigFileCommand(private val file: File, private val text: String) : ReportsFailure {
    override val errorTitleKey = "error.save_config.title"

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
            failure = "${file.absolutePath}: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    override fun undo() {
        if (!succeeded) return
        if (existed) file.writeText(previous.orEmpty()) else file.delete()
    }

    override fun wasSuccessful(): Boolean = succeeded
    override fun getFailureReason(): String? = failure
    override fun getDisplayName(): String = "Edit Config ${file.name}"
    override fun getTargetName(): String? = file.name
}
