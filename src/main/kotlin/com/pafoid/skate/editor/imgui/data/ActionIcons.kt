package com.pafoid.skate.editor.imgui.data

/**
 * One icon per common action (EDT-10), so Delete, Duplicate or Save look the same in every menu, toolbar and
 * button. `ActionIconsGuardTest` fails when a control whose label key names one of these verbs (last key segment
 * `delete`, `save_as`, ...) uses another icon.
 */
object ActionIcons {
    val byVerb: Map<String, String> = mapOf(
        "delete" to Icons.TRASH,
        "remove" to Icons.TRASH,
        "clear" to Icons.TRASH,
        "rename" to Icons.EDIT,
        "duplicate" to Icons.CLONE,
        "copy" to Icons.COPY,
        "cut" to Icons.CUT,
        "paste" to Icons.PASTE,
        "save" to Icons.SAVE,
        "close" to Icons.WINDOW_CLOSE,
        "refresh" to Icons.ARROW_ROTATE,
        "reset" to Icons.ARROW_ROTATE,
        "undo" to Icons.UNDO,
        "redo" to Icons.REDO,
        "quit" to Icons.SIGN_OUT,
        "settings" to Icons.GEAR,
    )

    /** The verb a label key names: its last segment, or that segment's first word (`close_all` -> `close`). */
    fun verbOf(labelKey: String): String? {
        val last = labelKey.substringAfterLast('.')
        return byVerb.keys.firstOrNull { last == it || last.startsWith(it + "_") }
    }
}
