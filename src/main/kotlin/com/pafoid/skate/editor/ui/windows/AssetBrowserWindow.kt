package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.assets.ProjectShaders
import com.pafoid.skate.editor.imgui.WindowHeader
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.tippedWith
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.windows.assetBrowser.AnimationsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.AssetBrowserListing
import com.pafoid.skate.editor.ui.windows.assetBrowser.AssetBrowserRefresh
import com.pafoid.skate.editor.ui.windows.assetBrowser.AssetBrowserScan
import com.pafoid.skate.editor.ui.windows.assetBrowser.AssetBrowserTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.AssetBrowserTabs
import com.pafoid.skate.editor.ui.windows.assetBrowser.FileListTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.SoundsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.TexturesTab
import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
import imgui.type.ImString

class AssetBrowserWindow(
    engine: Engine,
    private val undoRedoManager: UndoRedoManager,
) : EditorWindow("window.asset_browser", true) {
    private val stringManager = engine.stringManager

    /**
     * One tab per type folder (EDT-69, AST-26 §8), in [AssetBrowserTabs.ORDER]. Animations, Textures, Sounds and
     * Prefabs have their own previews; the other types list their files as tiles.
     */
    private val tabs: Map<AssetFolder, AssetBrowserTab> = AssetBrowserTabs.ORDER.associateWith { folder ->
        when (folder) {
            AssetFolder.ANIMATIONS -> AnimationsTab(engine, stringManager)
            AssetFolder.TEXTURES -> TexturesTab(stringManager, engine, undoRedoManager)
            AssetFolder.SOUNDS -> SoundsTab(engine, stringManager)
            AssetFolder.PREFABS -> PrefabsTab(engine, stringManager)
            else -> FileListTab(folder, engine.assetsManager, stringManager)
        }
    }

    private val listing = AssetBrowserListing()
    private val scan = AssetBrowserScan(
        runIO = { job -> engine.jobSystem.runIO { job() } },
        build = { listing.build(engine.assetsManager.paths.projectRoot) },
        publish = { found -> tabs.forEach { (folder, tab) -> tab.entries = found[folder].orEmpty() } },
    )

    private var searchText = ImString(256)

    init {
        val newShader = {
            engine.assetsManager.paths.projectRoot?.let { ProjectShaders.requestNewShader(it, engine.eventSystem) }
            Unit
        }
        tabs.values.forEach {
            it.onNewShader = newShader
            it.requestRefresh = scan::request
        }
        refreshAssets()
        AssetBrowserRefresh.subscribe(engine.eventSystem, ::refreshAssets)
    }

    fun refreshAssets() = scan.request()

    override val icon: String = Icons.FILE_IMAGE

    override val relatedWindows: List<String> = listOf("window.project", "window.properties")

    override fun imgui() {
        scan.update()
        if (beginWindow(stringManager)) {
            header(stringManager) { renderHeaderTools() }

            if (ImGui.beginTabBar("AssetBrowserTabs")) {
                tabs.forEach { (folder, tab) ->
                    val label = stringManager.label(AssetBrowserTabs.labelKey(folder), "${AssetBrowserTabs.icon(folder)} ")
                    if (ImGui.beginTabItem(label)) {
                        tab.imgui(folder.name.lowercase(), searchText)
                        ImGui.endTabItem()
                    }
                }
                ImGui.endTabBar()
            }
        }
        ImGui.end()
    }
    /** Header strip tools (EDT-51 §2.2): the search shared by every tab, then Refresh; `?` is drawn after them. */
    private fun renderHeaderTools() {
        val refresh = "${Icons.ARROW_ROTATE}##asset_browser_refresh"
        ImGui.setNextItemWidth((ImGui.getContentRegionAvailX() - WindowHeader.trailingWidth(refresh)).coerceAtLeast(80f))
        ImGui.inputTextWithHint("##asset_browser_search", "${Icons.SEARCH} ${stringManager.getString("lbl.search")}...", searchText)
        Tooltips.show(stringManager, "tooltip.search_assets")
        ImGui.sameLine()
        if (stringManager.tippedWith(ImGui.button(refresh), "tooltip.refresh_assets")) {
            refreshAssets()
        }
    }
}
