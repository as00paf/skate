package com.pafoid.skate.engine.render.material

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.Material

/**
 * Uploads a [Material] through its [MaterialSchema]. Per bind it fills a reusable snapshot (no allocation)
 * and skips parameter/sampler uploads when the same sink (shader) already holds an identical snapshot,
 * which is the common case for consecutive draws after sorting. Textures always go through [bindTexture]
 * (the backend's binding cache dedupes those).
 *
 * Assumes only this binder writes material uniforms on a given shader.
 */
class MaterialBinder(
    private val schema: MaterialSchema = MaterialSchema.PBR,
    private val bindTexture: (slot: Int, texture: Texture) -> Unit,
) {
    private var current = Snapshot(schema)
    private var last = Snapshot(schema)
    private var lastSink: UniformSink? = null

    var uploadsSkipped = 0L
        private set
    var uploadsIssued = 0L
        private set

    private class Snapshot(schema: MaterialSchema) {
        val values = FloatArray(schema.floatCount)
        val textureIds = IntArray(schema.textures.size)

        fun sameAs(o: Snapshot) = values.contentEquals(o.values) && textureIds.contentEquals(o.textureIds)
    }

    /**
     * Fills [target] from [material]; missing textures get id 0. A texture is present when it has a GPU texture: its
     * native handle is not a usable flag (a Vulkan image handle truncated to an Int can be negative, REN-13). Pure.
     */
    private fun fill(material: Material, target: Snapshot) {
        var offset = 0
        for (p in schema.params) {
            material.readParam(p.name, target.values, offset)
            offset += p.type.components
        }
        for ((i, t) in schema.textures.withIndex()) {
            target.textureIds[i] = material.textureFor(t.name)?.let(::textureKey) ?: 0
        }
    }

    /** Non-zero identity of an uploaded texture, 0 when it has none. */
    private fun textureKey(texture: Texture): Int {
        val rhi = texture.rhi
        val key = if (rhi != null) System.identityHashCode(rhi) else texture.texId.takeIf { it > 0 } ?: return 0
        return if (key == 0) 1 else key
    }

    fun bind(material: Material, sink: UniformSink) {
        fill(material, current)
        // Texture units are shared across materials, so always (re)bind; the binding cache skips repeats.
        for ((i, t) in schema.textures.withIndex()) {
            if (current.textureIds[i] != 0) material.textureFor(t.name)?.let { bindTexture(t.slot, it) }
        }
        if (lastSink === sink && current.sameAs(last)) {
            uploadsSkipped++
            return
        }
        upload(sink)
        uploadsIssued++
        val swap = last
        last = current
        current = swap
        lastSink = sink
    }

    private fun upload(sink: UniformSink) {
        val v = current.values
        var o = 0
        for (p in schema.params) {
            when (p.type) {
                ParamType.FLOAT -> sink.setFloat(p.uniform, v[o])
                ParamType.INT -> sink.setInt(p.uniform, v[o].toInt())
                ParamType.BOOL -> sink.setBool(p.uniform, v[o] != 0f)
                ParamType.VEC2 -> sink.setVec2(p.uniform, v[o], v[o + 1])
                ParamType.VEC3 -> sink.setVec3(p.uniform, v[o], v[o + 1], v[o + 2])
                ParamType.VEC4 -> sink.setVec4(p.uniform, v[o], v[o + 1], v[o + 2], v[o + 3])
            }
            o += p.type.components
        }
        for ((i, t) in schema.textures.withIndex()) {
            sink.setInt(t.samplerUniform, t.slot)
            t.hasUniform?.let { sink.setBool(it, current.textureIds[i] != 0) }
        }
    }

    /** Forget cached uniform state, e.g. after a shader is recompiled. */
    fun invalidate() {
        lastSink = null
    }
}
