package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.vcs.CommitChangesCommand
import com.pafoid.skate.editor.commands.vcs.CreateBranchCommand
import com.pafoid.skate.editor.commands.vcs.DeleteBranchCommand
import com.pafoid.skate.editor.commands.vcs.DiscardChangesCommand
import com.pafoid.skate.editor.commands.vcs.InitRepositoryCommand
import com.pafoid.skate.editor.commands.vcs.ResolveConflictCommand
import com.pafoid.skate.editor.commands.vcs.SwitchBranchCommand
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.VcsAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.vcs.VcsException
import com.pafoid.skate.editor.vcs.VcsSession
import com.pafoid.skate.editor.vcs.VcsStatusModel
import com.pafoid.skate.engine.core.EventSystem

/**
 * EDT-60: turns [VcsAction] requests into Git commands. Refuses them during Play and, for actions that rewrite
 * project files, while a scene has unsaved edits. After a branch switch, or a discard or resolve that touched scenes
 * or project files, the project is reloaded from disk.
 */
class VcsActionHandler(
    private val eventSystem: EventSystem,
    private val undoRedoManager: UndoRedoManager,
    private val session: VcsSession,
    private val playing: () -> Boolean,
    private val unsavedScenes: () -> Boolean,
    private val projectPath: () -> String?,
) {
    init {
        eventSystem.subscribe<VcsAction.Initialize> { run(false) { InitRepositoryCommand(session, it.firstMessage) } }
        eventSystem.subscribe<VcsAction.Commit> { run(false) { CommitChangesCommand(session, it.message, it.paths) } }
        eventSystem.subscribe<VcsAction.CreateBranch> { run(false) { CreateBranchCommand(session, it.name) } }
        eventSystem.subscribe<VcsAction.DeleteBranch> { run(false) { DeleteBranchCommand(session, it.name) } }
        eventSystem.subscribe<VcsAction.SwitchBranch> { e -> run(true) { SwitchBranchCommand(session, e.name, ::reload) } }
        eventSystem.subscribe<VcsAction.Discard> { e ->
            run(true) { DiscardChangesCommand(session, e.paths) { if (VcsStatusModel.needsReload(e.paths)) reload() } }
        }
        eventSystem.subscribe<VcsAction.Resolve> { e ->
            run(true) { ResolveConflictCommand(session, e.path, e.resolution) { if (VcsStatusModel.needsReload(listOf(e.path))) reload() } }
        }
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
}
