package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.events.SceneAction
import com.pafoid.skate.engine.ui.PauseMenuActions

/**
 * The runtime pause menu's Restart and Quit in editor Play (GUI-09), as the viewport toolbar does them:
 * Restart = reset the scene to its pre-play snapshot and keep playing (which also puts the scene camera back),
 * Quit = Stop.
 */
object EditorPauseActions {
    fun install(actions: PauseMenuActions, events: EventSystem) {
        actions.restart = {
            events.publish(SceneAction.ResetScene)
            events.publish(EngineAction.SetRuntimePlaying(true))
        }
        actions.quit = {
            events.publish(EngineAction.SetRuntimePlaying(false))
            events.publish(ViewportAction.StopSimulation)
        }
    }
}
