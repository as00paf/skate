package com.pafoid.skate.engine.render.rhi.opengl

/**
 * Tracks the bound VAO and the texture bound to each unit per target kind (2D, 2D array, cube), so redundant binds
 * are skipped. GL calls are injected to keep the logic testable without a context. Call [invalidate] whenever code
 * outside this cache may have changed bindings (ImGui, a new frame).
 *
 * @param glBindTexture Binds texture `id` to the active unit's target of `kind` (an index below [kinds])
 */
internal class BindingCache(
    private val glBindVao: (Int) -> Unit,
    private val glActiveTexture: (Int) -> Unit,
    private val glBindTexture: (kind: Int, id: Int) -> Unit,
    private val onStateChange: () -> Unit = {},
    textureUnits: Int = 16,
    kinds: Int = 3,
) {
    private var vao = UNKNOWN
    private var activeUnit = UNKNOWN
    private val textures = Array(kinds) { IntArray(textureUnits) { UNKNOWN } }

    /** Returns true when the VAO actually changed. */
    fun bindVao(id: Int): Boolean {
        if (vao == id) return false
        glBindVao(id)
        vao = id
        onStateChange()
        return true
    }

    /** Binds texture [id] (0: none) to [unit] as [kind]; returns true when a GL bind was issued. */
    fun bindTexture(unit: Int, id: Int, kind: Int = 0): Boolean {
        val bound = textures[kind]
        val tracked = unit in bound.indices
        if (tracked && bound[unit] == id) return false
        if (activeUnit != unit) {
            glActiveTexture(unit)
            activeUnit = unit
        }
        glBindTexture(kind, id)
        if (tracked) bound[unit] = id
        onStateChange()
        return true
    }

    /** Forget everything: the next bind of any kind is issued. */
    fun invalidate() {
        vao = UNKNOWN
        activeUnit = UNKNOWN
        textures.forEach { it.fill(UNKNOWN) }
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
