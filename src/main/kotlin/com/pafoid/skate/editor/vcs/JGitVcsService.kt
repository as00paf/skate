package com.pafoid.skate.editor.vcs

import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.CheckoutConflictException
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.api.errors.NotMergedException
import org.eclipse.jgit.api.errors.RefAlreadyExistsException
import org.eclipse.jgit.diff.DiffAlgorithm
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File

/**
 * EDT-60 phase 1: [VcsService] on JGit, so users need no Git install. The only file that imports `org.eclipse.jgit`
 * (`VcsLayeringGuardTest`). The repository must sit directly in [root] (`<root>/.git`): a project inside another
 * repository (e.g. under the engine checkout) is not treated as versioned. Each call opens and closes the repository,
 * so no pack file stays locked on Windows between calls.
 */
class JGitVcsService(private val root: File) : VcsService {
    private val gitDir get() = File(root, Constants.DOT_GIT)

    override fun isRepository(): Boolean = File(gitDir, "HEAD").isFile

    private fun <T> git(block: (Git) -> T): T {
        if (!isRepository()) throw VcsException("vcs.error.not_repository")
        return FileRepositoryBuilder().setGitDir(gitDir).setWorkTree(root).setMustExist(true).build().use { repo ->
            Git(repo).use { g -> wrap { block(g) } }
        }
    }

