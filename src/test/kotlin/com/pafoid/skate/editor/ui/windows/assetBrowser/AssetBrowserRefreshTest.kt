package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.engine.core.EventSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AssetBrowserRefreshTest {

    @Test
    fun FileSystemChanged_RescansTheTabs() {
        val events = EventSystem()
        var refreshes = 0
        AssetBrowserRefresh.subscribe(events) { refreshes++ }

        events.publish(FileSystemEvent.FileSystemChangedEvent("Assets/Store/polyhaven/table"))
        events.publish(FileSystemEvent.FileSystemChangedEvent(null))

        assertEquals(2, refreshes)
    }

    @Test
    fun OtherEvents_DoNotRescan() {
        val events = EventSystem()
        var refreshes = 0
        AssetBrowserRefresh.subscribe(events) { refreshes++ }

        events.publish(ProjectEvent.OpenProjectFileRequested)

        assertEquals(0, refreshes)
    }
}
