package com.pafoid.skate.editor.commands.vcs

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.editor.commands.KeyedCommand
import com.pafoid.skate.editor.vcs.Resolution
import com.pafoid.skate.editor.vcs.VcsException
import com.pafoid.skate.editor.vcs.VcsService
import com.pafoid.skate.editor.vcs.VcsSession

/**
 * EDT-60, EDT-79: Git actions of the branch button and the VCS dialogs. Git history cannot be undone with Ctrl+Z, so
 * every one is execute-only (listed in `UndoCoverageGuardTest`). Each runs in the background through
 * [VcsSession.run]; refusals and failures go to [VcsSession.onActionFailed]. [onSuccess] runs on the main side once
 * Git is done (reload the project, show a toast).
 */
abstract class VcsCommand(
    private val session: VcsSession,
    private val onSuccess: () -> Unit = {},
) : ExecuteOnlyCommand {
    /** False when another Git action was still running, so this one did not start. */
    var started = false
        private set

    protected abstract fun action(vcs: VcsService)

    /** Runs on the main side after a successful [action]; the default runs [onSuccess]. */
    protected open fun succeeded() = onSuccess()

    override fun execute() {
        started = session.run(::action, ::succeeded)
        if (!started) session.refuse(VcsException("vcs.error.busy"))
    }

    override fun undo() = Unit
}

class InitRepositoryCommand(session: VcsSession, private val firstMessage: String, onSuccess: () -> Unit = {}) : VcsCommand(session, onSuccess) {
    private val author = session.author()
    override fun action(vcs: VcsService) = vcs.init(author, firstMessage)
    override fun getDisplayName(): String = "Initialize Repository"
    override fun getTargetName(): String? = null
}

class CommitChangesCommand(
    session: VcsSession,
    private val message: String,
    private val paths: List<String>,
    onSuccess: (CommitChangesCommand) -> Unit = {},
) : VcsCommand(session) {
    private val author = session.author()

    /** The new commit's id, once Git is done. */
    @Volatile
    var commitId: String? = null
        private set

    private val done = onSuccess

    override fun action(vcs: VcsService) {
        commitId = vcs.commit(message, paths, author)
    }

    override fun succeeded() = done(this)
    override fun getDisplayName(): String = "Commit"
    override fun getTargetName(): String = message.lineSequence().first()
}

class CreateBranchCommand(
    session: VcsSession,
    private val name: String,
    private val from: String? = null,
    private val checkout: Boolean = true,
    onSuccess: () -> Unit = {},
) : VcsCommand(session, onSuccess) {
    override fun action(vcs: VcsService) = vcs.createBranch(name, from, checkout)
    override fun getDisplayName(): String = "New Branch"
    override fun getTargetName(): String = name
}

class SwitchBranchCommand(session: VcsSession, private val name: String, onSuccess: () -> Unit) : VcsCommand(session, onSuccess) {
    override fun action(vcs: VcsService) = vcs.checkout(name)
    override fun getDisplayName(): String = "Switch Branch"
    override fun getTargetName(): String = name
}

class DeleteBranchCommand(session: VcsSession, private val name: String, onSuccess: () -> Unit = {}) : VcsCommand(session, onSuccess) {
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
    private val paths: List<String>,
    private val resolution: Resolution,
    onSuccess: () -> Unit,
) : VcsCommand(session, onSuccess), KeyedCommand {
    override fun action(vcs: VcsService) = paths.forEach { vcs.resolve(it, resolution) }
    override val displayNameKey: String
        get() = if (resolution == Resolution.MINE) "command.ResolveConflictCommand.mine" else "command.ResolveConflictCommand.theirs"
    override fun getDisplayName(): String = if (resolution == Resolution.MINE) "Keep Mine" else "Take Theirs"
    override fun getTargetName(): String = paths.joinToString()
}
