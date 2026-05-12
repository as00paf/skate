package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.ui.windows.assetBrowser.AnimationsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.PrefabsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.SoundsTab
import com.pafoid.skate.editor.ui.windows.assetBrowser.TexturesTab
import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImString
import org.koin.core.component.KoinComponent

class AssetBrowserWindow(
    private val stringManager: StringManager,
    private val animationsTab: AnimationsTab,
    private val texturesTab: TexturesTab,
    private val prefabsTab: PrefabsTab,
    private val soundsTab: SoundsTab,
) : IWindow, KoinComponent {

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