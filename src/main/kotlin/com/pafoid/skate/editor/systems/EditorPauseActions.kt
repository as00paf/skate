package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.events.PlayModeAction
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ui.PauseMenuActions

/**
 * The runtime pause menu's Restart and Quit in editor Play (GUI-09), as the viewport toolbar does them:
 * Restart = Reset to the pre-play state and keep playing, Quit = Stop (EDT-45).
 */
object EditorPauseActions {
    fun install(actions: PauseMenuActions, events: EventSystem) {
        actions.restart = {
            events.publish(PlayModeAction.Reset)
            events.publish(PlayModeAction.Play)
        }
        actions.quit = { events.publish(PlayModeAction.Stop) }
    }
}
