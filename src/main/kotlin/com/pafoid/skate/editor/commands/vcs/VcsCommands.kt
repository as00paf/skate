package com.pafoid.skate.editor.commands.vcs

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.commands.KeyedCommand
import com.pafoid.skate.editor.vcs.Resolution
import com.pafoid.skate.editor.vcs.VcsException
import com.pafoid.skate.editor.vcs.VcsService
import com.pafoid.skate.editor.vcs.VcsSession

/**
 * EDT-60: Git actions of the Version Control window. Git history cannot be undone with Ctrl+Z, so every one is
 * execute-only (listed in `UndoCoverageGuardTest`). Each runs in the background through [VcsSession.run]; refusals
 * and failures show in the window. [onSuccess] runs on the main side once Git is done (e.g. to reload the project).
 */
abstract class VcsCommand(
    private val session: VcsSession,
    private val onSuccess: () -> Unit = {},
) : ExecuteOnlyCommand {
    /** False when another Git action was still running, so this one did not start. */
    var started = false
        private set

    protected abstract fun action(vcs: VcsService)

    override fun execute() {
        started = session.run(::action, onSuccess)
        if (!started) session.refuse(VcsException("vcs.error.busy"))
    }

    override fun undo() = Unit
}

class InitRepositoryCommand(session: VcsSession, private val firstMessage: String) : VcsCommand(session) {
    private val author = session.author()
    override fun action(vcs: VcsService) = vcs.init(author, firstMessage)
    override fun getDisplayName(): String = "Initialize Repository"
    override fun getTargetName(): String? = null
}

class CommitChangesCommand(session: VcsSession, private val message: String, private val paths: List<String>) : VcsCommand(session) {
    private val author = session.author()
    override fun action(vcs: VcsService) {
        vcs.commit(message, paths, author)
    }
    override fun getDisplayName(): String = "Commit"
    override fun getTargetName(): String = message.lineSequence().first()
}

class CreateBranchCommand(session: VcsSession, private val name: String) : VcsCommand(session) {
    override fun action(vcs: VcsService) = vcs.createBranch(name)
    override fun getDisplayName(): String = "New Branch"
    override fun getTargetName(): String = name
}

class SwitchBranchCommand(session: VcsSession, private val name: String, onSuccess: () -> Unit) : VcsCommand(session, onSuccess) {
    override fun action(vcs: VcsService) = vcs.checkout(name)
    override fun getDisplayName(): String = "Switch Branch"
    override fun getTargetName(): String = name
}

class DeleteBranchCommand(session: VcsSession, private val name: String) : VcsCommand(session) {
    override fun action(vcs: VcsService) = vcs.deleteBranch(name)
    override fun getDisplayName(): String = "Delete Branch"
    override fun getTargetName(): String = name
}

class DiscardChangesCommand(session: VcsSession, private val paths: List<String>, onSuccess: () -> Unit) : VcsCommand(session, onSuccess) {
    override fun action(vcs: VcsService) = vcs.discard(paths)
    override fun getDisplayName(): String = "Discard Changes"
    override fun getTargetName(): String = paths.joinToString()
}

class ResolveConflictCommand(
    session: VcsSession,
    private val path: String,
    private val resolution: Resolution,
    onSuccess: () -> Unit,
) : VcsCommand(session, onSuccess), KeyedCommand {
    override fun action(vcs: VcsService) = vcs.resolve(path, resolution)
    override val displayNameKey: String
        get() = if (resolution == Resolution.MINE) "command.ResolveConflictCommand.mine" else "command.ResolveConflictCommand.theirs"
    override fun getDisplayName(): String = if (resolution == Resolution.MINE) "Keep Mine" else "Take Theirs"
    override fun getTargetName(): String = path
}
