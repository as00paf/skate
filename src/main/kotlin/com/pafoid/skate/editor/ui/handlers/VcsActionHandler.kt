package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.vcs.CommitChangesCommand
import com.pafoid.skate.editor.commands.vcs.CreateBranchCommand
import com.pafoid.skate.editor.commands.vcs.DeleteBranchCommand
import com.pafoid.skate.editor.commands.vcs.DiscardChangesCommand
import com.pafoid.skate.editor.commands.vcs.FetchCommand
import com.pafoid.skate.editor.commands.vcs.InitRepositoryCommand
import com.pafoid.skate.editor.commands.vcs.PullCommand
import com.pafoid.skate.editor.commands.vcs.PushCommand
import com.pafoid.skate.editor.commands.vcs.ResolveConflictCommand
import com.pafoid.skate.editor.commands.vcs.SetRemoteCommand
import com.pafoid.skate.editor.commands.vcs.SwitchBranchCommand
import com.pafoid.skate.editor.events.EditorError
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.ScriptEditorAction
import com.pafoid.skate.editor.events.VcsAction
import com.pafoid.skate.editor.events.VcsCommitted
import com.pafoid.skate.editor.events.VcsSignInRequested
import com.pafoid.skate.editor.vcs.PullOutcome
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.vcs.Resolution
import com.pafoid.skate.editor.vcs.VcsException
import com.pafoid.skate.editor.vcs.VcsSession
import com.pafoid.skate.editor.vcs.VcsStatusModel
import com.pafoid.skate.editor.vcs.auth.GitCredential
import com.pafoid.skate.editor.vcs.auth.RemoteUrls
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.events.SceneAction

/**
 * EDT-60, EDT-79: turns [VcsAction] requests into Git commands. Refuses them during Play and, for actions that
 * rewrite project files, while a scene has unsaved edits. After a branch switch, or a discard or resolve that touched
 * scenes or project files, the project is reloaded from disk.
 *
 * Results (EDT-79 Q9): a refusal or failure becomes an [EditorError] modal, a success a status-bar toast through
 * [notify] (a string key and its arguments). A commit also publishes [VcsCommitted]. Saves and file changes ask the
 * session for a (debounced) refresh, so the branch button's count stays current.
 */
