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
 * Runs [PlayModeAction]s through the [PlayModeController] against the engine (EDT-45).
 *
 * Actions are queued and run by [update] at the start of the next frame, outside ImGui drawing, so no window draws a
 * scene that was just replaced. Closing a project is the exception: it stops Play right away and restores the scenes,
 * so runtime state is never saved and the next project opens in Edit.
 */
class PlayModeHandler(
    private val engine: Engine,
    private val undoRedoManager: UndoRedoManager,
) {
    private val eventSystem = engine.eventSystem
    private val sceneManager = engine.sceneManager
    private val pending = ArrayDeque<PlayModeAction>()

    val controller = PlayModeController(EngineHost())

    fun init() {
        eventSystem.subscribe<PlayModeAction.Play> { pending.addLast(it) }
        eventSystem.subscribe<PlayModeAction.Pause> { pending.addLast(it) }
        eventSystem.subscribe<PlayModeAction.TogglePause> { pending.addLast(it) }
        eventSystem.subscribe<PlayModeAction.Stop> { pending.addLast(it) }
        eventSystem.subscribe<PlayModeAction.TogglePlay> { pending.addLast(it) }
        eventSystem.subscribe<PlayModeAction.Reset> { pending.addLast(it) }
        eventSystem.subscribe<ProjectEvent.Closing> {
            pending.clear()
            controller.stop()
        }
    }

    /** Runs the queued actions; called once per frame before the UI is drawn. */
    fun update() {
        while (pending.isNotEmpty()) run(pending.removeFirst())
    }

    private fun run(action: PlayModeAction) = when (action) {
        PlayModeAction.Play -> controller.play()
        PlayModeAction.Pause -> controller.pause()
        PlayModeAction.TogglePause -> controller.togglePause()
        PlayModeAction.Stop -> controller.stop()
        PlayModeAction.TogglePlay -> controller.togglePlay()
        PlayModeAction.Reset -> controller.reset()
    }

    /** Quitting: the scenes are discarded, so there is nothing to restore. */
    fun shutdown() {
        pending.clear()
        controller.stop(restore = false)
    }

    private inner class EngineHost : PlayModeController.Host {
        override val isPlaying get() = engine.runtimePlaying
        override val isPaused get() = engine.paused

        override fun setRuntimePlaying(playing: Boolean) {
            eventSystem.publish(EngineAction.SetRuntimePlaying(playing))
            // The engine only updates the current scene's flag; a scene left behind by a switch must stop too.
            if (!playing) sceneManager.openScenes.forEach { it.isRunning = false }
        }

        override fun setRuntimePaused(paused: Boolean) = engine.setPaused(paused)

        override fun capture(): PlayModeController.Snapshot? {
            val active = sceneManager.currentScene ?: return null
            val saved = sceneManager.openScenes.map { SavedScene(it, ScenePlaySnapshot.capture(it, engine.serializer)) }
            return PlayModeController.Snapshot { restore(saved, active) }
        }

        /**
         * Every scene open at Play may have run (a tab switch, a script's scene switch): each one still open goes back
         * to its saved state, then the scene that was active at Play becomes active again. Scenes closed during Play
         * are skipped; scenes opened during Play are left as they are.
         */
        private fun restore(saved: List<SavedScene>, activeAtPlay: Scene) {
            val activeEntry = saved.firstOrNull { it.live === activeAtPlay }
            // The scene that is current now first, so systems load once for it, then the others without a load.
            val current = sceneManager.currentScene
            saved.sortedByDescending { it.live === current }.forEach { entry ->
                if (entry.live in sceneManager.openScenes) {
                    entry.live = entry.snapshot.restore(sceneManager, engine.serializer, entry.live)
                }
            }
            activeEntry?.live?.takeIf { it in sceneManager.openScenes && it !== sceneManager.currentScene }
                ?.let { sceneManager.switchScene(it) }
            // Recorded commands reference the replaced objects.
            undoRedoManager.clear()
        }
    }

    /** One open scene's pre-Play state; [live] follows the scene through each restore. */
    private class SavedScene(var live: Scene, val snapshot: ScenePlaySnapshot)
}
