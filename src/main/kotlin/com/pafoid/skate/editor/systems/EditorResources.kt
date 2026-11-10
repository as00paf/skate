package com.pafoid.skate.editor.systems

import java.io.File
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

/**
 * Editor-only files (strings, themes, help) ship in the jar under `/editor/<name>/` (AST-26 NQ1, §0.3). Dev runs read
 * them live from `src/main/resources/editor/<name>`, so edits show without a rebuild; otherwise the classpath folder
 * is used directly (a classes directory) or copied once out of the jar to a temporary folder.
 */
object EditorResources {
    const val CLASSPATH_DIR = "/editor"
    const val DEV_DIR = "src/main/resources/editor"

    private val extracted = HashMap<String, File>()

    /** A folder holding the editor resources [name] (`strings`, `themes`, `help`), or null when there is none. */
    @Synchronized
    fun dir(name: String, devRoot: File = File(DEV_DIR)): File? {
        File(devRoot, name).takeIf { it.isDirectory }?.let { return it }
        extracted[name]?.let { return it }
        val url = EditorResources::class.java.getResource("$CLASSPATH_DIR/$name") ?: return null
        val uri = url.toURI()
        if (uri.scheme == "file") return File(uri).takeIf { it.isDirectory }
        if (uri.scheme != "jar") return null
        val target = Files.createTempDirectory("skate-editor-$name").toFile().also { it.deleteOnExit() }
        val fs = try {
            FileSystems.getFileSystem(uri)
        } catch (e: FileSystemNotFoundException) {
            FileSystems.newFileSystem(uri, emptyMap<String, Any>())
        }
        copyTree(fs.getPath("$CLASSPATH_DIR/$name"), target)
        extracted[name] = target
        return target
    }

    /** [dir] as a path string for loaders that take one; the dev path when nothing is found (they then report it). */
    fun path(name: String): String = (dir(name) ?: File(DEV_DIR, name)).path

    private fun copyTree(source: Path, target: File) {
        Files.walk(source).use { paths ->
            paths.forEach { p ->
                val rel = source.relativize(p).toString()
                val out = if (rel.isEmpty()) target else File(target, rel)
                if (Files.isDirectory(p)) out.mkdirs() else { out.parentFile.mkdirs(); Files.copy(p, out.toPath()); out.deleteOnExit() }
            }
        }
    }

}
