package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.events.Event
import com.pafoid.skate.engine.events.SceneAction
import com.pafoid.skate.engine.ui.PauseMenuActions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** GUI-09: in editor Play the pause menu's Restart restarts Play and Quit stops it, like the viewport toolbar. */
class EditorPauseActionsTest {
    private val events = EventSystem()
    private val published = mutableListOf<Event>()
    private val actions = PauseMenuActions().also { EditorPauseActions.install(it, events) }

    init {
        events.subscribe(SceneAction.ResetScene::class) { published += it }
        events.subscribe(EngineAction.SetRuntimePlaying::class) { published += it }
        events.subscribe(ViewportAction.StopSimulation::class) { published += it }
    }

    @Test
    fun Restart_ResetsToThePrePlaySnapshotAndKeepsPlaying() {
        actions.restart()
        assertEquals(listOf(SceneAction.ResetScene, EngineAction.SetRuntimePlaying(true)), published)
    }

    @Test
    fun Quit_Stops() {
        actions.quit()
        assertEquals(listOf(EngineAction.SetRuntimePlaying(false), ViewportAction.StopSimulation), published)
    }
}
