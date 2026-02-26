package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.editor.windows.assetBrowser.AnimationsTab
import com.pafoid.skate.editor.windows.assetBrowser.PrefabsTab
import com.pafoid.skate.editor.windows.assetBrowser.TexturesTab
import com.pafoid.skate.engine.assets.ResourceManager
import imgui.ImGui
import imgui.type.ImBoolean
import imgui.type.ImString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AssetBrowserWindow : IWindow, KoinComponent {
    private val thumbnailCache: ThumbnailCache by inject()
    private val resourceManager: ResourceManager by inject()
    private val prefabsGenerator: PrefabsGenerator by inject()
    private val stringManager: StringManager by inject()

    private var searchText = ImString(256)

    private val animationsTab = AnimationsTab(resourceManager, thumbnailCache, stringManager)
    private val texturesTab = TexturesTab(resourceManager, thumbnailCache, stringManager)
    private val prefabsTab = PrefabsTab(resourceManager, thumbnailCache, stringManager, prefabsGenerator)

    init {
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
            if (ImGui.beginTabItem(stringManager.getString("lbl.prefabs"))) {
                prefabsTab.imgui("##prefabs", searchText)
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
        }

        ImGui.end()
    }
}