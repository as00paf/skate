package com.pafoid.skate.engine.render.renderer.passes

/**
 * A pass made of toggleable sub-steps (stages), e.g. the post effects of [PostProcessPass] (EDT-54). Stages are
 * indexed 0 until [stageCount] in run order; the list is fixed for the pass's lifetime, only enabled flags change.
 * Accessors must not allocate: [com.pafoid.skate.engine.render.graph.RenderGraph.info] polls them every frame.
 */
interface StagedRenderPass {
    val stageCount: Int
    fun stageId(index: Int): String
    fun stageLabelKey(index: Int): String
    fun isStageEnabled(index: Int): Boolean

    /** Sets stage [index] on or off (not a toggle); returns false when it cannot be changed. */
    fun setStageEnabled(index: Int, enabled: Boolean): Boolean

    /** False when no stage can run in this renderer, whatever the flags say. */
    val stagesActive: Boolean get() = true
}
