package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.core.StringManager

/**
 * Stable ImGui IDs for translated labels (EDT-38).
 *
 * ImGui identifies windows, tabs, tree nodes, collapsing headers, menus and popups by hashing their label. A label
 * taken straight from [StringManager] therefore becomes a different ID when the editor language changes: the window
 * leaves its dock slot and its `imgui.ini` entry, tabs and trees forget their state and open popups vanish. The text
 * after `###` replaces the label for hashing, so `"Console###window.console"` shows "Console" (or "Console" in
 * French) and always has the same ID. Use [StringManager.label] for any of those calls; plain widgets use `##id`.
 */
object ImGuiIds {
    const val STABLE = "###"

    /** [text] shown, [id] hashed. */
    fun stable(text: String, id: String): String = "$text$STABLE$id"

    /** The part of [label] ImGui hashes: from the last `###` on, else the whole label. */
    fun hashedPart(label: String): String {
        val i = label.lastIndexOf(STABLE)
        return if (i >= 0) label.substring(i) else label
    }
}

/** `"[prefix]<translation of key>###<key>"`: translated text with an ID that does not change with the language. */
fun StringManager.label(key: String, prefix: String = ""): String = ImGuiIds.stable(prefix + getString(key), key)
