package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants.ASSET_BROWSER_ITEM_WIDTH
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
    protected val items = mutableListOf<File>()

    open fun imgui(label:String, searchText: ImString) {
        renderHeader(label, searchText)

        val availableWidth = ImGui.getContentRegionAvailX()
        val files = items.filter { it.name.contains(searchText.get(), ignoreCase = true) }
        if (renderEmptyState(items.size, files.size, searchText)) return

        val numColumns = max(1, (availableWidth / ASSET_BROWSER_ITEM_WIDTH).toInt())

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

    /**
     * Empty state (EDT-10) when the tab has no assets, or none matching the search; true when it was shown and the
     * tab has nothing else to draw.
     */
    protected fun renderEmptyState(total: Int, shown: Int, searchText: ImString): Boolean {
        if (shown > 0) return false
        if (total == 0) EmptyState.render(stringManager, Icons.FOLDER, "empty.assets", "empty.assets.hint")
        else EmptyState.render(stringManager, Icons.SEARCH, "empty.search", "empty.search.hint")
        return true
    }

    open fun renderFileItem(file: File) {}
    open fun refreshAssets() {}

}
