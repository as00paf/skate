package com.pafoid.skate.templates

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.Scene
import java.io.File

/**
 * Writes scene and project files the way the editor saves them (AST-09, TLG-14): versioned, with every asset
 * reference relative to the project folder, so the project can be moved or exported and still load.
 */
class ProjectFiles(private val serializer: Serializer, projectDir: File) {
    val projectDir: File = projectDir.absoluteFile.normalize()

    /** Runs [block] with the serializer's project root set to [projectDir], then restores the previous root. */
    fun <T> inProject(block: () -> T): T {
        val paths = serializer.assetPaths
        val previous = paths.projectRoot
        paths.projectRoot = projectDir
        try {
            return block()
        } finally {
            paths.projectRoot = previous
        }
    }

    /** Saves [scene] to `<scenesDir>/<name>.scene` and returns the file. */
    fun writeScene(scenesDir: File, name: String, scene: Scene): File =
        File(scenesDir.absoluteFile, "$name.scene").also { file ->
            inProject { file.writeText(serializer.encodeFile(FileKind.SCENE, scene)) }
        }

    /** Saves [project] to its `projectPath`. */
    fun writeProject(project: Project): File = File(project.projectPath).also { file ->
        inProject { file.writeText(serializer.encodeFile(FileKind.PROJECT, project)) }
    }
}
