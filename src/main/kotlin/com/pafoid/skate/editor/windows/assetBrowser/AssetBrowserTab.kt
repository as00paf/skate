package com.pafoid.skate.editor.windows.assetBrowser

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.engine.assets.ResourceManager
import imgui.ImGui
import imgui.flag.ImGuiTableFlags
import imgui.type.ImString
import java.io.File

abstract class AssetBrowserTab(
    protected val resourceManager: ResourceManager,
    protected val thumbnailCache: ThumbnailCache,
    protected val stringManager: StringManager) {

    protected val items = mutableListOf<File>()

    init {
        refreshAssets()
    }

    open fun imgui(label:String, searchText: ImString) {
        ImGui.inputTextWithHint(label, "${Icons.SEARCH} ${stringManager.getString("lbl.search")}...", searchText)
        ImGui.sameLine()
        if (ImGui.button(Icons.ARROW_ROTATE)) {
            refreshAssets()
        }
        ImGui.separator()

        val files = items.filter { it.name.contains(searchText.get(), ignoreCase = true) }

        if (ImGui.beginTable("$label Table", 4, ImGuiTableFlags.SizingFixedFit)) {
            for (file in files) {
                ImGui.tableNextColumn()
                renderFileItem(file)
            }
            ImGui.endTable()
        }
    }

    open fun renderFileItem(file: File) {}
    open fun refreshAssets() {}

}
