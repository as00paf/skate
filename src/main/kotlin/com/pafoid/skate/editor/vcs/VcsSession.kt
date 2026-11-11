package com.pafoid.skate.editor.vcs

import com.pafoid.skate.engine.core.JobSystem
import java.io.File

/** Where [VcsSession] runs Git work and where it publishes results. */
interface VcsRunner {
    fun background(block: () -> Unit)
    fun main(block: () -> Unit)

    /** Git on [JobSystem.runIO], results back on the frame with [JobSystem.runOnMain] (EDT-60 §3). */
    class Jobs(private val jobs: JobSystem) : VcsRunner {
        override fun background(block: () -> Unit) {
            jobs.runIO { block() }
        }

        override fun main(block: () -> Unit) {
            jobs.runOnMain { block() }
        }
    }

    /** Runs everything at once, on the caller's thread (tests). */
    object Immediate : VcsRunner {
        override fun background(block: () -> Unit) = block()
        override fun main(block: () -> Unit) = block()
    }
}

/**
 * One view's diff (EDT-79): the Commit dialog, the Conflicts dialog and the Git log each own one, so they never
 * overwrite each other's. [key] says what was asked for; a result that arrives after the view asked for something
 * else is dropped.
 */
class DiffSlot {
    var key: String? = null
        internal set
    var diff: FileDiff? = null
        internal set

    /** The [VcsSession.State.version] the diff was read at; a working-tree diff is re-read when it is older. */
    var version: Int = -1
        internal set

    fun clear() {
        key = null
        diff = null
        version = -1
    }
}

/**
 * EDT-60, EDT-79: the editor's view of the current project's repository, shared by the status-bar branch button, the
 * Commit and Conflicts dialogs and the Git log window. Reads (status, branches, history, diffs) and the commands' Git
 * actions run one at a time in the background through [runner]; [state] changes only on the main side, so the UI
 * reads it without locking.
 *
 * [state.error] only holds read errors (a failed `git status`). A refused or failed action goes to [onActionFailed]
 * instead (the handler shows it as an `EditorError`, EDT-79 Q9).
 */
