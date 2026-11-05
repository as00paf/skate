package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event

/**
 * A failed user action to show in the error dialog (EDT-10), not only in the console.
 *
 * @param titleKey String key of the dialog title, e.g. `error.save_scene.title`
 * @param detail What went wrong (path, exception message); shown as is
 * @param messageKey String key of the explanation; defaults to `<titleKey without .title>.message`
 */
data class EditorError(val titleKey: String, val detail: String? = null, val messageKey: String = titleKey.removeSuffix(".title") + ".message") :
    Event("editor.error")
