package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.WindowHeader
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.tippedWith
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.windows.assetBrowser.AnimationsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.AssetBrowserRefresh
import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.SoundsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.TexturesTab
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
import imgui.type.ImString

class AssetBrowserWindow(
    engine: Engine,
    private val undoRedoManager: UndoRedoManager,
) : EditorWindow("window.asset_browser", true) {
    private val stringManager = engine.stringManager

    private val animationsTab: AnimationsTab = AnimationsTab(engine, stringManager)
    private val texturesTab: TexturesTab = TexturesTab(stringManager, engine, undoRedoManager)
    private val prefabsTab: PrefabsTab = PrefabsTab(engine, stringManager)
    private val soundsTab: SoundsTab = SoundsTab(engine, stringManager)

    private var searchText = ImString(256)

    init {
        refreshAssets()
        AssetBrowserRefresh.subscribe(engine.eventSystem, ::refreshAssets)
    }

    fun refreshAssets() {
        prefabsTab.refreshAssets()
        animationsTab.refreshAssets()
        texturesTab.refreshAssets()
        soundsTab.refreshAssets()
    }

    override val icon: String = Icons.FILE_IMAGE

    override val relatedWindows: List<String> = listOf("window.project", "window.properties")

    override fun imgui() {
        if (beginWindow(stringManager)) {
            header(stringManager) { renderHeaderTools() }

            if (ImGui.beginTabBar("AssetBrowserTabs")) {
                if (ImGui.beginTabItem(stringManager.label("lbl.animations"))) {
                    animationsTab.imgui("animations", searchText)
                    ImGui.endTabItem()
                }
                if (ImGui.beginTabItem(stringManager.label("lbl.textures"))) {
                    texturesTab.imgui("textures", searchText)
                    ImGui.endTabItem()
                }
                if (ImGui.beginTabItem(stringManager.label("lbl.sounds"))) {
                    soundsTab.imgui("sounds", searchText)
                    ImGui.endTabItem()
                }
                if (ImGui.beginTabItem(stringManager.label("lbl.prefabs"))) {
                    prefabsTab.imgui("prefabs", searchText)
                    ImGui.endTabItem()
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
