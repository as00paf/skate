package com.pafoid.skate.editor.commands.editor

import com.pafoid.skate.editor.commands.CommandCategory
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.RenderGraphAction
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.handlers.RenderGraphActionHandler
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.render.graph.RenderGraph
import com.pafoid.skate.engine.render.renderer.passes.BaseRenderPass
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-54 §6: pass toggles from the Render Graph window, as execute-only commands run by the handler. */
class SetRenderPassEnabledCommandTest {
    private class Pass(override val name: String, override val canDisable: Boolean = true) : BaseRenderPass()

    private val debug = Pass("DebugPass")
    private val shadow = Pass("ShadowPass")
    private val geometry = Pass("GeometryPass", canDisable = false)
    private val graph = RenderGraph { null }.apply { addPass(shadow); addPass(geometry); addPass(debug) }

    /** Runs commands right away, as UndoRedoManager does for execute-only commands. */
    private val undo = mockk<UndoRedoManager>().also { u ->
        every { u.executeCommand(any()) } answers { firstArg<com.pafoid.skate.editor.commands.Command>().execute() }
    }

    @Test
    fun SwitchesAPassOffAndBack_AndIsExecuteOnly() {
        val command = SetRenderPassEnabledCommand(graph, "DebugPass", false)
        assertEquals(CommandCategory.EXECUTE_ONLY, command.getCategory())
        command.execute()
        assertTrue(command.applied)
        assertFalse(debug.isEnabled)
        command.undo()
        assertTrue(debug.isEnabled)
        assertEquals("window.render_graph", command.getTargetName())
    }

    @Test
    fun RequiredPass_IsNotChanged() {
        val command = SetRenderPassEnabledCommand(graph, "GeometryPass", false)
        command.execute()
        assertFalse(command.applied)
        assertTrue(geometry.isEnabled)
    }

    @Test
    fun UnknownStage_IsNotApplied() {
        val command = SetRenderPassEnabledCommand(graph, "DebugPass", false, stageId = "FXAA")
        command.execute()
        assertFalse(command.applied)
        assertTrue(debug.isEnabled)
    }

    @Test
    fun Handler_RunsWindowRequests_AndEnablesEverythingWhenAProjectOpens() {
        val events = EventSystem()
        RenderGraphActionHandler(events, undo) { graph }

        events.publish(RenderGraphAction.SetPassEnabled("DebugPass", false))
        events.publish(RenderGraphAction.SetPassEnabled("ShadowPass", false))
        assertFalse(debug.isEnabled || shadow.isEnabled)

        events.publish(RenderGraphAction.EnableAll)
        assertTrue(debug.isEnabled && shadow.isEnabled)

        events.publish(RenderGraphAction.SetPassEnabled("DebugPass", false))
        events.publish(ProjectEvent.Opened(mockk<Project>(relaxed = true)))
        assertTrue(debug.isEnabled, "toggles are session-only: a project opens with every pass on")
    }
}
