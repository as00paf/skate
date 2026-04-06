package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.windows.project.FileTypeResolver
import com.pafoid.skate.editor.windows.project.FileSystemItem
import com.pafoid.skate.game.project.ProjectManager
import java.io.File

/**
 * Scans project directories and builds a FileSystemItem tree.
 *
 * Responsibilities:
 * - Recursive directory scanning
 * - File type resolution
 * - Favorite persistence (JSON file in project root)
 * - Change detection (manual refresh)
 */
class FileSystemScanner(
    private val projectManager: ProjectManager,
    private val logger: LoggerService,
    private val serializer: com.pafoid.skate.engine.assets.serialization.Serializer
) {
    private val favoritesFile: File?
        get() = projectManager.getProjectDirectory()?.let { File(it, ".favorites.json") }

    private var _favorites: MutableSet<String> = mutableSetOf()
    val favorites: Set<String> get() = _favorites

    init {
        loadFavorites()
    }

    /**
     * Scan the current project directory and return the root FileSystemItem tree.
     */
    fun scanProject(): List<FileSystemItem> {
        val projectDir = projectManager.getProjectDirectory()
            ?: return emptyList()

        val children = projectDir.listFiles()
            ?.filter { it.name !in SKIP_NAMES }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { buildTree(it) }
            ?: emptyList()

        val projectFileType = projectDir.listFiles { f -> f.extension == "skateproject" }
            ?.firstOrNull()
            ?.let { buildTree(it) }

        return buildList {
            projectFileType?.let { add(it) }
            addAll(children)
        }
    }

    private fun buildTree(file: File): FileSystemItem {
        val type = FileTypeResolver.resolve(file)
        val isFav = _favorites.contains(file.absolutePath)

        val children = if (file.isDirectory && shouldScanDirectory(file)) {
            file.listFiles()
                ?.filter { it.name !in SKIP_NAMES }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.map { buildTree(it) }
                ?: emptyList()
        } else {
            emptyList()
        }

        return FileSystemItem(file, type, isFav, children)
    }

    /**
     * Skip common non-project directories.
     */
    private fun shouldScanDirectory(dir: File): Boolean {
        return dir.name !in SKIP_NAMES
    }

    // ─── Favorites ───────────────────────────────────────────

    /**
     * Toggle favorite status for the given file path.
     */
    fun toggleFavorite(path: String) {
        if (_favorites.contains(path)) {
            _favorites.remove(path)
        } else {
            _favorites.add(path)
        }
        saveFavorites()
    }

    fun isFavorite(path: String): Boolean = path in _favorites

    private fun loadFavorites() {
        val file = favoritesFile ?: return
        if (!file.exists()) return
        try {
            val paths = serializer.decode<List<String>>(file.readText())
            _favorites = paths.toMutableSet()
        } catch (e: Exception) {
            logger.logEditor("Failed to load favorites: ${e.message}")
        }
    }

    private fun saveFavorites() {
        val file = favoritesFile ?: return
        try {
            file.writeText(serializer.encode(_favorites.toList()))
        } catch (e: Exception) {
            logger.logEditor("Failed to save favorites: ${e.message}")
        }
    }

    companion object {
        private val SKIP_NAMES = setOf(
            ".git", ".idea", "build", "gradle", ".kotlin", ".gradle",
            "out", ".vscode", ".settings", "target"
        )
    }
}
