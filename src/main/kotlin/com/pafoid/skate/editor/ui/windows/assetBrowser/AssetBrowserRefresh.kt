package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.engine.core.EventSystem

/**
 * Rescans the Asset Browser tabs when the project's files change (AST-22): an asset store import, a file created,
 * moved or deleted in the Project window. Without it, new files only appeared after the refresh button.
 */
object AssetBrowserRefresh {
    fun subscribe(eventSystem: EventSystem, refresh: () -> Unit) {
        eventSystem.subscribe<FileSystemEvent.FileSystemChangedEvent> { refresh() }
    }
}
