package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.events.PlayModeAction
import com.pafoid.skate.editor.imgui.HamburgerMenuRenderer
import com.pafoid.skate.editor.imgui.MenuAction
import com.pafoid.skate.editor.systems.PlayMode
import com.pafoid.skate.editor.systems.PlayModeController
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager

/** Play: the viewport toolbar's Play / Pause, Stop and Reset (EDT-45 state machine), as menu items (EDT-51 §2.7). */
class PlayMenuBuilder(
    private val stringManager: StringManager,
    private val eventSystem: EventSystem,
    private val controller: PlayModeController,
) {
    fun render() = HamburgerMenuRenderer.menu(stringManager, HamburgerMenuModel.PLAY, ::action)

    private fun action(id: String): MenuAction? {
        val mode = controller.mode
        return when (id) {
            "play_pause" -> MenuAction(checked = mode == PlayMode.PLAYING) {
                eventSystem.publish(if (mode == PlayMode.PLAYING) PlayModeAction.Pause else PlayModeAction.Play)
            }
            "stop" -> MenuAction(mode != PlayMode.EDIT) { eventSystem.publish(PlayModeAction.Stop) }
            "reset" -> MenuAction(controller.canReset) { eventSystem.publish(PlayModeAction.Reset) }
            else -> null
        }
    }
}