    private inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: VcsException) {
        throw e
    } catch (e: GitAPIException) {
        throw VcsException("vcs.error.git", e.message ?: e.javaClass.simpleName, cause = e)
    } catch (e: java.io.IOException) {
        throw VcsException("vcs.error.git", e.message ?: e.javaClass.simpleName, cause = e)
    }

    override fun init(author: VcsAuthor, firstMessage: String) {
        if (isRepository()) throw VcsException("vcs.error.already_repository")
        wrap { Git.init().setDirectory(root).setInitialBranch(DEFAULT_BRANCH).call().close() }
        writeManaged(".gitignore", GitIgnoreTemplate.gitignoreBlock)
        writeManaged(".gitattributes", GitIgnoreTemplate.gitattributes)
        git { g ->
            g.add().addFilepattern(".").call()
            g.commit().setMessage(firstMessage).setAuthor(author.name, author.email)
                .setCommitter(author.name, author.email).setAllowEmpty(true).call()
        }
    }

    private fun writeManaged(name: String, block: String) {
        val file = File(root, name)
        val existing = if (file.isFile) file.readText() else null
        val merged = GitIgnoreTemplate.mergeInto(existing, block)
        if (merged != existing) file.writeText(merged)
    }

    override fun status(): List<FileChange> = git { g ->
        val s = g.status().call()
        val byPath = linkedMapOf<String, ChangeKind>()
        fun put(paths: Set<String>, kind: ChangeKind) = paths.forEach { byPath.putIfAbsent(it, kind) }
        put(s.conflicting, ChangeKind.CONFLICTING)
        put(s.added, ChangeKind.ADDED)
        put(s.removed, ChangeKind.DELETED)
        put(s.missing, ChangeKind.DELETED)
        put(s.changed, ChangeKind.MODIFIED)
        put(s.modified, ChangeKind.MODIFIED)
        put(s.untracked, ChangeKind.UNTRACKED)
        byPath.map { (path, kind) -> FileChange(path, kind) }.sortedBy { it.path }
    }

    override fun diff(path: String): FileDiff = git { g ->
        val repo = g.repository
        val old = repo.resolve(Constants.HEAD)?.let { head -> RevWalk(repo).use { blobAt(repo, it.parseCommit(head), path) } }
        val file = File(root, path)
        TextDiff.of(path, old, if (file.isFile) file.readBytes() else null)
    }

    override fun diffInCommit(commitId: String, path: String): FileDiff = git { g ->
        val repo = g.repository
        RevWalk(repo).use { walk ->
            val commit = walk.parseCommit(repo.resolve(commitId) ?: throw VcsException("vcs.error.unknown_commit", commitId))
            val parent = commit.parents.firstOrNull()?.let { walk.parseCommit(it) }
            TextDiff.of(path, parent?.let { blobAt(repo, it, path) }, blobAt(repo, commit, path))
        }
    }

    private fun blobAt(repo: Repository, commit: RevCommit, path: String): ByteArray? =
        TreeWalk.forPath(repo, path, commit.tree)?.use { tw -> repo.open(tw.getObjectId(0)).bytes }

    override fun commit(message: String, paths: List<String>, author: VcsAuthor): String {
        if (message.isBlank()) throw VcsException("vcs.error.no_message")
        if (paths.isEmpty()) throw VcsException("vcs.error.nothing_selected")
        return git { g ->
            val repo = g.repository
            val merging = repo.repositoryState != RepositoryState.SAFE
            if (merging) {
                if (g.status().call().conflicting.isNotEmpty()) throw VcsException("vcs.error.unresolved_conflicts")
            } else if (repo.resolve(Constants.HEAD) != null) {
                // Only the selected files go into the commit: unstage everything else first (index back to HEAD).
                g.reset().call()
            }
            val (present, gone) = paths.distinct().partition { File(root, it).exists() }
            if (present.isNotEmpty()) g.add().apply { present.forEach { addFilepattern(it) } }.call()
            if (gone.isNotEmpty()) g.rm().setCached(true).apply { gone.forEach { addFilepattern(it) } }.call()
            g.commit().setMessage(message.trim()).setAuthor(author.name, author.email)
                .setCommitter(author.name, author.email).call().name
        }
    }

    override fun log(limit: Int, allBranches: Boolean): List<VcsCommit> = git { g ->
        val repo = g.repository
        if (repo.resolve(Constants.HEAD) == null) return@git emptyList()
        val command = g.log().setMaxCount(limit)
        if (allBranches) {
            repo.refDatabase.getRefsByPrefix(Constants.R_HEADS).forEach { ref -> ref.objectId?.let { command.add(it) } }
            command.add(repo.resolve(Constants.HEAD))
        }
        command.call().map { c ->
            VcsCommit(
                c.name, c.fullMessage.trim(), c.authorIdent.name, c.authorIdent.whenAsInstant.toEpochMilli(),
                changedFiles(repo, c), c.parents.map { it.name },
            )
        }
    }

    private fun changedFiles(repo: Repository, commit: RevCommit): List<String> = repo.newObjectReader().use { reader ->
        val parent = commit.parents.firstOrNull()
        val oldTree = if (parent == null) EmptyTreeIterator() else RevWalk(repo).use { w ->
            CanonicalTreeParser(null, reader, w.parseCommit(parent).tree)
        }
        val newTree = CanonicalTreeParser(null, reader, commit.tree)
        TreeWalk(repo, reader).use { tw ->
            tw.isRecursive = true
            tw.addTree(oldTree)
            tw.addTree(newTree)
            DiffEntry.scan(tw).map { if (it.changeType == DiffEntry.ChangeType.DELETE) it.oldPath else it.newPath }.sorted()
        }
    }

    override fun branches(): VcsBranches = git { g ->
        val repo = g.repository
        val refs = g.branchList().call()
        val onBranch = repo.fullBranch?.startsWith(Constants.R_HEADS) == true
        VcsBranches(
            current = if (onBranch) repo.branch else null,
            local = refs.map { Repository.shortenRefName(it.name) }.sorted(),
            tips = refs.mapNotNull { ref -> ref.objectId?.let { Repository.shortenRefName(ref.name) to it.name } }.toMap(),
            head = repo.resolve(Constants.HEAD)?.name,
        )
    }

    override fun createBranch(name: String, from: String?, checkout: Boolean) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || !Repository.isValidRefName(Constants.R_HEADS + trimmed)) {
            throw VcsException("vcs.error.invalid_branch_name", trimmed)
        }
        git { g ->
            val repo = g.repository
            if (repo.resolve(Constants.HEAD) == null) throw VcsException("vcs.error.no_commits")
            if (from != null && repo.findRef(Constants.R_HEADS + from) == null) throw VcsException("vcs.error.unknown_branch", from)
            val start = from?.let { Constants.R_HEADS + it }
            try {
                if (checkout) {
                    g.checkout().setCreateBranch(true).setName(trimmed).apply { start?.let { setStartPoint(it) } }.call()
                } else {
                    g.branchCreate().setName(trimmed).apply { start?.let { setStartPoint(it) } }.call()
                }
            } catch (e: RefAlreadyExistsException) {
                throw VcsException("vcs.error.branch_exists", trimmed, cause = e)
            }
        }
    }

    override fun checkout(name: String) {
        if (status().any { it.kind != ChangeKind.UNTRACKED }) throw VcsException("vcs.error.uncommitted_changes")
        git { g ->
            if (g.repository.findRef(Constants.R_HEADS + name) == null) throw VcsException("vcs.error.unknown_branch", name)
            try {
                g.checkout().setName(name).call()
            } catch (e: CheckoutConflictException) {
                throw VcsException("vcs.error.checkout_conflict", e.conflictingPaths.joinToString(), cause = e)
            }
        }
    }

    override fun deleteBranch(name: String) = git { g ->
        if (g.repository.branch == name) throw VcsException("vcs.error.delete_current", name)
        try {
            g.branchDelete().setBranchNames(name).setForce(false).call()
            Unit
        } catch (e: NotMergedException) {
            throw VcsException("vcs.error.not_merged", name, cause = e)
        }
    }

    override fun discard(paths: List<String>) {
        if (paths.isEmpty()) return
        val kinds = status().associate { it.path to it.kind }
        git { g ->
            val repo = g.repository
            val head = repo.resolve(Constants.HEAD)
            val tracked = mutableListOf<String>()
            paths.distinct().forEach { path ->
                when (kinds[path]) {
                    null -> Unit
                    ChangeKind.CONFLICTING -> throw VcsException("vcs.error.discard_conflict", path)
                    ChangeKind.UNTRACKED -> File(root, path).delete()
                    ChangeKind.ADDED -> {
                        g.rm().setCached(true).addFilepattern(path).call()
                        File(root, path).delete()
                    }
                    else -> tracked += path
                }
            }
            if (tracked.isNotEmpty() && head != null) {
                g.checkout().setStartPoint(head.name).apply { tracked.forEach { addPath(it) } }.call()
            }
        }
    }

    override fun resolve(path: String, resolution: Resolution) = git { g ->
        if (path !in g.status().call().conflicting) throw VcsException("vcs.error.not_conflicted", path)
        val stage = if (resolution == Resolution.MINE) CheckoutCommand.Stage.OURS else CheckoutCommand.Stage.THEIRS
        if (stageExists(g, path, stage)) {
            g.checkout().setStage(stage).addPath(path).call()
            g.add().addFilepattern(path).call()
        } else {
            // That side deleted the file: resolving to it deletes the file.
            File(root, path).delete()
            g.rm().addFilepattern(path).call()
        }
        Unit
    }

    private fun stageExists(g: Git, path: String, stage: CheckoutCommand.Stage): Boolean =
        stageBytes(g, path, if (stage == CheckoutCommand.Stage.OURS) 2 else 3) != null

    /** The blob of [path] at index [stage] (2 = ours, 3 = theirs), or null when that side has no such file. */
    private fun stageBytes(g: Git, path: String, stage: Int): ByteArray? {
        val repo = g.repository
        val cache = repo.readDirCache()
        val entry = (0 until cache.entryCount).map { cache.getEntry(it) }.firstOrNull { it.pathString == path && it.stage == stage }
        return entry?.let { repo.open(it.objectId).bytes }
    }

    override fun conflictDiff(path: String): FileDiff = git { g ->
        if (path !in g.status().call().conflicting) throw VcsException("vcs.error.not_conflicted", path)
        TextDiff.of(path, stageBytes(g, path, 2), stageBytes(g, path, 3))
    }

    override fun configuredAuthor(): VcsAuthor? {
        if (!isRepository()) return null
        return git { g ->
            val config = g.repository.config
            val name = config.getString("user", null, "name")?.takeIf { it.isNotBlank() } ?: return@git null
            VcsAuthor(name, config.getString("user", null, "email").orEmpty())
        }
    }

    companion object {
        const val DEFAULT_BRANCH = "main"
    }
}

