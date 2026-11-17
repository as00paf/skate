package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.assets.AssetMeta
import com.pafoid.skate.engine.assets.EngineDefaultResolver
import com.pafoid.skate.engine.assets.EngineDefaults
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** What the Asset Browser says about an engine default (AST-26 §8). */
enum class EngineBadge(val labelKey: String) {
    /** Not copied into the project: the game uses the engine's file. */
    ENGINE("lbl.asset_browser.badge.engine"),
    /** A copy of an engine default (it overrides the engine's file). */
    ENGINE_COPY("lbl.asset_browser.badge.engine_copy"),
    /** The copy was edited since it was copied. */
    MODIFIED("lbl.asset_browser.badge.modified"),
    /** The engine's file changed since it was copied. */
    UPDATE_AVAILABLE("lbl.asset_browser.badge.update_available");

    val tooltipKey: String get() = "$labelKey.tooltip"
}

/**
 * One Asset Browser tile or row. [file] is what previews and loads read: the project file, or the engine's own file
 * for an engine default that was not copied ([isEngineOnly]). [path] is what the editor stores when the entry is
 * used (applied, dragged): the `engine:` reference of an engine-only entry, else the file path.
 */
data class BrowserEntry(
    val file: File,
    val folder: AssetFolder,
    val badges: List<EngineBadge> = emptyList(),
    /** The `engine:` reference of an engine default, copied or not; null for the project's own files. */
    val reference: String? = null,
    /** An image inside a multi-file model folder (AST-26 decision 8). */
    val modelTexture: Boolean = false,
) {
    val name: String get() = file.name
    val isEngineOnly: Boolean get() = EngineBadge.ENGINE in badges
    val path: String get() = if (isEngineOnly) reference!! else file.path
}

/** Labels and icons of the Asset Browser tabs, one per type folder ([AssetFolder]) in its order (AST-26 §8). */
object AssetBrowserTabs {
    fun labelKey(folder: AssetFolder): String = "tab.asset_browser.${folder.name.lowercase()}"

    fun icon(folder: AssetFolder): String = when (folder) {
        AssetFolder.MODELS -> Icons.CUBE
        AssetFolder.ANIMATIONS -> Icons.FILM
        AssetFolder.TEXTURES -> Icons.FILE_IMAGE
        AssetFolder.MATERIALS -> Icons.PALETTE
        AssetFolder.SOUNDS -> Icons.MUSIC
        AssetFolder.SCRIPTS -> Icons.FILE_CODE
        AssetFolder.SHADERS -> Icons.MAGIC
        AssetFolder.FONTS -> Icons.FILE_FONT
        AssetFolder.DATA, AssetFolder.ITEMS -> Icons.FILE_TEXT
        AssetFolder.PREFABS -> Icons.CUBE
    }

    /** Every type folder, in tab order. */
    val ORDER: List<AssetFolder> = AssetFolder.entries
}

/**
 * Builds every tab's entries in one scan (EDT-69, AST-26 §8): the files of the assets root by type, then, with a
 * project open, the engine defaults it did not copy as [EngineBadge.ENGINE] entries. Copies carry
 * [EngineBadge.ENGINE_COPY] and, from the hashes in their `.meta`, [EngineBadge.MODIFIED] and
 * [EngineBadge.UPDATE_AVAILABLE]. Runs on an IO worker; file hashes are cached by path, size and date.
 */
class AssetBrowserListing(
    private val engineRoot: File = File(System.getProperty("user.dir")),
    private val catalog: List<String> = EngineDefaults.CATALOG,
) {
    private data class Stamp(val length: Long, val modified: Long)
    private val hashes = ConcurrentHashMap<String, Pair<Stamp, String>>()

    fun build(projectRoot: File?): Map<AssetFolder, List<BrowserEntry>> {
        val root = AssetDiscovery.root(projectRoot, engineRoot)
        val resolver = EngineDefaultResolver(engineRoot) { projectRoot }
        val entries = AssetDiscovery.discover(root).mapValues { (folder, files) ->
            files.map { file ->
                val copy = if (projectRoot != null) copyBadges(file, resolver) else null
                BrowserEntry(
                    file, folder,
                    badges = copy?.second.orEmpty(),
                    reference = copy?.first,
                    modelTexture = folder == AssetFolder.TEXTURES && AssetDiscovery.isModelTexture(root, file),
                )
            }
        }.toMutableMap()
        if (projectRoot != null) {
            for ((folder, virtual) in engineOnly(resolver).groupBy { it.folder }) {
                entries[folder] = (entries[folder].orEmpty() + virtual).sortedWith(compareBy({ it.name.lowercase() }, { it.file.path }))
            }
        }
        return entries
    }

    /** The reference and badges of a project copy of an engine default, or null for the project's own files. */
    private fun copyBadges(file: File, resolver: EngineDefaultResolver): Pair<String, List<EngineBadge>>? {
        val meta = runCatching { AssetMeta.read(file) }.getOrNull() ?: return null
        val logical = meta.settings[EngineDefaults.META_SOURCE] ?: return null
        val copied = meta.settings[EngineDefaults.META_SHA256]
        val badges = mutableListOf(EngineBadge.ENGINE_COPY)
        if (copied != null) {
            if (hash(file) != copied) badges += EngineBadge.MODIFIED
            val engine = resolver.engineFile(logical)
            if (engine != null && hash(engine) != copied) badges += EngineBadge.UPDATE_AVAILABLE
        }
        return EngineDefaults.reference(logical) to badges
    }

    /** Catalog entries with no project copy, listed from the engine's own file. */
    private fun engineOnly(resolver: EngineDefaultResolver): List<BrowserEntry> = catalog.mapNotNull { reference ->
        val logical = EngineDefaults.logicalPath(reference) ?: return@mapNotNull null
        if (resolver.overrideFile(logical) != null) return@mapNotNull null
        val folder = folderOfLogical(logical) ?: return@mapNotNull null
        val file = resolver.engineFile(logical) ?: return@mapNotNull null
        BrowserEntry(file, folder, listOf(EngineBadge.ENGINE), reference)
    }

    private fun folderOfLogical(logical: String): AssetFolder? {
        val top = logical.substringBefore('/')
        return AssetFolder.entries.firstOrNull { it.dirName == top } ?: AssetDiscovery.folderOf(File(logical))
    }

    private fun hash(file: File): String {
        val stamp = Stamp(file.length(), file.lastModified())
        hashes[file.absolutePath]?.let { (s, h) -> if (s == stamp) return h }
        return EngineDefaultResolver.sha256(file).also { hashes[file.absolutePath] = stamp to it }
    }
}
