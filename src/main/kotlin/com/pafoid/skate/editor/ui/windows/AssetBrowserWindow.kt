package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.windows.assetBrowser.AnimationsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.SoundsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.TexturesTab
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImString

class AssetBrowserWindow(
    private val engine: Engine,
    private val stringManager: StringManager,
    private val prefabsGenerator: PrefabsGenerator,
    private val undoRedoManager: UndoRedoManager,
) : IWindow {
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

    override fun imgui(pOpen: ImBoolean?) {
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