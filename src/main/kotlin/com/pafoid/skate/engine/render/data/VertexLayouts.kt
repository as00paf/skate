package com.pafoid.skate.engine.render.data

import com.pafoid.skate.engine.render.rhi.AttributeType
import com.pafoid.skate.engine.render.rhi.VertexAttribute
import com.pafoid.skate.engine.render.rhi.VertexBinding
import com.pafoid.skate.engine.render.rhi.VertexLayoutDesc
import com.pafoid.skate.engine.utils.RenderConsts2D
import com.pafoid.skate.engine.utils.RenderConsts3D

/**
 * Vertex layouts of the engine's dynamic geometry (REN-12 step 2). GL-free, so `VertexLayoutShaderTest` checks each
 * one against the `layout(location = N) in` declarations of the shaders it feeds.
 */
object VertexLayouts {

    private fun batch(posType: AttributeType, stride: Int, offsets: IntArray) = VertexLayoutDesc(listOf(
        VertexBinding(stride, listOf(
            VertexAttribute(RenderConsts2D.POS_LOCATION, posType, offsets[0]),
            VertexAttribute(RenderConsts2D.COLOR_LOCATION, AttributeType.VEC4, offsets[1]),
            VertexAttribute(RenderConsts2D.TEX_COORDS_LOCATION, AttributeType.VEC2, offsets[2]),
            VertexAttribute(RenderConsts2D.TEX_ID_LOCATION, AttributeType.FLOAT, offsets[3]),
            VertexAttribute(RenderConsts2D.ENTITY_ID_LOCATION, AttributeType.FLOAT, offsets[4]),
        )),
    ))

    /** Batched 2D quads: RenderBatch2D and the runtime UI (`shader_2d_batch`, `picking`). */
    val BATCH_2D = batch(
        AttributeType.VEC2, RenderConsts2D.VERTEX_SIZE_BYTES,
        intArrayOf(RenderConsts2D.POS_OFFSET, RenderConsts2D.COLOR_OFFSET, RenderConsts2D.TEX_COORDS_OFFSET,
            RenderConsts2D.TEX_ID_OFFSET, RenderConsts2D.ENTITY_ID_OFFSET),
    )

    /**
     * Batched 3D quads: RenderBatch3D (`shader_3d_texture`). The stride is [RenderConsts3D.VERTEX_SIZE_BYTES] as it
     * always was, even though a vertex holds 11 floats (known issue, kept so REN-12 renders identically).
     */
    val BATCH_3D = batch(
        AttributeType.VEC3, RenderConsts3D.VERTEX_SIZE_BYTES,
        intArrayOf(RenderConsts3D.POS_OFFSET, RenderConsts3D.COLOR_OFFSET, RenderConsts3D.TEX_COORDS_OFFSET,
            RenderConsts3D.TEX_ID_OFFSET, RenderConsts3D.ENTITY_ID_OFFSET),
    )

    /** Debug lines and triangles: position and colour (`debugLine2D`). */
    val DEBUG = VertexLayoutDesc(listOf(
        VertexBinding(6 * Float.SIZE_BYTES, listOf(
            VertexAttribute(0, AttributeType.VEC3, 0),
            VertexAttribute(1, AttributeType.VEC3, 3 * Float.SIZE_BYTES),
        )),
    ))

    /** Full-screen quads given as NDC xy at location 0 (`editor_grid`). */
    val FULLSCREEN_2D = VertexLayoutDesc(listOf(
        VertexBinding(2 * Float.SIZE_BYTES, listOf(VertexAttribute(0, AttributeType.VEC2, 0))),
    ))

    /** Floats per particle instance: position+size, colour, uv rect. */
    const val PARTICLE_FLOATS_PER_INSTANCE = 12

    /** Particle billboards: a static corner quad plus one instance record per particle (`particles`). */
    val PARTICLES = VertexLayoutDesc(listOf(
        VertexBinding(2 * Float.SIZE_BYTES, listOf(VertexAttribute(0, AttributeType.VEC2, 0))),
        VertexBinding(PARTICLE_FLOATS_PER_INSTANCE * Float.SIZE_BYTES, listOf(
            VertexAttribute(1, AttributeType.VEC4, 0),
            VertexAttribute(2, AttributeType.VEC4, 4 * Float.SIZE_BYTES),
            VertexAttribute(3, AttributeType.VEC4, 8 * Float.SIZE_BYTES),
        ), perInstance = true),
    ))

    /**
     * Mesh attributes uploaded by `VAOLoader`, one tightly packed buffer each (`shader_3d_default` and the shadow,
     * picking, sky and post shaders read subsets of them).
     */
    enum class MeshAttribute(val location: Int, val type: AttributeType) {
        POSITION(0, AttributeType.VEC3),
        UV(1, AttributeType.VEC2),
        NORMAL(2, AttributeType.VEC3),
        TANGENT(3, AttributeType.VEC3),
        COLOR(4, AttributeType.VEC4),
        UV1(5, AttributeType.VEC2),
        JOINTS(6, AttributeType.IVEC4),
        WEIGHTS(7, AttributeType.VEC4),
    }

    /** One binding per attribute, in [attributes] order. */
    fun mesh(attributes: List<Pair<Int, AttributeType>>) = VertexLayoutDesc(attributes.map { (location, type) ->
        VertexBinding(type.components * 4, listOf(VertexAttribute(location, type, 0)))
    })
}
