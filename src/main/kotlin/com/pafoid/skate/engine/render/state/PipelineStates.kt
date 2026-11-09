package com.pafoid.skate.engine.render.state

import com.pafoid.skate.engine.render.rhi.BlendState
import com.pafoid.skate.engine.render.rhi.CompareFunc
import com.pafoid.skate.engine.render.rhi.CullMode
import com.pafoid.skate.engine.render.rhi.DepthState
import com.pafoid.skate.engine.render.rhi.PipelineState
import com.pafoid.skate.engine.render.rhi.PolygonOffset
import com.pafoid.skate.engine.render.rhi.Topology

/**
 * Fixed-function state of every kind of draw in the engine (REN-12 step 6). Each one spells out what the old `with*`
 * scopes and raw GL calls left in effect at that draw, so frames render identically.
 */
object PipelineStates {
    /** Opaque and alpha-masked mesh parts: depth read/write, back faces culled unless double sided. */
    fun mesh(blended: Boolean, doubleSided: Boolean): PipelineState = when {
        blended && doubleSided -> MESH_BLENDED_DOUBLE_SIDED
        blended -> MESH_BLENDED
        doubleSided -> MESH_OPAQUE_DOUBLE_SIDED
        else -> MESH_OPAQUE
    }

    val MESH_OPAQUE = PipelineState()
    val MESH_OPAQUE_DOUBLE_SIDED = PipelineState(cull = CullMode.NONE)

    /** Transparent parts test depth without writing it and blend with an opaque target alpha (REN-19). */
    val MESH_BLENDED = PipelineState(
        depth = DepthState(write = false),
        blend = BlendState.ALPHA_KEEP_TARGET_OPAQUE,
    )
    val MESH_BLENDED_DOUBLE_SIDED = MESH_BLENDED.copy(cull = CullMode.NONE)

    /** World sprites (2D batches): depth read/write, no blending, both faces. */
    val SPRITES = PipelineState(cull = CullMode.NONE)

    /** Sky dome: drawn at the far plane (LEQUAL) after the scene, without writing depth. */
    val SKY = PipelineState(
        depth = DepthState(write = false, func = CompareFunc.LESS_EQUAL),
        cull = CullMode.NONE,
    )

    /** Debug geometry: LEQUAL so lines on a surface win ties, depth writes, no blending, both faces. */
    val DEBUG_TRIANGLES = PipelineState(depth = DepthState(func = CompareFunc.LESS_EQUAL), cull = CullMode.NONE)
    val DEBUG_LINES = DEBUG_TRIANGLES.copy(topology = Topology.LINES, lineWidth = 4f)

    /**
     * Editor grid (REN-25): a full-screen quad writing its own fragment depth on the grid plane; depth tested (LEQUAL),
     * not written, so debug lines drawn after it are not hidden; straight alpha keeping the target opaque.
     */
    val GRID = PipelineState(
        depth = DepthState(write = false, func = CompareFunc.LESS_EQUAL),
        blend = BlendState.ALPHA_KEEP_TARGET_OPAQUE,
        cull = CullMode.NONE,
    )

    /** Particle billboards: depth tested, not written, straight alpha or additive, both faces. */
    val PARTICLES = PipelineState(
        topology = Topology.TRIANGLE_STRIP,
        depth = DepthState(write = false),
        blend = BlendState.ALPHA_KEEP_TARGET_OPAQUE,
        cull = CullMode.NONE,
    )
    val PARTICLES_ADDITIVE = PARTICLES.copy(blend = BlendState.ADDITIVE)

    /** Full-screen post-processing quads: no depth, no blending. */
    val FULLSCREEN = PipelineState(depth = DepthState.DISABLED, cull = CullMode.NONE)

    /** Runtime UI: over everything, straight alpha keeping the target opaque. */
    val UI = PipelineState(depth = DepthState.DISABLED, blend = BlendState.ALPHA_KEEP_TARGET_OPAQUE, cull = CullMode.NONE)

    /** Splash screen over the back buffer. */
    val SPLASH = PipelineState(depth = DepthState.DISABLED, blend = BlendState.ALPHA, cull = CullMode.NONE)

    /**
     * Shadow casters: depth only, both faces (open meshes still cast), slope-scaled offset against acne, and depth
     * clamping so casters between the light and the near plane are flattened onto it instead of clipped.
     */
    val SHADOW = PipelineState(
        depth = DepthState(clamp = true),
        cull = CullMode.NONE,
        polygonOffset = PolygonOffset(SHADOW_OFFSET_FACTOR, SHADOW_OFFSET_UNITS),
    )

    const val SHADOW_OFFSET_FACTOR = 1.5f
    const val SHADOW_OFFSET_UNITS = 2f
}
