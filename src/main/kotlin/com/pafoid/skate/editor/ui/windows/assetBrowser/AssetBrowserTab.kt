package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants.ASSET_BROWSER_ITEM_WIDTH
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiTableFlags
import imgui.type.ImString
import java.awt.Desktop
import java.io.File
import kotlin.math.max

abstract class AssetBrowserTab(
    protected val assetsManager: AssetsManager,
    protected val stringManager: StringManager
) {
    /** Listed files; replaced whole by [refreshAssets] on an IO worker, so the UI never iterates a list being filled. */
    @Volatile
    protected var items: List<File> = emptyList()

    private var scannedProject: File? = null
    private var scanned = false

    /** The tab content; the search box and Refresh live in the window's header strip (EDT-51 §2.2). */
    open fun imgui(label: String, searchText: ImString) {
        refreshOnProjectChange()
        content(label) {
            val files = items.filter { it.name.contains(searchText.get(), ignoreCase = true) }
            if (renderEmptyState(items.size, files.size, searchText)) return@content
            val numColumns = max(1, (ImGui.getContentRegionAvailX() / ASSET_BROWSER_ITEM_WIDTH).toInt())
            if (ImGui.beginTable("##${label}_table", numColumns, ImGuiTableFlags.SizingFixedFit)) {
                for (file in files) {
                    ImGui.tableNextColumn()
                    renderFileItem(file)
                }
                ImGui.endTable()
            }
        }
    }

    /** Folder listed by this tab, or null when it is not a folder on disk (the prefab catalog). */
    protected open val folder: File? get() = AssetDiscovery.root(assetsManager.paths.projectRoot)

    /**
     * Draws [body] in a child that fills the tab, with the right-click menu on its empty space (EDT-51 §2.3):
     * Refresh, then Show folder and Copy folder path when the tab lists a folder.
     */
    protected fun content(label: String, body: () -> Unit) {
        if (ImGui.beginChild("##${label}_content", 0f, 0f)) {
            body()
            val dir = folder
            EmptySpaceMenu.render(stringManager, "##${label}_empty_space", EmptySpaceItem.assetTab(dir != null)) { item ->
                when (item) {
                    EmptySpaceItem.REFRESH -> refreshAssets()
                    EmptySpaceItem.SHOW_FOLDER -> dir?.let(::openFolder)
                    EmptySpaceItem.COPY_FOLDER_PATH -> dir?.let { ImGui.setClipboardText(it.absolutePath) }
                    else -> Unit
                }
            }
        }
        ImGui.endChild()
    }

    private fun openFolder(dir: File) {
        runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir) }
    }

    /** Rescans when a project is opened, created or closed after the tab was built (the tabs exist before it). */
    protected fun refreshOnProjectChange() {
        val root = assetsManager.paths.projectRoot
        if (scanned && root == scannedProject) return
        scanned = true
        scannedProject = root
        refreshAssets()
    }

    /** Lists the [type] files of the open project (or the engine assets) on an IO worker. */
    protected fun scan(type: BrowserAssetType, runIO: (() -> Unit) -> Unit) {
        val root = AssetDiscovery.root(assetsManager.paths.projectRoot)
        runIO { items = AssetDiscovery.discover(root, type) }
    }

    /**
     * Empty state (EDT-10) when the tab has no assets, or none matching the search; true when it was shown and the
     * tab has nothing else to draw.
     */
    protected fun renderEmptyState(total: Int, shown: Int, searchText: ImString): Boolean = when (BrowserEmptyState.of(total, shown)) {
        BrowserEmptyState.NONE -> false
        BrowserEmptyState.NOTHING_YET -> { EmptyState.nothingYet(stringManager, Icons.FOLDER, "empty.assets", "empty.assets.hint"); true }
        BrowserEmptyState.NO_RESULTS -> { EmptyState.noResults(stringManager) { searchText.set("") }; true }
    }

    open fun renderFileItem(file: File) {}
    open fun refreshAssets() {}

}
