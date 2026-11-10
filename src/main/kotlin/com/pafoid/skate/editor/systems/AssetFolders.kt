package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.animation.AnimatorControllerCatalog
import com.pafoid.skate.editor.data.FileType
import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.assets.ProjectLayout
import java.io.File

/**
 * Which type folder a new file lands in (AST-26 §3). Drop and import, store imports, Blender imports and the engine
 * default copier all use [folderFor]; null means the root of `Assets/` (or, for scenes, `Scenes/`).
 */
object AssetFolders {
    /** Model companions with no type of their own: they stay with their model. */
    private val MODEL_COMPANIONS = setOf("mtl", "bin")

    fun folderFor(file: File): AssetFolder? {
        val name = file.name.lowercase()
        if (file.name == ProjectLayout.STRINGS_FILE) return null
        if (AnimatorControllerCatalog.isController(name)) return AssetFolder.ANIMATIONS
        if (name.endsWith(ITEM_SUFFIX)) return AssetFolder.ITEMS
        if (file.extension.equals("blend", ignoreCase = true)) return AssetFolder.MODELS
        if (file.extension.lowercase() in MODEL_COMPANIONS) return AssetFolder.MODELS
        return when (FileTypeResolver.resolve(file)) {
            FileType.MODEL_3D -> AssetFolder.MODELS
            FileType.ANIMATION -> AssetFolder.ANIMATIONS
            FileType.TEXTURE -> AssetFolder.TEXTURES
            FileType.MATERIAL -> AssetFolder.MATERIALS
            FileType.SOUND -> AssetFolder.SOUNDS
            FileType.SCRIPT_KOTLIN, FileType.SCRIPT_JAVA -> AssetFolder.SCRIPTS
            FileType.SHADER -> AssetFolder.SHADERS
            FileType.FONT -> AssetFolder.FONTS
            FileType.PREFAB -> AssetFolder.PREFABS
            FileType.JSON, FileType.CONFIG, FileType.TEXT -> AssetFolder.DATA
            else -> null
        }
    }

    /** Where [file] should go in [projectDir]: its type folder, `Scenes/` for scenes, else the root of `Assets/`. */
    fun targetDir(projectDir: File, file: File): File = when {
        FileTypeResolver.resolve(file) == FileType.SCENE -> ProjectLayout.scenesDir(projectDir)
        else -> folderFor(file)?.let { ProjectLayout.typeDir(projectDir, it) } ?: ProjectLayout.assetsDir(projectDir)
    }

    /** GPL-24 item definitions. */
    const val ITEM_SUFFIX = ".item.json"
}
