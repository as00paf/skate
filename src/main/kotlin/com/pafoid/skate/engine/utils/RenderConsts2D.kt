package com.pafoid.skate.engine.utils

object RenderConsts2D {
    /**
     * Vertex attribute locations of the batched quad layout, shared by RenderBatch2D, RenderBatch3D and
     * UiBatchRenderer. Every batch shader (`shader_2d_batch`, `picking`, `shader_3d_texture`) declares the same
     * `layout(location = N)`; `BatchShaderLayoutTest` guards that.
     */
    const val POS_LOCATION = 0
    const val COLOR_LOCATION = 1
    const val TEX_COORDS_LOCATION = 2
    const val TEX_ID_LOCATION = 3
    const val ENTITY_ID_LOCATION = 4

    const val POS_SIZE = 2
    const val COLOR_SIZE = 4
    const val TEX_COORDS_SIZE = 2
    const val TEX_ID_SIZE = 1
    const val ENTITY_ID_SIZE = 1

    const val POS_OFFSET = 0
    const val COLOR_OFFSET = POS_OFFSET + POS_SIZE * Float.SIZE_BYTES
    const val TEX_COORDS_OFFSET = COLOR_OFFSET + COLOR_SIZE * Float.SIZE_BYTES
    const val TEX_ID_OFFSET = TEX_COORDS_OFFSET + TEX_COORDS_SIZE * Float.SIZE_BYTES
    const val ENTITY_ID_OFFSET = TEX_ID_OFFSET + TEX_ID_SIZE * Float.SIZE_BYTES

    const val VERTEX_SIZE = 10

    /** Size of the `uTextures` sampler array in the batch shaders. */
    const val MAX_BATCH_TEXTURES = 8
    val SAMPLER_INDICES = IntArray(MAX_BATCH_TEXTURES) { it }
    const val VERTEX_SIZE_BYTES = VERTEX_SIZE * Float.SIZE_BYTES
}

object RenderConsts3D {
    const val POS_SIZE = 3
    const val COLOR_SIZE = 4
    const val TEX_COORDS_SIZE = 2
    const val TEX_ID_SIZE = 1
    const val ENTITY_ID_SIZE = 1

    const val POS_OFFSET = 0
    const val COLOR_OFFSET = POS_OFFSET + POS_SIZE * Float.SIZE_BYTES
    const val TEX_COORDS_OFFSET = COLOR_OFFSET + COLOR_SIZE * Float.SIZE_BYTES
    const val TEX_ID_OFFSET = TEX_COORDS_OFFSET + TEX_COORDS_SIZE * Float.SIZE_BYTES
    const val ENTITY_ID_OFFSET = TEX_ID_OFFSET + TEX_ID_SIZE * Float.SIZE_BYTES

    const val VERTEX_SIZE = 10
    const val VERTEX_SIZE_BYTES = VERTEX_SIZE * Float.SIZE_BYTES
}