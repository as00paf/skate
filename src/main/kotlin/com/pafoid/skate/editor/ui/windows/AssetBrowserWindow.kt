package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.windows.assetBrowser.AnimationsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.SoundsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.TexturesTab
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
import imgui.type.ImString

class AssetBrowserWindow(
    engine: Engine,
    private val undoRedoManager: UndoRedoManager,
) : EditorWindow("window.asset_browser", true) {
    private val stringManager = engine.stringManager
    private val prefabsGenerator: PrefabsGenerator = engine.prefabsGenerator

    private val animationsTab: AnimationsTab = AnimationsTab(engine, stringManager)
    private val texturesTab: TexturesTab = TexturesTab(stringManager, engine, undoRedoManager)
    private val prefabsTab: PrefabsTab = PrefabsTab(engine, stringManager, prefabsGenerator)
    private val soundsTab: SoundsTab = SoundsTab(engine, stringManager)

    private var searchText = ImString(256)

    init {
        refreshAssets()
    }

    fun refreshAssets() {
        prefabsTab.refreshAssets()
        animationsTab.refreshAssets()
        texturesTab.refreshAssets()
    }

    override fun imgui() {
        ImGui.begin(stringManager.getString("window.asset_browser"))

        if (ImGui.beginTabBar("AssetBrowserTabs")) {
            if (ImGui.beginTabItem(stringManager.getString("lbl.animations"))) {
                animationsTab.imgui("##searchAnimations", searchText)
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem(stringManager.getString("lbl.textures"))) {
                texturesTab.imgui("##searchTextures", searchText)
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem(stringManager.getString("lbl.sounds"))) {
                soundsTab.imgui("##searchSounds", searchText)
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem(stringManager.getString("lbl.prefabs"))) {
                prefabsTab.imgui("##prefabs", searchText)
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
        }

        ImGui.end()
    }
}