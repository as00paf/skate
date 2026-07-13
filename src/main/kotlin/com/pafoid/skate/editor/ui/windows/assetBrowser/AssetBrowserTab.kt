package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiTableFlags
import imgui.type.ImString
import java.io.File
import kotlin.math.max

abstract class AssetBrowserTab(
    protected val assetsManager: AssetsManager,
    protected val stringManager: StringManager
) {

    companion object {
        const val ITEM_WIDTH = 120f
    }

    protected val items = mutableListOf<File>()
    protected val assetItems = mutableListOf<Any>()

    open fun imgui(label:String, searchText: ImString) {
        renderHeader(label, searchText)

        val availableWidth = ImGui.getContentRegionAvailX()
        val files = items.filter { it.name.contains(searchText.get(), ignoreCase = true) }

        val numColumns = max(1, (availableWidth / ITEM_WIDTH).toInt())

        if (ImGui.beginTable("$label Table", numColumns, ImGuiTableFlags.SizingFixedFit)) {
            for (file in files) {
                ImGui.tableNextColumn()
                renderFileItem(file)
            }
            ImGui.endTable()
        }
    }

    protected fun renderHeader(label: String, searchText: ImString) {
        val availableWidth = ImGui.getContentRegionAvailX()
        val refreshButtonWidth = 30f
        val spacing = ImGui.getStyle().itemSpacingX

        ImGui.pushItemWidth(availableWidth - refreshButtonWidth - spacing)
        ImGui.inputTextWithHint("##Search$label", "${Icons.SEARCH} ${stringManager.getString("lbl.search")}...", searchText)
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.search_assets"))
        }
        ImGui.popItemWidth()

        ImGui.sameLine()
        if (ImGui.button("${Icons.ARROW_ROTATE}##Refresh$label")) {
            refreshAssets()
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.refresh_assets"))
        }
        ImGui.separator()
    }

    open fun renderFileItem(file: File) {}
    open fun refreshAssets() {}

}
