package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.systems.AssetFolders
import com.pafoid.skate.editor.ui.windows.assetBrowser.AssetBrowserListing
import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.ecs.components.AssetRef
import java.io.File
import java.lang.reflect.Field

/** Pure logic of asset reference fields (EDT-108): which files fit a kind, whether a reference resolves, the picker list. */
object AssetRefs {
    /** Drag payloads the Asset Browser tabs and the Project window start; all carry a path or `engine:` reference. */
    val DROP_PAYLOADS = listOf("ASSET_PATH", "FILE_PATH", "TEXTURE", "SOUND")

    fun kindOf(field: Field): AssetFolder? = field.getAnnotation(AssetRef::class.java)?.kind

    /** True when [path] is a file of type [kind] (by extension, like the Asset Browser tabs). */
    fun accepts(kind: AssetFolder, path: String): Boolean {
        if (path.isBlank()) return false
        val name = if (EngineDefaults.isReference(path)) path.substringAfter(EngineDefaults.PREFIX) else path
        return AssetFolders.folderFor(File(name)) == kind
    }

    /** True when the file [path] names can be read: on disk (project-relative or absolute), an engine default, or packed. */
    fun exists(paths: AssetPaths, path: String): Boolean {
        if (path.isBlank()) return true
        if (EngineDefaults.isReference(path)) return paths.engineDefaults.resolve(path) != null || paths.pack?.find(path) != null
        return File(paths.absolute(path)).exists() || paths.pack?.find(path) != null
    }

    /** What to store for a dropped [payload] on a [kind] field, or null when it is not a file of that kind. */
    fun dropValue(kind: AssetFolder, payload: String?): String? = payload?.takeIf { accepts(kind, it) }

    /** Picker entries for [kind]: project files, then engine defaults not copied; paths as the Asset Browser drags them. */
    fun candidates(listing: AssetBrowserListing, projectRoot: File?, kind: AssetFolder): List<String> =
        listing.build(projectRoot)[kind].orEmpty().filter { !it.modelTexture }.map { it.path }

    /** Entries of [paths] containing [filter] (case-insensitive), by display name. */
    fun filter(paths: List<String>, filter: String): List<String> =
        if (filter.isBlank()) paths else paths.filter { it.contains(filter.trim(), ignoreCase = true) }
}
