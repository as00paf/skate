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
 * EDT-60: the Version Control window's view of the current project's repository. Reads (status, branches, history,
 * diffs) and the commands' Git actions run one at a time in the background through [runner]; [state] changes only on
 * the main side, so the window reads it without locking.
 */
class VcsSession(
    private val projectRoot: () -> File?,
    private val runner: VcsRunner,
    private val factory: (File) -> VcsService = ::JGitVcsService,
    private val historyLimit: Int = 200,
) {
    data class State(
        val root: File? = null,
        val isRepository: Boolean = false,
        val changes: List<FileChange> = emptyList(),
        val branches: VcsBranches? = null,
        val history: List<VcsCommit> = emptyList(),
        val diff: FileDiff? = null,
        /** The commit whose file [diff] shows, or null for a working-tree diff. */
        val diffCommit: String? = null,
        val error: VcsException? = null,
        val busy: Boolean = false,
        val loaded: Boolean = false,
    ) {
        val entries: List<ChangeEntry> get() = VcsStatusModel.entries(changes)
        val hasConflicts: Boolean get() = changes.any { it.kind == ChangeKind.CONFLICTING }
    }

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

    /** Re-reads the repository when the project changed since the last read (call every frame). */
    fun followProject() {
        val root = projectRoot()?.absoluteFile
        if (root != state.root && !state.busy) {
            state = State(root = root)
            if (root != null) refresh()
        }
    }

    fun refresh() {
        run(action = null)
    }

    /**
     * Runs [action] on the repository in the background, then re-reads status, branches and history. Returns false
     * (and does nothing) while another action runs or no project is open. [onSuccess] runs on the main side.
     */
    fun run(action: ((VcsService) -> Unit)?, onSuccess: () -> Unit = {}): Boolean {
        val root = state.root ?: projectRoot()?.absoluteFile ?: return false
        if (state.busy) return false
        state = state.copy(root = root, busy = true, error = if (action != null) null else state.error)
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
                val keptDiff = if (failure == null && action != null) null else state.diff
                state = snapshot.copy(
                    root = root, busy = false, error = failure ?: snapshot.error ?: state.error.takeIf { action == null },
                    diff = keptDiff, diffCommit = if (keptDiff == null) null else state.diffCommit,
                )
                if (failure == null) onSuccess()
            }
        }
        return true
    }

    private fun read(svc: VcsService): State = try {
        if (!svc.isRepository()) State(loaded = true)
        else State(isRepository = true, changes = svc.status(), branches = svc.branches(), history = svc.log(historyLimit), loaded = true)
    } catch (e: VcsException) {
        State(loaded = true, isRepository = svc.isRepository(), error = e)
    }

    /** Shows the working-tree diff of [path]. */
    fun showDiff(path: String) = loadDiff(null) { it.diff(path) }

    /** Shows how [commitId] changed [path]. */
    fun showCommitDiff(commitId: String, path: String) = loadDiff(commitId) { it.diffInCommit(commitId, path) }

    private fun loadDiff(commitId: String?, read: (VcsService) -> FileDiff) {
        val root = state.root ?: return
        runner.background {
            val result = runCatching { read(serviceFor(root)) }
            runner.main {
                state = result.fold(
                    { state.copy(diff = it, diffCommit = commitId) },
                    { e -> state.copy(diff = null, error = e as? VcsException ?: VcsException("vcs.error.git", e.message.orEmpty(), cause = e)) },
                )
            }
        }
    }

    /** Shows a refusal decided before any Git work (Play running, unsaved scenes). */
    fun refuse(error: VcsException) {
        state = state.copy(error = error)
    }

    fun clearError() {
        state = state.copy(error = null)
    }

    /** The author for a new commit: Git's own configuration, else the OS user name. */
    fun author(): VcsAuthor {
        val root = state.root
        val configured = root?.let { runCatching { serviceFor(it).configuredAuthor() }.getOrNull() }
        if (configured != null) return configured
        val user = System.getProperty("user.name")?.takeIf { it.isNotBlank() } ?: "Skate"
        return VcsAuthor(user, "$user@users.noreply.skate")
    }
}
