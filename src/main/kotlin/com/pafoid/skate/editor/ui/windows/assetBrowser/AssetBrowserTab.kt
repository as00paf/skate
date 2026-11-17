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
    /**
     * Listed entries (EDT-69): set whole by the window after its one scan of the assets on an IO worker
     * ([AssetBrowserListing]), so the UI never iterates a list being filled.
     */
    @Volatile
    var entries: List<BrowserEntry> = emptyList()

    /** Set by the window: asks for one rescan of every tab (several requests in a frame make one scan). */
    var requestRefresh: () -> Unit = {}

    /** Entries shown before the search: every entry by default; the Textures tab hides model textures. */
    protected open fun visible(entries: List<BrowserEntry>): List<BrowserEntry> = entries

    private var scannedProject: File? = null
    private var scanned = false

    /** The tab content; the search box and Refresh live in the window's header strip (EDT-51 §2.2). */
    open fun imgui(label: String, searchText: ImString) {
        refreshOnProjectChange()
        pumpPreviews()
        content(label) {
            renderToolbar()
            val listed = visible(entries)
            val shown = listed.filter { it.name.contains(searchText.get(), ignoreCase = true) }
            if (renderEmptyState(listed.size, shown.size, searchText)) return@content
            val numColumns = max(1, (ImGui.getContentRegionAvailX() / ASSET_BROWSER_ITEM_WIDTH).toInt())
            if (ImGui.beginTable("##${label}_table", numColumns, ImGuiTableFlags.SizingFixedFit)) {
                for (entry in shown) {
                    ImGui.tableNextColumn()
                    renderFileItem(entry)
                    EngineBadges.render(stringManager, entry)
                }
                ImGui.endTable()
            }
        }
    }

    /** Set by the window: creates a project shader from the template (EDT-80); null hides New Shader. */
    var onNewShader: (() -> Unit)? = null

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
            EmptySpaceMenu.render(stringManager, "##${label}_empty_space", EmptySpaceItem.assetTab(dir != null, hasProject = onNewShader != null && assetsManager.paths.projectRoot != null)) { item ->
                when (item) {
                    EmptySpaceItem.NEW_SHADER -> onNewShader?.invoke()
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
        onProjectChanged()
        refreshAssets()
    }

    /** Uploads previews decoded since the last frame, a few per frame (AST-23). Main thread, once per frame. */
    protected open fun pumpPreviews() {}

    /** Drops cached previews of the previous project. */
    protected open fun onProjectChanged() {}

    /** Filters above the grid (the Textures tab's "Show model textures"). */
    protected open fun renderToolbar() {}

    /**
     * Empty state (EDT-10) when the tab has no assets, or none matching the search; true when it was shown and the
     * tab has nothing else to draw.
     */
    protected fun renderEmptyState(total: Int, shown: Int, searchText: ImString): Boolean = when (BrowserEmptyState.of(total, shown)) {
        BrowserEmptyState.NONE -> false
        BrowserEmptyState.NOTHING_YET -> { EmptyState.nothingYet(stringManager, Icons.FOLDER, "empty.assets", "empty.assets.hint"); true }
        BrowserEmptyState.NO_RESULTS -> { EmptyState.noResults(stringManager) { searchText.set("") }; true }
    }

    open fun renderFileItem(entry: BrowserEntry) {}

    /** Rescans: every tab, once ([requestRefresh]). */
    open fun refreshAssets() = requestRefresh()

}
