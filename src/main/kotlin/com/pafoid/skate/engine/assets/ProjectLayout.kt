package com.pafoid.skate.engine.assets

import java.io.File

/**
 * Folder layout of a project (AST-14). Everything the game loads lives under the single [ASSETS_DIR]; the engine's
 * bundled defaults are copied into [ENGINE_DEFAULTS_DIR] inside it, never into a second assets tree.
 *
 * ```
 * <project>/
 *   <name>.skateproject
 *   Assets/                 user assets (Materials, Scripts, strings.properties, ...)
 *     EngineDefaults/       Characters, Models, Shaders, Sounds, Textures (bundled copies)
 *   Scenes/
 *   Builds/                 export output
 * ```
 */
object ProjectLayout {
    const val ASSETS_DIR = "Assets"
    const val ENGINE_DEFAULTS_DIR = "EngineDefaults"
    const val SCENES_DIR = "Scenes"
    const val BUILDS_DIR = "Builds"
    const val STRINGS_FILE = "strings.properties"

    fun assetsDir(projectDir: File): File = File(projectDir, ASSETS_DIR)

    fun engineDefaultsDir(projectDir: File): File = File(assetsDir(projectDir), ENGINE_DEFAULTS_DIR)

    fun scenesDir(projectDir: File): File = File(projectDir, SCENES_DIR)

    fun buildsDir(projectDir: File): File = File(projectDir, BUILDS_DIR)

    /** `Assets/<relative>` as a project-relative reference with `/` separators. */
    fun assetPath(relative: String): String = "$ASSETS_DIR/" + relative.replace('\\', '/').trimStart('/')

    /** Creates the project's top-level folders; returns the assets folder. */
    fun create(projectDir: File): File {
        listOf(assetsDir(projectDir), scenesDir(projectDir), buildsDir(projectDir)).forEach { it.mkdirs() }
        return assetsDir(projectDir)
    }

    /**
     * Folders under [projectDir] that look like a second assets root: any folder named `assets` (any case) other
     * than [ASSETS_DIR] itself, and any folder whose name repeats a sibling's in another case (`Shaders`/`shaders`).
     */
    fun duplicateAssetFolders(projectDir: File): List<File> {
        val root = assetsDir(projectDir).absoluteFile
        val dirs = projectDir.walkTopDown().onEnter { it == projectDir || !it.name.startsWith(".") }
            .filter { it.isDirectory && it != projectDir }.toList()
        val namedAssets = dirs.filter { it.name.equals(ASSETS_DIR, ignoreCase = true) && it.absoluteFile != root }
        val caseTwins = dirs.groupBy { (it.parentFile.absolutePath to it.name.lowercase()) }.values
            .filter { it.size > 1 }.flatten()
        return (namedAssets + caseTwins).distinct()
    }
}
