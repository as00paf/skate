package com.pafoid.skate.editor.vcs

/** Groups of the Changes list, in display order (EDT-60 §2). */
enum class ChangeGroup(val labelKey: String) {
    CONFLICTS("vcs.group.conflicts"),
    SCENES("vcs.group.scenes"),
    PREFABS("vcs.group.prefabs"),
    SCRIPTS("vcs.group.scripts"),
    ASSETS("vcs.group.assets"),
    SETTINGS("vcs.group.settings"),
    OTHER("vcs.group.other"),
}

/** One row of the Changes list: a file, plus its `.meta` sidecar when that changed too (they are staged together). */
data class ChangeEntry(val change: FileChange, val meta: FileChange? = null) {
    val path: String get() = change.path
    val paths: List<String> get() = listOfNotNull(change.path, meta?.path)
}

/**
 * EDT-60: pure logic behind the Version Control window. Groups changed files, pairs each asset with its `.meta`
 * and decides whether Commit is allowed. No JGit, no ImGui.
 */
object VcsStatusModel {
    private const val META = ".meta"
    private val scriptExtensions = setOf("kts", "kt", "ts", "js", "lua")
    private val settingsFiles = setOf("game.settings.json", ".gitignore", ".gitattributes")

    /** Files under the never-committed folders ([GitIgnoreTemplate.ignoredFolders]). */
    fun isIgnored(path: String): Boolean {
        val top = path.substringBefore('/')
        return path.contains('/') && GitIgnoreTemplate.ignoredFolders.any { it.equals(top, ignoreCase = true) }
    }

    fun entries(changes: List<FileChange>): List<ChangeEntry> {
        val visible = changes.filter { !isIgnored(it.path) }
        val byPath = visible.associateBy { it.path }
        // A conflicted file or .meta stays its own row, so each can be resolved on its own.
        val paired = visible.filter { meta ->
            meta.path.endsWith(META) && meta.kind != ChangeKind.CONFLICTING &&
                byPath[meta.path.removeSuffix(META)]?.let { it.kind != ChangeKind.CONFLICTING } == true
        }.map { it.path }.toSet()
        return visible.filter { it.path !in paired }
            .map { c -> ChangeEntry(c, byPath[c.path + META]?.takeIf { it.path in paired }) }
            .sortedBy { it.path }
    }

    fun groupOf(change: FileChange): ChangeGroup {
        if (change.kind == ChangeKind.CONFLICTING) return ChangeGroup.CONFLICTS
        val path = change.path.removeSuffix(META)
        val name = path.substringAfterLast('/')
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext == "scene" -> ChangeGroup.SCENES
            ext == "prefab" -> ChangeGroup.PREFABS
            ext in scriptExtensions -> ChangeGroup.SCRIPTS
            ext == "skateproject" || (!path.contains('/') && name in settingsFiles) -> ChangeGroup.SETTINGS
            path.startsWith("Assets/", ignoreCase = true) -> ChangeGroup.ASSETS
            else -> ChangeGroup.OTHER
        }
    }

    fun grouped(entries: List<ChangeEntry>): Map<ChangeGroup, List<ChangeEntry>> =
        entries.groupBy { groupOf(it.change) }.toSortedMap()

    /** Every path to commit or discard for the selected rows ([selected] holds row paths), `.meta` sidecars included. */
    fun pathsFor(entries: List<ChangeEntry>, selected: Set<String>): List<String> =
        entries.filter { it.path in selected && it.change.kind != ChangeKind.CONFLICTING }.flatMap { it.paths }

    /** Why Commit is disabled (a `vcs.commit.blocked.*` key), or null when it is allowed. */
    fun commitBlockedReason(message: String, selectedPaths: List<String>, playing: Boolean, busy: Boolean, hasConflicts: Boolean): String? = when {
        playing -> "vcs.commit.blocked.playing"
        busy -> "vcs.commit.blocked.busy"
        hasConflicts -> "vcs.commit.blocked.conflicts"
        selectedPaths.isEmpty() -> "vcs.commit.blocked.nothing_selected"
        message.isBlank() -> "vcs.commit.blocked.no_message"
        else -> null
    }

    /**
     * Why a Git action is refused before it starts (a `vcs.error.*` key), or null. Nothing runs during Play (EDT-45,
     * same rule as saving), and an action that rewrites project files ([changesFiles]) waits for unsaved scenes.
     */
    fun actionRefusal(changesFiles: Boolean, playing: Boolean, unsavedScenes: Boolean): String? = when {
        playing -> "vcs.error.playing"
        changesFiles && unsavedScenes -> "vcs.error.unsaved_scenes"
        else -> null
    }

    /** True when rewriting [paths] on disk needs the project reloaded (scenes, prefabs and project files). */
    fun needsReload(paths: List<String>): Boolean = paths.any { p ->
        val ext = p.substringAfterLast('.', "").lowercase()
        ext == "scene" || ext == "prefab" || ext == "skateproject" || p == "game.settings.json"
    }

    /** One-letter status shown in front of a file, like `git status --short`. */
    fun letter(kind: ChangeKind): String = when (kind) {
        ChangeKind.ADDED, ChangeKind.UNTRACKED -> "A"
        ChangeKind.MODIFIED -> "M"
        ChangeKind.DELETED -> "D"
        ChangeKind.CONFLICTING -> "!"
    }
}
