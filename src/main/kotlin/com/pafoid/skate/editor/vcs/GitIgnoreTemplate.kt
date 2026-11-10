package com.pafoid.skate.editor.vcs

/**
 * EDT-60 §4 and §6: the `.gitignore` and `.gitattributes` text that Initialize writes. The Skate lines sit in a marked
 * block, so [mergeInto] can add them to a user's existing file exactly once and keep the user's own lines.
 * `.meta`, `.scene` and `.skateproject` files are never ignored: losing a `.meta` breaks every reference to its asset.
 */
object GitIgnoreTemplate {
    const val BEGIN = "# --- Skate (managed by the editor; edit outside this block) ---"
    const val END = "# --- end Skate ---"

    /** Folders that never go into Git, relative to the project root (export output, per-user state, caches). */
    val ignoredFolders = listOf("Builds", "builds", ".skate", "Library")

    val gitignoreBlock: String = listOf(
        BEGIN,
        "# Export output",
        "Builds/",
        "builds/",
        "# Per-user editor state for this project",
        ".skate/",
        "# Rebuildable caches",
        "Library/",
        "# Temporary and crash files",
        "*.tmp",
        "*.bak",
        "# OS files",
        "Thumbs.db",
        "desktop.ini",
        ".DS_Store",
        END,
    ).joinToString("\n", postfix = "\n")

    /** Binary asset types: never diffed or merged as text (and the later LFS candidates, §6). */
    val binaryTypes = listOf("png", "jpg", "jpeg", "tga", "hdr", "wav", "ogg", "mp3", "fbx", "glb", "gltf", "obj", "blend")

    val gitattributes: String = buildString {
        append(BEGIN).append('\n')
        append("* text=auto\n")
        listOf("scene", "prefab", "meta", "skateproject").forEach { append("*.").append(it).append(" text eol=lf\n") }
        binaryTypes.forEach { append("*.").append(it).append(" binary\n") }
        append(END).append('\n')
    }

    /** [existing] with the Skate [block] appended once; a file that already holds the block is returned unchanged. */
    fun mergeInto(existing: String?, block: String = gitignoreBlock): String {
        if (existing.isNullOrEmpty()) return block
        if (existing.lines().any { it.trim() == BEGIN }) return existing
        val separator = if (existing.endsWith("\n")) "\n" else "\n\n"
        return existing + separator + block
    }
}
