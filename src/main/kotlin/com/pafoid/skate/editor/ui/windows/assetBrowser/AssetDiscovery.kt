package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.systems.AssetFolders
import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.assets.ProjectLayout
import java.io.File

/**
 * Finds the files the Asset Browser lists (AST-13, EDT-69). With a project open it scans the project's `Assets`
 * folder; without one it scans the engine's `assets` folder. A tab lists **by type over the whole tree**, not by
 * folder (AST-26 §8): a moved file still shows in its tab, and `Board@Ollie.glb` shows under Animations. Files are
 * listed whether or not they are loaded yet: thumbnails and previews load them on demand.
 */
object AssetDiscovery {
    const val PROJECT_ASSETS_DIR = ProjectLayout.ASSETS_DIR
    const val ENGINE_ASSETS_DIR = "assets"

    /** Model companions (`.mtl`, `.bin`): they belong to their model and are not listed on their own. */
    private val COMPANIONS = setOf("mtl", "bin")

    fun root(projectRoot: File?, engineRoot: File = File(System.getProperty("user.dir"))): File =
        projectRoot?.let { File(it, PROJECT_ASSETS_DIR) } ?: File(engineRoot, ENGINE_ASSETS_DIR)

    /**
     * The tab [file] is listed in, or null when none (sidecars, the strings file, model companions, unknown files).
     * The type folder rules of [AssetFolders.folderFor], plus: one action exported from a `.blend` (`Board@Ollie.glb`,
     * AST-19) is an animation.
     */
    fun folderOf(file: File): AssetFolder? {
        val ext = file.extension.lowercase()
        if (ext in COMPANIONS) return null
        if (ext == "glb" && '@' in file.nameWithoutExtension) return AssetFolder.ANIMATIONS
        return AssetFolders.folderFor(file)
    }

    /** Every listed file under [root] (hidden folders skipped), by tab, each tab sorted by name. */
    fun discover(root: File, folderOf: (File) -> AssetFolder? = ::folderOf): Map<AssetFolder, List<File>> {
        if (!root.isDirectory) return emptyMap()
        return root.walkTopDown()
            .onEnter { it == root || !it.name.startsWith(".") }
            .filter { it.isFile }
            .mapNotNull { file -> folderOf(file)?.let { it to file } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, files) -> files.sortedWith(byName) }
    }

    /** The [folder] files under [root]. */
    fun discover(root: File, folder: AssetFolder): List<File> = discover(root)[folder].orEmpty()

    /**
     * An image inside a multi-file model folder (`Models/<asset>/textures/wood.png`, AST-26 decision 8): hidden from
     * the Textures tab unless "Show model textures" is on.
     */
    fun isModelTexture(root: File, file: File): Boolean {
        val parts = runCatching { file.relativeTo(root).invariantSeparatorsPath }.getOrNull()?.split('/') ?: return false
        return parts.size >= 3 && parts.first() == AssetFolder.MODELS.dirName
    }

    val byName: Comparator<File> = compareBy({ it.name.lowercase() }, { it.path })
}
