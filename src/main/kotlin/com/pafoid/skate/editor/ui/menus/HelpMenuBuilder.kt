package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.HamburgerMenuRenderer
import com.pafoid.skate.editor.imgui.MenuAction
import com.pafoid.skate.editor.imgui.WindowRegistry
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager

/** Help: Getting started (the Game Viewport's help popover), Keyboard shortcuts, About Skate (EDT-51 §2.7). */
class HelpMenuBuilder(
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
    private val windowRegistry: WindowRegistry,
    private val aboutModal: AboutModal,
) {
    fun render() = HamburgerMenuRenderer.menu(stringManager, HamburgerMenuModel.HELP, ::action)

    private fun action(id: String): MenuAction? = when (id) {
        "getting_started" -> MenuAction {
            windowRegistry.getWindow(GAME_VIEWPORT)?.let { viewport ->
                viewport.isOpen.set(true)
                viewport.help.open()
            }
        }
        "shortcuts" -> MenuAction { eventSystem.publish(WindowAction.Show(KEY_BINDINGS)) }
        "about" -> MenuAction { aboutModal.open() }
        else -> null
    }

    companion object {
        const val GAME_VIEWPORT = "window.game_viewport"
        const val KEY_BINDINGS = "window.keybindings"
    }
}
