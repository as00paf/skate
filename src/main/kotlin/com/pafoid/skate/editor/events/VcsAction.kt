package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.vcs.Resolution
import com.pafoid.skate.engine.events.Event

/** EDT-60: requests from the Version Control window, turned into Git commands by `VcsActionHandler`. */
sealed class VcsAction(eventName: String) : Event(eventName) {
    data class Initialize(val firstMessage: String) : VcsAction("vcs.initialize")
    data class Commit(val message: String, val paths: List<String>) : VcsAction("vcs.commit")
    data class CreateBranch(val name: String) : VcsAction("vcs.create_branch")
    data class SwitchBranch(val name: String) : VcsAction("vcs.switch_branch")
    data class DeleteBranch(val name: String) : VcsAction("vcs.delete_branch")
    data class Discard(val paths: List<String>) : VcsAction("vcs.discard")
    data class Resolve(val path: String, val resolution: Resolution) : VcsAction("vcs.resolve")
}
