package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.vcs.Resolution
import com.pafoid.skate.engine.events.Event

/** EDT-60, EDT-79: Git requests from the status-bar branch button and the VCS dialogs, run by `VcsActionHandler`. */
sealed class VcsAction(eventName: String) : Event(eventName) {
    data class Initialize(val firstMessage: String) : VcsAction("vcs.initialize")
    data class Commit(val message: String, val paths: List<String>) : VcsAction("vcs.commit")
    /** A new branch from [from] (null: the current commit); [checkout] switches to it. */
    data class CreateBranch(val name: String, val from: String? = null, val checkout: Boolean = true) : VcsAction("vcs.create_branch")
    data class SwitchBranch(val name: String) : VcsAction("vcs.switch_branch")
    data class DeleteBranch(val name: String) : VcsAction("vcs.delete_branch")
    data class Discard(val paths: List<String>) : VcsAction("vcs.discard")
    /** Resolves every one of [paths] as a whole with [resolution], in one Git action (EDT-79: several files at once). */
    data class Resolve(val paths: List<String>, val resolution: Resolution) : VcsAction("vcs.resolve")
}

/** EDT-79: a commit was made ([commitId]); the Commit dialog clears its message. */
data class VcsCommitted(val commitId: String) : Event("vcs.committed")
