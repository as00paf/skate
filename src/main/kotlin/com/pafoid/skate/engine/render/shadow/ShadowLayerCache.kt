package com.pafoid.skate.engine.render.shadow

import org.joml.Matrix4f

/**
 * Remembers what each shadow-map layer last contained so unchanged layers are not re-rendered (REN-22).
 *
 * A layer's signature hashes its light matrix and every caster drawn into it (identity, world matrix, skin pose
 * version, alpha-test inputs). With a still camera and static casters, as in an idle editor viewport, every layer
 * keeps its signature and the shadow pass draws nothing.
 */
class ShadowLayerCache(capacity: Int) {
    private val signatures = LongArray(capacity) { NONE }

    /** Forget every layer, e.g. after the shadow map storage was reallocated. */
    fun invalidateAll() = signatures.fill(NONE)

    /** True when [layer] must be rendered for [signature]; records the signature as the layer's content. */
    fun needsRender(layer: Int, signature: Long): Boolean {
        if (layer !in signatures.indices) return true
        if (signatures[layer] == signature) return false
        signatures[layer] = signature
        return true
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
    }
}

/** 64-bit FNV-1a style mixing for shadow layer signatures; allocation-free. */
object ShadowSignature {
    const val SEED = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
    private const val PRIME = 0x100000001b3L

    fun mix(h: Long, v: Long): Long = (h xor v) * PRIME
    fun mix(h: Long, v: Int): Long = mix(h, v.toLong())
    fun mix(h: Long, v: Float): Long = mix(h, java.lang.Float.floatToRawIntBits(v).toLong())
    fun mix(h: Long, v: Boolean): Long = mix(h, if (v) 1L else 0L)

    fun mix(h: Long, m: Matrix4f): Long {
        var r = h
        r = mix(r, m.m00()); r = mix(r, m.m01()); r = mix(r, m.m02()); r = mix(r, m.m03())
        r = mix(r, m.m10()); r = mix(r, m.m11()); r = mix(r, m.m12()); r = mix(r, m.m13())
        r = mix(r, m.m20()); r = mix(r, m.m21()); r = mix(r, m.m22()); r = mix(r, m.m23())
        r = mix(r, m.m30()); r = mix(r, m.m31()); r = mix(r, m.m32()); r = mix(r, m.m33())
        return r
    }
}
