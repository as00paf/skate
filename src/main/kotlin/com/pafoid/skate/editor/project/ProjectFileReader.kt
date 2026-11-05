package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Reads a `.skateproject` file (EDT-41). Saved projects store their paths relative to the project folder (AST-09),
 * so they are resolved against the folder [file] is in now, not against the open project or the working directory,
 * and [Project.projectPath] always comes from [file] itself: the file on disk is the source of truth, which keeps
 * a moved project working.
 */
object ProjectFileReader {
    fun read(serializer: Serializer, file: File): Project {
        val element = serializer.json.parseToJsonElement(file.readText())
        val upgraded = if (element is JsonObject) serializer.migrations.upgrade(FileKind.PROJECT, element) else element
        val root = file.absoluteFile.parentFile
        val absolute = AssetPaths.rewrite(upgraded) { AssetPaths.absolute(it, root) }
        val project = serializer.json.decodeFromJsonElement(Project.serializer(), absolute)
        project.projectPath = file.absolutePath
        return project
    }

    /** [read], or null when [file] is missing or cannot be decoded. */
    fun readOrNull(serializer: Serializer, file: File): Project? =
        if (file.isFile) runCatching { read(serializer, file) }.getOrNull() else null
}
