package com.pafoid.skate.engine.render.state

/**
 * 64-bit draw sort key: `[transparent:1][shader:16][material:24][mesh:22]`.
 * Opaque draws sort first, then by shader → material → mesh so consecutive draws share state.
 */
object SortKey {
    private const val SHADER_BITS = 16
    private const val MATERIAL_BITS = 24
    private const val MESH_BITS = 22

    private const val MESH_SHIFT = 0
    private const val MATERIAL_SHIFT = MESH_SHIFT + MESH_BITS
    private const val SHADER_SHIFT = MATERIAL_SHIFT + MATERIAL_BITS
    private const val TRANSPARENT_SHIFT = SHADER_SHIFT + SHADER_BITS

    private fun mask(bits: Int) = (1L shl bits) - 1

    fun build(transparent: Boolean, shaderId: Int, materialId: Int, meshId: Int): Long {
        val t = if (transparent) 1L else 0L
        return (t shl TRANSPARENT_SHIFT) or
            ((shaderId.toLong() and mask(SHADER_BITS)) shl SHADER_SHIFT) or
            ((materialId.toLong() and mask(MATERIAL_BITS)) shl MATERIAL_SHIFT) or
            ((meshId.toLong() and mask(MESH_BITS)) shl MESH_SHIFT)
    }

    fun isTransparent(key: Long): Boolean = (key ushr TRANSPARENT_SHIFT) == 1L
    fun shader(key: Long): Int = ((key ushr SHADER_SHIFT) and mask(SHADER_BITS)).toInt()
    fun material(key: Long): Int = ((key ushr MATERIAL_SHIFT) and mask(MATERIAL_BITS)).toInt()
    fun mesh(key: Long): Int = ((key ushr MESH_SHIFT) and mask(MESH_BITS)).toInt()
}
