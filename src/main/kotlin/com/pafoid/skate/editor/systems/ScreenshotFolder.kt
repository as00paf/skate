package com.pafoid.skate.editor.systems

import java.io.File

/**
 * Where editor screenshots go (EDT-42): `<project>/Screenshots` while a project is open, else `Screenshots` in the
 * editor's user-data folder (EDT-18). Never the working directory, which depends on how the editor was started.
 */
object ScreenshotFolder {
    const val NAME = "Screenshots"

    fun resolve(projectDir: File?, userDataDir: () -> File = { EditorPaths.dir }): File =
        File(projectDir ?: userDataDir(), NAME).absoluteFile
}
