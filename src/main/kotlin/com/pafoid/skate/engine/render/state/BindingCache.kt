package com.pafoid.skate.engine.render.state

/**
 * Tracks the bound VAO and per-unit 2D textures so redundant binds are skipped.
 * GL calls are injected to keep the logic testable without a context. Call [invalidate] whenever
 * code outside this cache may have changed bindings (pass boundaries, raw GL use).
 */
class BindingCache(
    private val glBindVao: (Int) -> Unit,
    private val glActiveTexture: (Int) -> Unit,
    private val glBindTexture2D: (Int) -> Unit,
    private val onStateChange: () -> Unit = {},
    textureUnits: Int = 16,
) {
    private var vao = UNKNOWN
    private var activeUnit = UNKNOWN
    private val textures = IntArray(textureUnits) { UNKNOWN }

    /** Returns true when the VAO actually changed. */
    fun bindVao(id: Int): Boolean {
        if (vao == id) return false
        glBindVao(id)
        vao = id
        onStateChange()
        return true
    }

    /** Returns true when a GL texture bind was issued. */
    fun bindTexture(unit: Int, id: Int): Boolean {
        val tracked = unit in textures.indices
        if (tracked && textures[unit] == id) return false
        if (activeUnit != unit) {
            glActiveTexture(unit)
            activeUnit = unit
        }
        glBindTexture2D(id)
        if (tracked) textures[unit] = id
        onStateChange()
        return true
    }

    /** Forget everything: the next bind of any kind is issued. */
    fun invalidate() {
        vao = UNKNOWN
        activeUnit = UNKNOWN
        textures.fill(UNKNOWN)
    }

    /** Unbinds the VAO if this cache bound one, then invalidates. */
    fun reset() {
        if (vao > 0) glBindVao(0)
        invalidate()
    }

    private companion object {
        const val UNKNOWN = -1
    }
}
