package com.pafoid.skate.editor.windows.project

import com.pafoid.skate.editor.imgui.data.Icons
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

/**
 * Categorized file types for icon display and filtering.
 */
enum class FileType {
    FOLDER,
    PROJECT_FILE,
    SCENE,
    SCRIPT_KOTLIN,
    SCRIPT_JAVA,
    TEXTURE,
    MODEL_3D,
    ANIMATION,
    SOUND,
    PREFAB,
    JSON,
    CONFIG,
    SHADER,
    MATERIAL,
    TEXT,
    UNKNOWN
}

/**
 * Resolves a File to its corresponding FileType.
 */
object FileTypeResolver {
    fun resolve(file: File): FileType {
        if (file.isDirectory) return FileType.FOLDER
        return when (file.extension.lowercase()) {
            "skateproject" -> FileType.PROJECT_FILE
            "scene" -> FileType.SCENE
            "kt" -> FileType.SCRIPT_KOTLIN
            "java" -> FileType.SCRIPT_JAVA
            "png", "jpg", "jpeg", "tga", "bmp", "webp" -> FileType.TEXTURE
            "glb", "gltf", "fbx", "obj", "dae" -> FileType.MODEL_3D
            "wav", "ogg", "mp3" -> FileType.SOUND
            "prefab" -> FileType.PREFAB
            "json" -> FileType.JSON
            "properties", "yml", "yaml", "toml", "cfg", "ini" -> FileType.CONFIG
            "glsl", "vert", "frag" -> FileType.SHADER
            "material" -> FileType.MATERIAL
            "txt", "md" -> FileType.TEXT
            else -> FileType.UNKNOWN
        }
    }

    /**
     * Get the appropriate icon character for a file type.
     */
    fun getIcon(type: FileType): String = when (type) {
        FileType.FOLDER -> Icons.FOLDER
        FileType.PROJECT_FILE -> Icons.CUBE
        FileType.SCENE -> Icons.FILM
        FileType.SCRIPT_KOTLIN, FileType.SCRIPT_JAVA -> Icons.FILE_TEXT
        FileType.TEXTURE -> "\uf03e"
        FileType.MODEL_3D -> Icons.CUBE
        FileType.ANIMATION -> Icons.FILM
        FileType.SOUND -> Icons.MUSIC
        FileType.PREFAB -> Icons.CUBE
        FileType.JSON, FileType.CONFIG -> Icons.FILE_TEXT
        FileType.SHADER -> Icons.MAGIC
        FileType.MATERIAL -> Icons.PALETTE
        FileType.TEXT -> Icons.FILE_TEXT
        FileType.UNKNOWN -> Icons.FILE_TEXT
    }
}
