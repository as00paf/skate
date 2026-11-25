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

    /** EDT-60 phase 2: sets `origin`. Credentials in [url] are moved to the OS store, never saved in the URL. */
    data class SetRemote(val url: String) : VcsAction("vcs.set_remote")
    object Fetch : VcsAction("vcs.fetch")
    object Pull : VcsAction("vcs.pull")
    object Push : VcsAction("vcs.push")

    /** Stores a user name and token for [host] in the credential store (pasted personal access token). */
    data class SignIn(val host: String, val username: String, val token: String) : VcsAction("vcs.sign_in") {
        override fun toString(): String = "SignIn($host, $username, ***)"
    }
    data class SignOut(val host: String) : VcsAction("vcs.sign_out")
}

/** EDT-60 phase 2: a remote refused the stored credentials (or there were none); the UI opens Sign in for [host]. */
data class VcsSignInRequested(val host: String) : Event("vcs.sign_in_requested")

/** EDT-79: a commit was made ([commitId]); the Commit dialog clears its message. */
data class VcsCommitted(val commitId: String) : Event("vcs.committed")
