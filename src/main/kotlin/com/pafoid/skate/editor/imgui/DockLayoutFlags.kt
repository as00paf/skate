package com.pafoid.skate.editor.imgui

import imgui.flag.ImGuiDockNodeFlags as PublicFlags
import imgui.internal.ImGuiDockNode
import imgui.internal.flag.ImGuiDockNodeFlags

/**
 * Flags of the Game Viewport's (central) dock node (EDT-88 §1). Alone in its node the viewport shows no tab bar, as
 * before; once another window is docked there ImGui shows the tab bar, so windows can be tabbed in front of it.
 */
object DockLayoutFlags {
    /** Hides the tab bar while the node holds one window; no window-list button. */
    const val CENTRAL_ADD = PublicFlags.AutoHideTabBar or ImGuiDockNodeFlags.NoWindowMenuButton

    /** Layouts saved before EDT-88 gave the node NoTabBar, which also stops windows from being tabbed into it. */
    const val CENTRAL_REMOVE = ImGuiDockNodeFlags.NoTabBar

    /** The central node's local flags with [CENTRAL_ADD] set and [CENTRAL_REMOVE] cleared. */
    fun centralFlags(localFlags: Int): Int = (localFlags or CENTRAL_ADD) and CENTRAL_REMOVE.inv()

    /**
     * Applies [centralFlags] to [node]. ImGui does not save AutoHideTabBar in imgui.ini, so this runs every frame
     * after the dock space: it fixes saved layouts and layouts reloaded from a maximize snapshot alike.
     */
    fun applyCentral(node: ImGuiDockNode) {
        if (!node.isValidPtr) return
        val flags = centralFlags(node.localFlags)
        if (flags != node.localFlags) node.localFlags = flags
    }
}
