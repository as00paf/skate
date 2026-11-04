package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.events.ViewportAction.CreateUi
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ui.UiKind
import imgui.ImGui

/** The "UI" create submenu shared by the viewport and hierarchy context menus. */
object UiCreateMenu {
    fun render(stringManager: StringManager, eventSystem: EventSystem, parent: GameObject?) {
        if (!ImGui.beginMenu(stringManager.getString("context.create_ui"))) return
        UiKind.entries.forEach { kind ->
            if (ImGui.menuItem(stringManager.getString("context.create_ui.${kind.name.lowercase()}"))) {
                eventSystem.publish(CreateUi(kind, parent))
            }
        }
        ImGui.endMenu()
    }
}
