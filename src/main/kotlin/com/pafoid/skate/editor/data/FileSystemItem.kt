package com.pafoid.skate.editor.data

import java.io.File

/**
 * Represents a file or folder in the project file system tree.
 *
 * @property file The underlying Java File object
 * @property type The resolved file type for icon display
 * @property isFavorite Whether this item is marked as a favorite
 * @property children Child items (only populated for directories)
 * @property assetGuid GUID from .meta file (models/animations only)
 * @property computedSize Pre-computed file/folder size (cached during buildTree)
 */
data class FileSystemItem(
    val file: File,
    val type: FileType,
    val isFavorite: Boolean = false,
    val children: List<FileSystemItem> = emptyList(),
    val assetGuid: String? = null,
    val computedSize: Long = if (file.isFile) file.length() else 0L
) {
    val name: String get() = file.name
    val path: String get() = file.absolutePath
    val exists: Boolean get() = file.exists()
    val size: Long get() = computedSize
    val extension: String get() = file.extension

    /**
     * Get the path relative to the given root directory.
     */
    fun relativeTo(root: File): String {
        return file.absolutePath.removePrefix(root.absolutePath + File.separator)
    }
}