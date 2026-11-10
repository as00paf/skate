package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiPopupFlags

/** What a browser list (Project tree, Asset Browser tab) shows when it has nothing to list (EDT-51 §2.5). */
enum class BrowserEmptyState {
    /** Items to show. */
    NONE,
    /** The list itself is empty ("No assets here yet"). */
    NOTHING_YET,
    /** Items exist but the search hides them all ("No results", with Clear search). */
    NO_RESULTS;

    companion object {
        fun of(total: Int, shown: Int): BrowserEmptyState = when {
            shown > 0 -> NONE
            total == 0 -> NOTHING_YET
            else -> NO_RESULTS
        }
    }
}

/**
 * An item of the right-click menu on the empty space of a browser list (EDT-51 §2.3): create actions first, then
 * the folder actions, then copy. These menus have no destructive item.
 */
enum class EmptySpaceItem(val labelKey: String, val icon: String, val group: Group) {
    NEW_FOLDER("context.project.new_folder", Icons.FOLDER, Group.CREATE),
    NEW_FILE("context.project.new_file", Icons.PLUS, Group.CREATE),
    REFRESH("context.browser.refresh", Icons.ARROW_ROTATE, Group.MAIN),
    SHOW_FOLDER("context.browser.show_folder", Icons.EXTERNAL_LINK, Group.MAIN),
    COPY_FOLDER_PATH("context.browser.copy_folder_path", Icons.COPY, Group.COPY);

    enum class Group { CREATE, MAIN, COPY }

    companion object {
        /** Project window: create at the project root, refresh, open or copy the project folder. */
        val PROJECT = listOf(NEW_FOLDER, NEW_FILE, REFRESH, SHOW_FOLDER, COPY_FOLDER_PATH)

        /**
         * An Asset Browser tab. [hasFolder] is false for the prefab catalog: a fixed list, not a folder on disk, so it
         * has nothing to act on and gets no menu (EDT-51 §2.3).
         */
        fun assetTab(hasFolder: Boolean): List<EmptySpaceItem> =
            if (hasFolder) listOf(REFRESH, SHOW_FOLDER, COPY_FOLDER_PATH) else emptyList()

        /** A separator goes between two groups. */
        fun separatorBefore(items: List<EmptySpaceItem>, index: Int): Boolean =
            index > 0 && items[index].group != items[index - 1].group
    }
}

/** Draws the empty-space menu of the current window (or child); [onPick] runs for the chosen item. */
object EmptySpaceMenu {
    fun render(strings: StringManager, popupId: String, items: List<EmptySpaceItem>, onPick: (EmptySpaceItem) -> Unit) {
        if (items.isEmpty()) return
        if (!ImGui.beginPopupContextWindow(popupId, ImGuiPopupFlags.MouseButtonRight or ImGuiPopupFlags.NoOpenOverItems)) return
        items.forEachIndexed { i, item ->
            if (EmptySpaceItem.separatorBefore(items, i)) ImGui.separator()
            if (strings.tipped(ImGui.menuItem("${item.icon} ${strings.getString(item.labelKey)}"), item.labelKey)) onPick(item)
        }
        ImGui.endPopup()
    }
}
