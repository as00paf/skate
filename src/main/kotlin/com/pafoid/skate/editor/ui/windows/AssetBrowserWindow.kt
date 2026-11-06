package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.EditorWindow
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

    override fun imgui() {
        ImGui.begin(stringManager.label("window.asset_browser"))

        if (ImGui.beginTabBar("AssetBrowserTabs")) {
            if (ImGui.beginTabItem(stringManager.label("lbl.animations"))) {
                animationsTab.imgui("##searchAnimations", searchText)
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem(stringManager.label("lbl.textures"))) {
                texturesTab.imgui("##searchTextures", searchText)
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem(stringManager.label("lbl.sounds"))) {
                soundsTab.imgui("##searchSounds", searchText)
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem(stringManager.label("lbl.prefabs"))) {
                prefabsTab.imgui("##prefabs", searchText)
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
        }

        ImGui.end()
    }
}