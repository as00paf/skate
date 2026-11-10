package com.pafoid.skate.editor.commands.editor

import com.pafoid.skate.editor.commands.ExecuteOnlyCommand
import com.pafoid.skate.engine.render.graph.RenderGraph

/**
 * Switches a render pass, or one stage of it ([stageId], e.g. a post effect), on or off from the Render Graph window
 * (EDT-54 §6). Execute-only: a debug view of the editor's renderer for this session, not scene or project data.
 */
class SetRenderPassEnabledCommand(
    private val graph: RenderGraph,
    private val passId: String,
    private val enabled: Boolean,
    private val stageId: String? = null,
) : ExecuteOnlyCommand {

    /** False when the pass or stage is unknown or required (nothing changed). */
    var applied = false
        private set

    override fun execute() {
        applied = if (stageId == null) graph.setEnabled(passId, enabled) else graph.setStageEnabled(passId, stageId, enabled)
    }

    override fun undo() {
        if (stageId == null) graph.setEnabled(passId, !enabled) else graph.setStageEnabled(passId, stageId, !enabled)
    }

    override fun getDisplayName(): String = "Set Render Pass Enabled"

    /** String key, translated by [com.pafoid.skate.editor.commands.CommandNames.targetName]. */
    override fun getTargetName(): String = "window.render_graph"
}
