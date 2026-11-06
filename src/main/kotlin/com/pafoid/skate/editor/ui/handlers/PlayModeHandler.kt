package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.events.PlayModeAction
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.systems.PlayModeController
import com.pafoid.skate.editor.systems.ScenePlaySnapshot
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.events.EngineAction

/**
 * Runs [PlayModeAction]s through the [PlayModeController] against the engine (EDT-45). Closing a project stops
 * Play and restores the scene first, so runtime state is never saved and the next project opens in Edit.
 */
class PlayModeHandler(
    private val engine: Engine,
    private val undoRedoManager: UndoRedoManager,
) {
    private val eventSystem = engine.eventSystem
    private val sceneManager = engine.sceneManager

    val controller = PlayModeController(EngineHost())

    fun init() {
        eventSystem.subscribe<PlayModeAction.Play> { controller.play() }
        eventSystem.subscribe<PlayModeAction.Pause> { controller.pause() }
        eventSystem.subscribe<PlayModeAction.TogglePause> { controller.togglePause() }
        eventSystem.subscribe<PlayModeAction.Stop> { controller.stop() }
        eventSystem.subscribe<PlayModeAction.TogglePlay> { controller.togglePlay() }
        eventSystem.subscribe<PlayModeAction.Reset> { controller.reset() }
        eventSystem.subscribe<ProjectEvent.Closing> { controller.stop() }
    }

    /** Quitting: the scenes are discarded, so there is nothing to restore. */
    fun shutdown() = controller.stop(restore = false)

    private inner class EngineHost : PlayModeController.Host {
        override val isPlaying get() = engine.runtimePlaying
        override val isPaused get() = engine.paused

        override fun setRuntimePlaying(playing: Boolean) =
            eventSystem.publish(EngineAction.SetRuntimePlaying(playing))

        override fun setRuntimePaused(paused: Boolean) = engine.setPaused(paused)

        override fun capture(): PlayModeController.Snapshot? {
            val scene = sceneManager.currentScene ?: return null
            val saved = ScenePlaySnapshot.capture(scene, engine.serializer)
            var live: Scene = scene
            return PlayModeController.Snapshot {
                // The scene was closed while playing: nothing to put back.
                if (live in sceneManager.openScenes) {
                    live = saved.restore(sceneManager, engine.serializer, live)
                    // Recorded commands reference the replaced objects.
                    undoRedoManager.clear()
                }
            }
        }
    }
}
