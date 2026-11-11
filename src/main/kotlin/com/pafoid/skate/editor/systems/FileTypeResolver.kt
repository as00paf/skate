package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.editor.imgui.data.Icons
import org.lwjgl.assimp.Assimp.aiImportFile
import org.lwjgl.assimp.Assimp.aiReleaseImport
import org.lwjgl.assimp.Assimp.aiProcess_GenSmoothNormals
import org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices
import org.lwjgl.assimp.Assimp.aiProcess_Triangulate
import java.io.File

/**
 * Resolves a File to its corresponding FileType.
 */
object FileTypeResolver {
    fun resolve(file: File): FileType {
        if (file.isDirectory) return FileType.FOLDER
        val ext = file.extension.lowercase()
        // FBX files can contain models, animations, or both. Inspect content to decide.
        if (ext == "fbx") return FbxClassifier.classify(file)
        return when {
            ext in FileType.PROJECT_FILE.extensions -> FileType.PROJECT_FILE
            ext in FileType.SCENE.extensions -> FileType.SCENE
            ext in FileType.SCRIPT_KOTLIN.extensions -> FileType.SCRIPT_KOTLIN
            ext in FileType.SCRIPT_JAVA.extensions -> FileType.SCRIPT_JAVA
            ext in FileType.TEXTURE.extensions -> FileType.TEXTURE
            ext in FileType.MODEL_3D.extensions -> FileType.MODEL_3D
            ext in FileType.ANIMATION.extensions -> FileType.ANIMATION
            ext in FileType.SOUND.extensions -> FileType.SOUND
            ext in FileType.PREFAB.extensions -> FileType.PREFAB
            ext in FileType.JSON.extensions -> FileType.JSON
            ext in FileType.CONFIG.extensions -> FileType.CONFIG
            ext in FileType.SHADER.extensions -> FileType.SHADER
            ext in FileType.MATERIAL.extensions -> FileType.MATERIAL
            ext in FileType.TEXT.extensions -> FileType.TEXT
            ext in FileType.FONT.extensions -> FileType.FONT
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
        FileType.SCRIPT_KOTLIN, FileType.SCRIPT_JAVA -> Icons.FILE_CODE
        FileType.TEXTURE -> Icons.FILE_IMAGE
        FileType.MODEL_3D -> Icons.CUBE
        FileType.ANIMATION -> Icons.FILM
        FileType.SOUND -> Icons.MUSIC
        FileType.PREFAB -> Icons.CUBE
        FileType.JSON, FileType.CONFIG -> Icons.FILE_TEXT
        FileType.SHADER -> Icons.MAGIC
        FileType.MATERIAL -> Icons.PALETTE
        FileType.FONT -> Icons.FILE_FONT
        FileType.TEXT -> Icons.FILE_TEXT
        FileType.UNKNOWN -> Icons.FILE_UNKNOWN
    }
}

/**
 * Classifies an FBX file as [FileType.MODEL_3D] or [FileType.ANIMATION] by inspecting its content.
 * Uses Assimp to import the scene and check for meshes. Falls back to scanning the binary
 * for the "mNumMeshes" property string when Assimp cannot import.
 */
object FbxClassifier {

    /**
     * Classification result for an FBX file.
     */
    enum class Classification {
        /** File contains one or more meshes (with or without animation clips). */
        MODEL,
        /** File contains animation data only (no meshes). */
        ANIMATION,
    }

    /**
     * Classify [file] by inspecting its content.
     * A file with meshes is [MODEL]; animation-only is [ANIMATION]; both is [MODEL].
     */
    fun classify(file: File): FileType =
        when (classifyContent(file)) {
            Classification.MODEL -> FileType.MODEL_3D
            Classification.ANIMATION -> FileType.ANIMATION
        }

    /** Lightweight inspection: try Assimp import, then raw-byte scan. */
    fun classifyContent(file: File): Classification =
        try {
            val hasMeshes = tryImport(file) ?: scanBinary(file)
            if (hasMeshes) Classification.MODEL else Classification.ANIMATION
        } catch (_: Exception) {
            // File missing, unreadable, or any IO error: fall back to ANIMATION (the engine's bundled FBX
            // files in `assets/Animations/` are all animation-only).
            Classification.ANIMATION
        }

    /** Attempt a lightweight Assimp import to check [mNumMeshes]. Returns null on failure. */
    private fun tryImport(file: File): Boolean? {
        val scene = aiImportFile(
            file.path,
            aiProcess_GenSmoothNormals or aiProcess_JoinIdenticalVertices or aiProcess_Triangulate,
        ) ?: return null
        try {
            return scene.mNumMeshes() > 0
        } finally {
            aiReleaseImport(scene)
        }
    }

    /** Scan the raw binary for the "Geometry" property string as a fallback. */
    private fun scanBinary(file: File): Boolean {
        val marker = "Geometry".toByteArray()
        val bytes = file.readBytes()
        for (i in 0 until bytes.size - marker.size + 1) {
            var matched = true
            for (j in marker.indices) {
                if (bytes[i + j] != marker[j]) { matched = false; break }
            }
            if (matched) return true
        }
        return false
    }
}
