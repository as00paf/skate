package com.pafoid.skate.engine.assets

import java.io.File

/** The type folders under a project's `Assets/` (AST-26 §2, GPL-24 `Items`): where new files of each type land. */
enum class AssetFolder(val dirName: String) {
    MODELS("Models"),
    ANIMATIONS("Animations"),
    TEXTURES("Textures"),
    MATERIALS("Materials"),
    SOUNDS("Sounds"),
    SCRIPTS("Scripts"),
    SHADERS("Shaders"),
    FONTS("Fonts"),
    DATA("Data"),
    PREFABS("Prefabs"),
    ITEMS("Items"),
}

/**
 * Folder layout of a project (AST-14, typed folders AST-26). Everything the game loads lives under the single
 * [ASSETS_DIR], in one folder per type ([AssetFolder]); copied engine defaults go straight into those folders (there
 * is no `EngineDefaults` folder).
 *
 * ```
 * <project>/
 *   <name>.skateproject
 *   Assets/
 *     Models/ Animations/ Textures/ Materials/ Sounds/ Scripts/ Shaders/ Fonts/ Data/ Prefabs/ Items/
 *     strings.properties
 *   Scenes/
 *   Builds/                 export output
 * ```
 */
object ProjectLayout {
    const val ASSETS_DIR = "Assets"
    const val SCENES_DIR = "Scenes"
    const val BUILDS_DIR = "Builds"
    const val STRINGS_FILE = "strings.properties"

    fun assetsDir(projectDir: File): File = File(projectDir, ASSETS_DIR)

    fun typeDir(projectDir: File, folder: AssetFolder): File = File(assetsDir(projectDir), folder.dirName)

    fun scenesDir(projectDir: File): File = File(projectDir, SCENES_DIR)

    fun buildsDir(projectDir: File): File = File(projectDir, BUILDS_DIR)

    /** `Assets/<relative>` as a project-relative reference with `/` separators. */
    fun assetPath(relative: String): String = "$ASSETS_DIR/" + relative.replace('\\', '/').trimStart('/')

    /** Creates the project's top-level folders and every type folder, even empty ones; returns the assets folder. */
    fun create(projectDir: File): File {
        listOf(assetsDir(projectDir), scenesDir(projectDir), buildsDir(projectDir)).forEach { it.mkdirs() }
        AssetFolder.entries.forEach { typeDir(projectDir, it).mkdirs() }
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
