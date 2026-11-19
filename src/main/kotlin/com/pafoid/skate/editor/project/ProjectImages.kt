package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.ProjectLayout
import java.io.File

/**
 * Keeps images the project points at inside the project (EDT-91 review): an icon or splash picked outside the
 * project folder would be saved as an absolute path, so the project would not open the same on another machine.
 */
object ProjectImages {
    /**
     * [path] as a file inside [projectDir]: kept when it already is (or is an `engine:` reference or blank),
     * otherwise copied into `Assets/Textures/` (an identical file there is reused; another name gets `_2`, `_3`…).
     * Returns the path to store. The copy is not removed on undo: it is an ordinary project file from then on.
     */
    fun inProject(projectDir: File, path: String): String {
        if (path.isBlank() || EngineDefaults.isReference(path)) return path
        val source = File(path).let { if (it.isAbsolute) it else File(projectDir, path) }
        if (!source.isFile) return path
        val root = projectDir.canonicalFile
        if (source.canonicalFile.startsWith(root)) return source.absolutePath
        val dir = ProjectLayout.typeDir(projectDir, AssetFolder.TEXTURES).also { it.mkdirs() }
        val bytes = source.readBytes()
        var target = File(dir, source.name)
        var n = 2
        while (target.exists() && !target.readBytes().contentEquals(bytes)) {
            target = File(dir, "${source.nameWithoutExtension}_${n++}.${source.extension}")
        }
        if (!target.exists()) target.writeBytes(bytes)
        return target.absolutePath
    }
}
