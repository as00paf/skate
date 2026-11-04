package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.renderer.passes.SelectionHighlight.HOVERED
import com.pafoid.skate.engine.render.renderer.passes.SelectionHighlight.NONE
import com.pafoid.skate.engine.render.renderer.passes.SelectionHighlight.SELECTED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SelectionHighlightTest {

    private val scene = Scene()
    private val floor = GameObject("Floor")
    private val board = GameObject("Skateboard")   // physics root, no mesh
    private val visual = GameObject("Visual")      // rendered child
    private val wheel = GameObject("Wheel")        // rendered grandchild

    init {
        scene.addChild(floor)
        scene.addChild(board)
        board.addChild(visual)
        visual.addChild(wheel)
    }

    private fun state(go: GameObject) = SelectionHighlight.stateOf(go, scene)

    @Test
    fun hoveredRootHighlightsWholeRenderedSubtree() {
        scene.hoveredGameObject = board
        assertEquals(HOVERED, state(visual))
        assertEquals(HOVERED, state(wheel))
        assertEquals(NONE, state(floor))
    }

    @Test
    fun selectedRootHighlightsWholeRenderedSubtree() {
        scene.selectedGameObject = board
        assertEquals(SELECTED, state(visual))
        assertEquals(SELECTED, state(wheel))
        assertEquals(NONE, state(floor))
    }

    @Test
    fun drilledDownSelectionHighlightsOnlyThatSubtree() {
        scene.selectedGameObject = wheel
        assertEquals(SELECTED, state(wheel))
        assertEquals(NONE, state(visual))
        assertEquals(NONE, state(board))
    }

    @Test
    fun drilledDownHoverHighlightsOnlyTheHit() {
        scene.hoveredGameObject = wheel
        assertEquals(HOVERED, state(wheel))
        assertEquals(NONE, state(visual))
    }

    @Test
    fun selectionWinsOverHoverInBothDirections() {
        scene.selectedGameObject = board
        scene.hoveredGameObject = wheel
        assertEquals(SELECTED, state(wheel))

        scene.selectedGameObject = wheel
        scene.hoveredGameObject = board
        assertEquals(SELECTED, state(wheel))
        assertEquals(HOVERED, state(visual))
    }

    @Test
    fun nothingSelectedOrHoveredIsNone() {
        assertEquals(NONE, state(wheel))
        assertEquals(NONE, state(floor))
    }

    @Test
    fun runningSceneIsNeverHighlighted() {
        scene.selectedGameObject = board
        scene.hoveredGameObject = floor
        scene.isRunning = true
        assertEquals(NONE, state(wheel))
        assertEquals(NONE, state(floor))
    }
}
