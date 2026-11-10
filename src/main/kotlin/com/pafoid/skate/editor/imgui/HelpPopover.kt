package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui

/**
 * A window's help (EDT-51 §2.4): a non-modal popover with "What it is", "How to use it" and "Related windows",
 * opened by the header's `?` or the tab menu's Help. Text comes from `help.<id>.what/how/related` (EDT-53); a
 * missing section shows a placeholder. Related windows are buttons that open them.
 */
class HelpPopover(private val window: EditorWindow) {
    private var requested = false

    fun open() {
        requested = true
    }

    /** Draws the popover; must run inside the window, after its `begin` (as `EditorWindow.beginWindow` does). */
    fun render(strings: StringManager) {
        if (requested) {
            requested = false
            ImGui.openPopup(POPUP_ID)
        }
        if (!ImGui.beginPopup(POPUP_ID)) return
        ImGui.pushTextWrapPos(ImGui.getFontSize() * 30f)
        ImGui.text(window.title(strings).substringBefore("###"))
        ImGui.separator()
        val sections = sections(window.helpId) { if (strings.hasString(it)) strings.getString(it) else null }
        sections.forEach { section ->
            ImGui.spacing()
            ImGui.textDisabled(strings.getString(section.titleKey))
            ImGui.textWrapped(section.text ?: strings.getString(PLACEHOLDER_KEY))
        }
        if (window.relatedWindows.isNotEmpty()) {
            if (sections.none { it.titleKey == "help.section.related" }) {
                ImGui.spacing()
                ImGui.textDisabled(strings.getString("help.section.related"))
            }
            window.relatedWindows.forEach { related ->
                val label = "${Icons.EXTERNAL_LINK} ${strings.getString(related)}##help_related_$related"
                if (strings.tippedWith(ImGui.smallButton(label), "help.related.open.tooltip")) {
                    openWindow(related)
                    ImGui.closeCurrentPopup()
                }
            }
        }
        ImGui.popTextWrapPos()
        ImGui.endPopup()
    }

    /** One help section: its heading key and its text, null when not written yet. */
    data class Section(val titleKey: String, val text: String?)

    companion object {
        private const val POPUP_ID = "##window_help_popover"
        const val PLACEHOLDER_KEY = "help.placeholder"

        /** Opens a window by name; set by [WindowRegistry]. */
        var openWindow: (String) -> Unit = {}

        /** `window.console` -> `console`. */
        fun helpIdOf(windowName: String): String = windowName.removePrefix("window.")

        /** The text keys of a window's help, in display order. */
        fun keysOf(helpId: String): List<String> = listOf("what", "how", "related").map { "help.$helpId.$it" }

        /** What and How always show; the Related text only when it exists. [text] returns null for a missing key. */
        fun sections(helpId: String, text: (String) -> String?): List<Section> {
            val (what, how, related) = keysOf(helpId)
            return listOfNotNull(
                Section("help.section.what", text(what)),
                Section("help.section.how", text(how)),
                text(related)?.let { Section("help.section.related", it) },
            )
        }
    }
}
