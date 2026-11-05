package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.events.ViewportAction.CreateUi
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ui.UiKind
import imgui.ImGui

/** The "UI" create submenu shared by the viewport and hierarchy context menus. */
object UiCreateMenu {
    fun render(stringManager: StringManager, eventSystem: EventSystem, parent: GameObject?) {
        if (!ImGui.beginMenu(stringManager.label("context.create_ui"))) return
        UiKind.entries.forEach { kind ->
            val key = "context.create_ui.${kind.name.lowercase()}"
            if (stringManager.tipped(ImGui.menuItem(stringManager.getString(key)), key)) {
                eventSystem.publish(CreateUi(kind, parent))
            }
        }
        ImGui.endMenu()
    }
}