class VcsActionHandler(
    private val eventSystem: EventSystem,
    private val undoRedoManager: UndoRedoManager,
    private val session: VcsSession,
    private val playing: () -> Boolean,
    private val unsavedScenes: () -> Boolean,
    private val projectPath: () -> String?,
    private val notify: (key: String, args: List<Any>) -> Unit = { _, _ -> },
) {
    init {
        session.onActionFailed = { e ->
            // EDT-60 phase 2: an authentication failure opens Sign in instead of an error.
            if (e.messageKey == AUTH_REQUIRED && e.args.isNotEmpty()) {
                eventSystem.publish(VcsSignInRequested(e.args[0].toString()))
            } else {
                eventSystem.publish(EditorError(ERROR_TITLE, messageKey = e.messageKey, messageArgs = e.args.toList()))
            }
        }
        eventSystem.subscribe<VcsAction.Initialize> { e ->
            run(false) { InitRepositoryCommand(session, e.firstMessage) { notify("vcs.toast.initialized", emptyList()) } }
        }
        eventSystem.subscribe<VcsAction.Commit> { e ->
            run(false) {
                CommitChangesCommand(session, e.message, e.paths) { c ->
                    val id = c.commitId.orEmpty()
                    notify("vcs.toast.committed", listOf(id.take(7), e.paths.size))
                    eventSystem.publish(VcsCommitted(id))
                }
            }
        }
        eventSystem.subscribe<VcsAction.CreateBranch> { e ->
            run(false) {
                CreateBranchCommand(session, e.name.trim(), e.from, e.checkout) {
                    notify(if (e.checkout) "vcs.toast.branch_created_switched" else "vcs.toast.branch_created", listOf(e.name.trim()))
                }
            }
        }
        eventSystem.subscribe<VcsAction.DeleteBranch> { e ->
            run(false) { DeleteBranchCommand(session, e.name) { notify("vcs.toast.branch_deleted", listOf(e.name)) } }
        }
        eventSystem.subscribe<VcsAction.SwitchBranch> { e ->
            run(true) {
                SwitchBranchCommand(session, e.name) {
                    notify("vcs.toast.switched", listOf(e.name))
                    reload()
                }
            }
        }
        eventSystem.subscribe<VcsAction.Discard> { e ->
            run(true) {
                DiscardChangesCommand(session, e.paths) {
                    notify("vcs.toast.discarded", listOf(e.paths.size))
                    if (VcsStatusModel.needsReload(e.paths)) reload()
                }
            }
        }
        eventSystem.subscribe<VcsAction.Resolve> { e ->
            run(true) {
                ResolveConflictCommand(session, e.paths, e.resolution) {
                    notify(if (e.resolution == Resolution.MINE) "vcs.toast.kept_mine" else "vcs.toast.took_theirs", listOf(e.paths.size))
                    if (VcsStatusModel.needsReload(e.paths)) reload()
                }
            }
        }
        eventSystem.subscribe<VcsAction.SetRemote> { e ->
            val parsed = RemoteUrls.strip(e.url)
            val host = RemoteUrls.host(parsed.url)
            // EDT-60 §5: credentials pasted in the URL go to the credential store; the URL is saved without them.
            val moved = parsed.secret != null && host != null
            if (moved) session.credentials.save(host!!, GitCredential(parsed.username ?: "git", parsed.secret!!))
            run(false) {
                SetRemoteCommand(session, parsed.url) {
                    notify(if (moved) "vcs.toast.remote_set_credentials_moved" else "vcs.toast.remote_set", listOf(parsed.url))
                }
            }
        }
        eventSystem.subscribe<VcsAction.Fetch> {
            run(false) { FetchCommand(session) { notify("vcs.toast.fetched", emptyList()) } }
        }
        eventSystem.subscribe<VcsAction.Pull> {
            run(true) {
                PullCommand(session) { outcome ->
                    notify(
                        when (outcome) {
                            PullOutcome.UP_TO_DATE -> "vcs.toast.up_to_date"
                            PullOutcome.FAST_FORWARD, PullOutcome.MERGED -> "vcs.toast.pulled"
                            PullOutcome.CONFLICTS -> "vcs.toast.pull_conflicts"
                        },
                        emptyList(),
                    )
                    // The Conflicts dialog opens by itself (ConflictPrompt); conflicted scenes keep our version.
                    if (outcome != PullOutcome.UP_TO_DATE) reload()
                }
            }
        }
        eventSystem.subscribe<VcsAction.Push> {
            run(false) { PushCommand(session) { notify("vcs.toast.pushed", emptyList()) } }
        }
        eventSystem.subscribe<VcsAction.SignIn> { e ->
            if (e.token.isBlank()) {
                session.refuse(VcsException("vcs.signin.token_required"))
                return@subscribe
            }
            session.credentials.save(e.host, GitCredential(e.username.trim().ifEmpty { "git" }, e.token.trim()))
            notify(if (session.credentials.store.persistent) "vcs.toast.signed_in" else "vcs.toast.signed_in_session", listOf(e.host))
        }
        eventSystem.subscribe<VcsAction.SignOut> { e ->
            session.credentials.signOut(e.host)
            notify("vcs.toast.signed_out", listOf(e.host))
        }
        // EDT-79 §6: without a window to focus, these keep the branch button's count fresh.
        eventSystem.subscribe<ProjectEvent.Saved> { session.requestRefresh() }
        eventSystem.subscribe<FileSystemEvent.FileSystemChangedEvent> { session.requestRefresh() }
        eventSystem.subscribe<ScriptEditorAction.Saved> { session.requestRefresh() }
        eventSystem.subscribe<SceneAction.Saved> { session.requestRefresh() }
    }

    private fun run(changesFiles: Boolean, command: () -> Command) {
        val refusal = VcsStatusModel.actionRefusal(changesFiles, playing(), unsavedScenes())
        if (refusal != null) {
            session.refuse(VcsException(refusal))
            return
        }
        undoRedoManager.executeCommand(command())
    }

    private fun reload() {
        projectPath()?.let { eventSystem.publish(ProjectEvent.OpenProjectRequested(it)) }
    }

    companion object {
        const val ERROR_TITLE = "vcs.error.title"
        const val AUTH_REQUIRED = "vcs.error.auth_required"
    }
}
