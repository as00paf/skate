package com.pafoid.skate.editor.imgui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import com.pafoid.skate.editor.imgui.DefaultDockLayout.Zone

class DefaultDockLayoutTest {
    @Test
    fun DockLayout_CoreWindows_AreInExpectedZones() {
        assertEquals(Zone.LEFT, DefaultDockLayout.zoneOf("window.hierarchy"))
        assertEquals(Zone.CENTER, DefaultDockLayout.zoneOf("window.game_viewport"))
        assertEquals(Zone.RIGHT, DefaultDockLayout.zoneOf("window.properties"))
        assertEquals(Zone.BOTTOM, DefaultDockLayout.zoneOf("window.asset_browser"))
        assertEquals(Zone.BOTTOM, DefaultDockLayout.zoneOf("window.console"))
    }

    @Test
    fun DockLayout_DialogsStayFloating() {
        assertNull(DefaultDockLayout.zoneOf("window.project_wizard"))
        assertNull(DefaultDockLayout.zoneOf("window.editor_settings"))
    }

    @Test
    fun DockLayout_DefaultWindows_AllHaveAZone() {
        val src = File("src/main/kotlin/com/pafoid/skate/editor/ui/windows")
        val defaults = src.walkTopDown().filter { it.extension == "kt" }
            .flatMap { Regex("EditorWindow\\(\"(window\\.[a-z_]+)\", true\\)").findAll(it.readText()).map { m -> m.groupValues[1] } }
            .toList()
        assertTrue(defaults.isNotEmpty())
        defaults.forEach { assertTrue(DefaultDockLayout.zoneOf(it) != null, "$it has no dock zone") }
    }

    @Test
    fun DockLayout_CategorizedToolWindows_AllHaveAZone() {
        // EDT-40: a tool window listed in View > Windows docks somewhere on first run and after Reset Layout
        // (the Shell panel used to float). Settings/key-binding windows are floating by design.
        val floating = setOf("window.editor_settings", "window.project_settings", "window.keybindings")
        val categorized = Regex(""""(window\.[a-z_]+)" to [A-Z]+""")
            .findAll(File("src/main/kotlin/com/pafoid/skate/editor/imgui/WindowCategory.kt").readText())
            .map { it.groupValues[1] }.toList()
        assertTrue(categorized.size > 10)
        (categorized - floating).forEach { assertTrue(DefaultDockLayout.zoneOf(it) != null, "$it has no dock zone") }
        assertEquals(Zone.BOTTOM, DefaultDockLayout.zoneOf("window.shell"))
    }

    @Test
    fun DockLayout_Ratios_LeaveRoomForCentre() {
        assertTrue(DefaultDockLayout.LEFT_RATIO + DefaultDockLayout.RIGHT_RATIO < 0.6f)
        assertTrue(DefaultDockLayout.BOTTOM_RATIO < 0.5f)
    }
}
