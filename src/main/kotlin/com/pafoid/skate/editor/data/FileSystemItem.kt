package com.pafoid.skate.editor.data

import java.io.File

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
}