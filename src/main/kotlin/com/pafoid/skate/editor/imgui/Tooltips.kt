package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.data.EditorShortcut
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiHoveredFlags

/**
 * Tooltips for buttons and menu items (EDT-05). A control's tooltip key is derived from its label key:
 * `<labelKey>.tooltip` (e.g. `menu.file.save` -> `menu.file.save.tooltip`), in the same per-area string file.
 *
 * Wrap the control call so the tooltip belongs to that item (the call runs first, so it is ImGui's last item):
 * ```
 * if (strings.tipped(ImGui.button(strings.label("btn.export.start")), "btn.export.start")) start()
 * if (strings.tippedWith(ImGui.button(Icons.PLAY), "tooltip.viewport_toolbar.play_simulation")) play()
 * ```
 * `TooltipGuardTest` fails on buttons and menu items with a translated or icon label and no tooltip.
 */
object Tooltips {
    const val SUFFIX = ".tooltip"

    /**
     * The Show tooltips setting (EDT-57), set by the editor. It gates the help tooltips of controls ([show], [text],
     * `tipped`); field explanations and data ([showIfPresent], [always]) always show.
     */
    var enabled: () -> Boolean = { true }

    /** Whether a tooltip of [kind] shows with the current setting. */
    fun shows(kind: Kind): Boolean = kind == Kind.ALWAYS || enabled()

    enum class Kind {
        /** What a button or menu item does: hidden when tooltips are off. */
        HELP,
        /** A field explanation or data (a full path, an error message): shown whatever the setting. */
        ALWAYS,
    }

    fun keyFor(labelKey: String): String = labelKey + SUFFIX

    /**
     * Property tooltips: shows [tooltipKey] for the last item only when the tables have it (fields of components
     * and UI elements; `StringTablesTest` requires one for every `component.<Type>.<field>` and `ui.inspector.*`).
     */
    fun showIfPresent(strings: StringManager, tooltipKey: String) {
        if (shows(Kind.ALWAYS) && ImGui.isItemHovered() && strings.hasString(tooltipKey)) ImGui.setTooltip(strings.getString(tooltipKey))
    }

    /**
     * Shows [tooltipKey]'s text while the last item is hovered, with [shortcut]'s live combo when given (EDT-10).
     * Disabled items keep their tooltip, so a grayed-out button still says what it does (EDT-45).
     */
    fun show(strings: StringManager, tooltipKey: String, shortcut: EditorShortcut? = null) {
        if (shows(Kind.HELP) && ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) ImGui.setTooltip(withShortcut(strings, strings.getString(tooltipKey), shortcut))
    }

    /** A help tooltip with ready-made [text] for the last item; hidden when tooltips are off. */
    fun text(text: String) {
        if (shows(Kind.HELP) && ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) ImGui.setTooltip(text)
    }

    /** A field explanation or data tooltip (a full path, an error) for the last item; shown even with tooltips off. */
    fun always(text: String) {
        if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) ImGui.setTooltip(text)
    }

    /**
     * [text] plus a "Shortcut: Ctrl+D" line. Shortcut text is never written into tooltip strings: it comes from
     * [EditorShortcuts][com.pafoid.skate.editor.data.EditorShortcuts], so it stays right after a rebinding.
     */
    fun withShortcut(strings: StringManager, text: String, shortcut: EditorShortcut?): String =
        if (shortcut == null) text else "$text\n${strings.getString("tooltip.shortcut", shortcut.display())}"
}

/** Returns [clicked] after attaching the tooltip derived from [labelKey] to the item just drawn. */
fun StringManager.tipped(clicked: Boolean, labelKey: String, shortcut: EditorShortcut? = null): Boolean {
    Tooltips.show(this, Tooltips.keyFor(labelKey), shortcut)
    return clicked
}

/** Like [tipped] with an explicit [tooltipKey] (icon-only buttons, labels built from several keys). */
fun StringManager.tippedWith(clicked: Boolean, tooltipKey: String, shortcut: EditorShortcut? = null): Boolean {
    Tooltips.show(this, tooltipKey, shortcut)
    return clicked
}