/** Unified diff of two file versions, three lines of context, or a [FileDiff.Binary] when either side is binary. */
internal object TextDiff {
    private const val CONTEXT = 3

    fun of(path: String, old: ByteArray?, new: ByteArray?): FileDiff {
        if ((old != null && RawText.isBinary(old)) || (new != null && RawText.isBinary(new))) {
            return FileDiff.Binary(path, old?.size?.toLong(), new?.size?.toLong())
        }
        val a = RawText(old ?: ByteArray(0))
        val b = RawText(new ?: ByteArray(0))
        val edits = DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM).diff(RawTextComparator.DEFAULT, a, b)
        return FileDiff.Text(path, hunks(edits, a, b))
    }

    private fun hunks(edits: List<Edit>, a: RawText, b: RawText): List<DiffLine> {
        val out = mutableListOf<DiffLine>()
        var i = 0
        while (i < edits.size) {
            // Merge edits whose context overlaps into one hunk.
            var j = i
            while (j + 1 < edits.size && edits[j + 1].beginA - edits[j].endA <= 2 * CONTEXT) j++
            val first = edits[i]
            val last = edits[j]
            val startA = (first.beginA - CONTEXT).coerceAtLeast(0)
            val endA = (last.endA + CONTEXT).coerceAtMost(a.size())
            val startB = (first.beginB - CONTEXT).coerceAtLeast(0)
            val endB = (last.endB + CONTEXT).coerceAtMost(b.size())
            out += DiffLine(DiffLine.Kind.HUNK, "@@ -${startA + 1},${endA - startA} +${startB + 1},${endB - startB} @@")
            var posA = startA
            for (k in i..j) {
                val e = edits[k]
                while (posA < e.beginA) out += DiffLine(DiffLine.Kind.CONTEXT, a.getString(posA++))
                for (x in e.beginA until e.endA) out += DiffLine(DiffLine.Kind.REMOVED, a.getString(x))
                for (x in e.beginB until e.endB) out += DiffLine(DiffLine.Kind.ADDED, b.getString(x))
                posA = e.endA
            }
            while (posA < endA) out += DiffLine(DiffLine.Kind.CONTEXT, a.getString(posA++))
            i = j + 1
        }
        return out
    }
}