class VcsSession(
    private val projectRoot: () -> File?,
    private val runner: VcsRunner,
    private val factory: (File) -> VcsService = ::JGitVcsService,
    private val historyLimit: Int = 300,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class State(
        val root: File? = null,
        val isRepository: Boolean = false,
        val changes: List<FileChange> = emptyList(),
        val branches: VcsBranches? = null,
        /** Every local branch's history, newest first (the Git log). */
        val history: List<VcsCommit> = emptyList(),
        val error: VcsException? = null,
        val busy: Boolean = false,
        val loaded: Boolean = false,
        /** Goes up with every completed read, so views can tell their data is stale. */
        val version: Int = 0,
    ) {
        val entries: List<ChangeEntry> get() = VcsStatusModel.entries(changes)
        val conflicts: List<String> get() = changes.filter { it.kind == ChangeKind.CONFLICTING }.map { it.path }
        val hasConflicts: Boolean get() = changes.any { it.kind == ChangeKind.CONFLICTING }
    }

    /** Called on the main side with every refused or failed action. */
    var onActionFailed: (VcsException) -> Unit = {}

    private var refreshRequested = false
    private var lastReadAt = Long.MIN_VALUE / 2

    @Volatile
    var state = State()
        private set

    private var service: VcsService? = null
    private var serviceRoot: File? = null

    private fun serviceFor(root: File): VcsService {
        if (serviceRoot != root) {
            service = factory(root)
            serviceRoot = root
        }
        return service!!
    }

    /**
     * Call every frame: re-reads the repository when the project changed, and runs a [requestRefresh] once
     * [REFRESH_INTERVAL_MS] has passed since the last read.
     */
    fun followProject() {
        val root = projectRoot()?.absoluteFile
        if (root != state.root && !state.busy) {
            state = State(root = root, version = state.version)
            refreshRequested = false
            if (root != null) refresh()
            return
        }
        if (refreshRequested && !state.busy && state.root != null && clock() - lastReadAt >= REFRESH_INTERVAL_MS) {
            refreshRequested = false
            refresh()
        }
    }

    /** Re-reads now (an explicit Refresh button). */
    fun refresh() {
        run(action = null)
    }

    /**
     * Asks for a re-read soon (EDT-79 §6: a popup or dialog opened, the editor window got focus, files changed, a
     * scene was saved). Requests are coalesced: [followProject] runs at most one read per [REFRESH_INTERVAL_MS].
     */
    fun requestRefresh() {
        refreshRequested = true
    }

    /**
     * Runs [action] on the repository in the background, then re-reads status, branches and history. Returns false
     * (and does nothing) while another action runs or no project is open. [onSuccess] runs on the main side.
     */
    fun run(action: ((VcsService) -> Unit)?, onSuccess: () -> Unit = {}): Boolean {
        val root = state.root ?: projectRoot()?.absoluteFile ?: return false
        if (state.busy) return false
        state = state.copy(root = root, busy = true)
        lastReadAt = clock()
        runner.background {
            val svc = serviceFor(root)
            val failure = try {
                action?.invoke(svc)
                null
            } catch (e: VcsException) {
                e
            } catch (e: RuntimeException) {
                VcsException("vcs.error.git", e.message ?: e.javaClass.simpleName, cause = e)
            }
            val snapshot = read(svc)
            runner.main {
                state = snapshot.copy(root = root, busy = false, version = state.version + 1)
                lastReadAt = clock()
                if (failure == null) onSuccess() else onActionFailed(failure)
            }
        }
        return true
    }

    private fun read(svc: VcsService): State = try {
        if (!svc.isRepository()) State(loaded = true)
        else State(
            isRepository = true, changes = svc.status(), branches = svc.branches(),
            history = svc.log(historyLimit, allBranches = true), loaded = true,
        )
    } catch (e: VcsException) {
        State(loaded = true, isRepository = svc.isRepository(), error = e)
    }

    /** Loads the working-tree diff of [path] into [slot]. */
    fun showDiff(slot: DiffSlot, path: String) = loadDiff(slot, "work:$path") { it.diff(path) }

    /** Loads how [commitId] changed [path] into [slot]. */
    fun showCommitDiff(slot: DiffSlot, commitId: String, path: String) =
        loadDiff(slot, "commit:$commitId:$path") { it.diffInCommit(commitId, path) }

    /** Loads a conflicted file's two sides (ours against theirs) into [slot]. */
    fun showConflictDiff(slot: DiffSlot, path: String) = loadDiff(slot, "conflict:$path") { it.conflictDiff(path) }

    private fun loadDiff(slot: DiffSlot, key: String, read: (VcsService) -> FileDiff) {
        val root = state.root ?: return
        slot.key = key
        val version = state.version
        runner.background {
            // A read failure (the file vanished, the conflict was resolved meanwhile) just shows no diff.
            val result = runCatching { read(serviceFor(root)) }.getOrNull()
            runner.main {
                if (slot.key == key) {
                    slot.diff = result
                    slot.version = version
                }
            }
        }
    }

    /** Reports a refusal decided before any Git work (Play running, unsaved scenes, another action running). */
    fun refuse(error: VcsException) {
        onActionFailed(error)
    }

    /** The author for a new commit: Git's own configuration, else the OS user name. */
    fun author(): VcsAuthor {
        val root = state.root
        val configured = root?.let { runCatching { serviceFor(it).configuredAuthor() }.getOrNull() }
        if (configured != null) return configured
        val user = System.getProperty("user.name")?.takeIf { it.isNotBlank() } ?: "Skate"
        return VcsAuthor(user, "$user@users.noreply.skate")
    }

    companion object {
        /** At most one automatic re-read per second (EDT-79 §6). */
        const val REFRESH_INTERVAL_MS = 1000L
    }
}
