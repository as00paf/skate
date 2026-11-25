package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.terrain.TerrainTool
import com.pafoid.skate.editor.terrain.TerrainToolState
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** EDT-92: when the terrain tool takes the viewport from the usual gizmos. */
class TerrainBrushToolTest {
    private val undo = mockk<UndoRedoManager>(relaxed = true)
    private val state = TerrainToolState()
    private val tool = TerrainBrushTool(mockk<Engine>(relaxed = true), undo, mockk<DebugRenderer>(relaxed = true), state) { null }

    @Test
    fun Claims_OnlyTheInspectedTerrain_WhenItIsTheOnlySelection_AndAToolIsChosen() {
        val terrain = TerrainComponent()
        val go = GameObject("Ground").addComponent(Transform()).addComponent(terrain)
        val other = GameObject("Box").addComponent(Transform())
        state.terrain = terrain

        assertNull(tool.claims(listOf(go)), "no tool chosen")
        state.tool = TerrainTool.RAISE
        assertSame(terrain, tool.claims(listOf(go)))
        assertNull(tool.claims(listOf(go, other)), "multi-selection")
        assertNull(tool.claims(listOf(other)), "another object")
        assertNull(tool.claims(emptyList()))
        terrain.enabled = false
        assertNull(tool.claims(listOf(go)), "disabled terrain")
        terrain.enabled = true
        state.terrain = TerrainComponent()
        assertNull(tool.claims(listOf(go)), "the inspector shows another terrain")
    }

    @Test
    fun EndStroke_WithoutAStroke_RecordsNothing() {
        tool.endStroke()
        verify(exactly = 0) { undo.pushCommand(any()) }
    }
}
