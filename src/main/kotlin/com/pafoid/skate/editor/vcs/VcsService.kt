package com.pafoid.skate.editor.vcs

/** How a file differs from the last commit. */
enum class ChangeKind { ADDED, MODIFIED, DELETED, UNTRACKED, CONFLICTING }

/** One changed file; [path] is relative to the project root, with `/` separators. */
data class FileChange(val path: String, val kind: ChangeKind)

data class VcsAuthor(val name: String, val email: String)

data class VcsCommit(
    val id: String,
    val message: String,
    val author: String,
    val timeMs: Long,
    val files: List<String>,
) {
    val shortId: String get() = id.take(7)
}

data class VcsBranches(val current: String?, val local: List<String>)

data class DiffLine(val kind: Kind, val text: String) {
    enum class Kind { HUNK, CONTEXT, ADDED, REMOVED }
}

sealed class FileDiff {
    abstract val path: String

    data class Text(override val path: String, val lines: List<DiffLine>) : FileDiff()

    /** A binary file: only its size before and after (null when it did not exist). */
    data class Binary(override val path: String, val oldSize: Long?, val newSize: Long?) : FileDiff()
}

/** Which side wins when a conflicted file is resolved as a whole (EDT-60 §7.2, human decision). */
enum class Resolution { MINE, THEIRS }

/**
 * A refused or failed Git action. [messageKey] is a string key (`vcs.error.*`) shown to the user with [args].
 */
class VcsException(val messageKey: String, vararg val args: Any, cause: Throwable? = null) :
    Exception("$messageKey ${args.joinToString()}", cause)

/**
 * EDT-60: local Git operations on one project folder. Every call blocks, so callers run them off the frame
 * ([VcsSession]). Paths are relative to the project root with `/` separators. Refusals throw [VcsException].
 */
interface VcsService {
    fun isRepository(): Boolean

    /** Creates the repository, writes `.gitignore` and `.gitattributes` (§4, §6) and makes a first commit. */
    fun init(author: VcsAuthor, firstMessage: String)

    /** Changed, untracked and conflicted files, in path order. Ignored files are left out. */
    fun status(): List<FileChange>

    /** The working-tree file against the last commit. */
    fun diff(path: String): FileDiff

    /** The file as changed by [commitId] against that commit's first parent. */
    fun diffInCommit(commitId: String, path: String): FileDiff

    /** Commits exactly [paths] (additions, edits and deletions) and returns the commit id. */
    fun commit(message: String, paths: List<String>, author: VcsAuthor): String

    /** The current branch's history, newest first. */
    fun log(limit: Int): List<VcsCommit>

    fun branches(): VcsBranches

    /** Creates [name] from the current commit and switches to it. */
    fun createBranch(name: String)

    /** Switches to [name]; refused while tracked files have uncommitted changes. */
    fun checkout(name: String)

    /** Deletes [name]; refused for the current branch or a branch that is not merged. */
    fun deleteBranch(name: String)

    /** Puts [paths] back to the last commit; files that were never committed are deleted. */
    fun discard(paths: List<String>)

    /** Resolves a conflicted file as a whole: keep our version or take theirs, then mark it resolved. */
    fun resolve(path: String, resolution: Resolution)

    /** The author from Git's own configuration, when it has one. */
    fun configuredAuthor(): VcsAuthor?
}
