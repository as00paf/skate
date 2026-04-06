package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.systems.PrefabsGenerator
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.editor.windows.assetBrowser.AnimationsTab
import com.pafoid.skate.editor.windows.assetBrowser.PrefabsTab
import com.pafoid.skate.editor.windows.assetBrowser.SoundsTab
import com.pafoid.skate.editor.windows.assetBrowser.TexturesTab
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.database.AssetDatabase
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
    private val assetDatabase: AssetDatabase by inject()

    private var searchText = ImString(256)

    private val animationsTab by lazy { AnimationsTab(resourceManager, thumbnailCache, stringManager, assetDatabase) }
    private val texturesTab by lazy { TexturesTab(resourceManager, thumbnailCache, stringManager, assetDatabase) }
    private val prefabsTab by lazy { PrefabsTab(resourceManager, thumbnailCache, stringManager, prefabsGenerator) }
    private val soundsTab by lazy { SoundsTab(resourceManager, thumbnailCache, stringManager, assetDatabase) }

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