package com.pafoid.skate.engine.utils

object RenderConsts {
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
    const val VERTEX_SIZE_BYTES = VERTEX_SIZE * Float.SIZE_BYTES
}