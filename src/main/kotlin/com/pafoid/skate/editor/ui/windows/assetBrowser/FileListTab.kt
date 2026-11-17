package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.imgui.contextPopupId
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import java.awt.Desktop

/**
 * A type tab with no preview of its own (EDT-69): Models, Materials, Scripts, Shaders, Fonts, Data and Items. Each
 * file is a tile with its type icon, its path as a tooltip, and Show in Folder / Copy Path; dragging it carries its
 * path (`ASSET_PATH`). The search, empty states and empty-space menu are the shared ones of [AssetBrowserTab].
 */
class FileListTab(
    val type: AssetFolder,
    assetsManager: AssetsManager,
    stringManager: StringManager,
) : AssetBrowserTab(assetsManager, stringManager) {

    override fun renderFileItem(entry: BrowserEntry) {
        val file = entry.file
        val size = 80f
        ImGui.beginGroup()
        ImGui.pushID(file.absolutePath)
        ImGui.button(AssetBrowserTabs.icon(type), size, size)
        Tooltips.always(entry.path)

        if (ImGui.beginPopupContextItem(contextPopupId("asset_file"))) {
            if (stringManager.tipped(ImGui.menuItem("${Icons.FOLDER} ${stringManager.getString("context.asset_browser.show_in_folder")}"), "context.asset_browser.show_in_folder")) {
                runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file.parentFile) }
            }
            if (stringManager.tipped(ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.project.copy_path")}"), "context.project.copy_path")) {
                ImGui.setClipboardText(entry.path)
            }
            ImGui.endPopup()
        }
        ImGui.popID()

        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload(PAYLOAD, entry.path)
            ImGui.text("${AssetBrowserTabs.icon(type)} ${file.name}")
            ImGui.endDragDropSource()
        }

        ImGui.textWrapped(file.name)
        ImGui.dummy(0f, 5f)
        ImGui.endGroup()
    }

    companion object {
        /** Drag payload of a file tile: its path, or its `engine:` reference when not copied. */
        const val PAYLOAD = "ASSET_PATH"
    }
}
