package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.engine.assets.ProjectLayout
import java.io.File

/** Asset kinds listed by the file-based Asset Browser tabs, matched by extension (any case). */
enum class BrowserAssetType(val extensions: Set<String>) {
    TEXTURE(FileType.TEXTURE.extensions.toSet()),
    ANIMATION(FileType.ANIMATION.extensions.toSet()),
    SOUND(FileType.SOUND.extensions.toSet());

    fun matches(file: File): Boolean {
        val ext = file.extension.lowercase()
        // `Board@Ollie.glb`: one action exported from a .blend (AST-19) is an animation, not a model
        if (this == ANIMATION && ext == "glb" && '@' in file.nameWithoutExtension) return true
        return ext in extensions
    }
}

/**
 * Finds the files the Asset Browser lists (AST-13). With a project open it scans the project's `Assets` folder (the
 * bundled defaults land in `Assets/EngineDefaults`); without one it scans the engine's `assets` folder. Files are
 * listed whether or not they are loaded yet: thumbnails and previews load them on demand.
 */
object AssetDiscovery {
    const val PROJECT_ASSETS_DIR = ProjectLayout.ASSETS_DIR
    const val ENGINE_ASSETS_DIR = "assets"

    fun root(projectRoot: File?, engineRoot: File = File(System.getProperty("user.dir"))): File =
        projectRoot?.let { File(it, PROJECT_ASSETS_DIR) } ?: File(engineRoot, ENGINE_ASSETS_DIR)

    /** Every [type] file under [root] (hidden folders skipped), sorted by name. */
    fun discover(root: File, type: BrowserAssetType): List<File> {
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .onEnter { it == root || !it.name.startsWith(".") }
            .filter { it.isFile && type.matches(it) }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.path }))
            .toList()
    }
}
