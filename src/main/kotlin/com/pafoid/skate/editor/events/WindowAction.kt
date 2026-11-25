package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event

sealed class WindowAction(eventName: String) : Event(eventName) {
    data class Show(val name: String) : WindowAction("window.show")
    data class Hide(val name: String) : WindowAction("window.hide")
    object ShowDefault : WindowAction("window.showDefault")
    object ResetLayout : WindowAction("window.resetLayout")
    object HideAll : WindowAction("window.hideAll")

    /** EDT-88: the maximize shortcut; maximizes or restores the focused tool window. */
    object ToggleMaximizeFocused : WindowAction("window.toggleMaximizeFocused")

    /** EDT-101: the Help shortcut (F1); opens the focused window's article. */
    object HelpForFocused : WindowAction("window.helpForFocused")

    /** EDT-88: puts the layout back as it was before maximizing (Search Everywhere). */
    object RestoreMaximized : WindowAction("window.restoreMaximized")

    /** EDT-84: a tool window's side-bar button or shortcut: open it, bring it to the front, or close it. */
    data class ToggleToolWindow(val name: String) : WindowAction("window.toggleToolWindow")
}