package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.imgui.data.Icons
import java.io.File

/**
 * Resolves a File to its corresponding FileType.
 */
object FileTypeResolver {
    fun resolve(file: File): FileType {
        if (file.isDirectory) return FileType.FOLDER
        return when (file.extension.lowercase()) {
            in FileType.PROJECT_FILE.extensions -> FileType.PROJECT_FILE
            in FileType.SCENE.extensions -> FileType.SCENE
            in FileType.SCRIPT_KOTLIN.extensions -> FileType.SCRIPT_KOTLIN
            in FileType.SCRIPT_JAVA.extensions -> FileType.SCRIPT_JAVA
            in FileType.TEXTURE.extensions -> FileType.TEXTURE
            in FileType.MODEL_3D.extensions -> FileType.MODEL_3D
            in FileType.SOUND.extensions -> FileType.SOUND
            in FileType.PREFAB.extensions -> FileType.PREFAB
            in FileType.JSON.extensions -> FileType.JSON
            in FileType.CONFIG.extensions -> FileType.CONFIG
            in FileType.SHADER.extensions -> FileType.SHADER
            in FileType.MATERIAL.extensions -> FileType.MATERIAL
            in FileType.TEXT.extensions -> FileType.TEXT
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