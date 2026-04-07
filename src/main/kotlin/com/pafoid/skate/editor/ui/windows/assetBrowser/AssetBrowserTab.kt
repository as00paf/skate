package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.assets.database.AssetInfo
import com.pafoid.skate.engine.assets.database.AssetType
import imgui.ImGui
import imgui.flag.ImGuiTableFlags
import imgui.type.ImString
import java.io.File

abstract class AssetBrowserTab(
    protected val resourceManager: ResourceManager,
    protected val thumbnailCache: ThumbnailCache,
    protected val stringManager: StringManager,
    protected val assetDatabase: AssetDatabase? = null) {

    companion object {
        const val ITEM_WIDTH = 120f
    }

    protected val items = mutableListOf<File>()
    protected val assetItems = mutableListOf<AssetInfo>()

    init {
        refreshAssets()
    }

    open fun imgui(label:String, searchText: ImString) {
        renderHeader(label, searchText)

        val availableWidth = ImGui.getContentRegionAvailX()
        val files = items.filter { it.name.contains(searchText.get(), ignoreCase = true) }

        val numColumns = Math.max(1, (availableWidth / ITEM_WIDTH).toInt())

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

    /**
     * Refresh assets from the AssetDatabase for a given type.
     * Falls back to directory scanning if database is not available.
     */
    protected fun refreshFromDatabase(type: AssetType, fileExtensions: Set<String>) {
        items.clear()
        assetItems.clear()

        if (assetDatabase != null && assetDatabase.isInitialized) {
            val assets = assetDatabase.getAllByType(type)
            assetItems.addAll(assets)
            // Also populate files for backward compat
            val projectRoot = assetDatabase.projectRoot
            if (projectRoot != null) {
                items.addAll(assets.map { File(projectRoot, it.sourcePath) })
            }
        } else {
            // Fallback: scan directories
            refreshFromDirectory(fileExtensions)
        }
    }

    /**
     * Default directory scanning fallback.
     */
    protected open fun refreshFromDirectory(fileExtensions: Set<String>) {
        // Override in subclass
    }
}
