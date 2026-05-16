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