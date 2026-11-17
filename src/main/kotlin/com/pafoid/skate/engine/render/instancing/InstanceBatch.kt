package com.pafoid.skate.engine.render.instancing

import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.RhiBuffer
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import org.joml.Matrix4f
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer

/**
 * One mesh part × material × instance buffer (REN-29 §3.6), drawn with one instanced call by
 * `ModelRenderer.drawInstanced`. Records are packed on the CPU ([InstancePacking]) into a reused staging array and
 * uploaded by [upload] only when they changed; the GPU buffer grows by doubling, so steady frames allocate nothing.
 * Used by terrain scatter (REN-28 task 7) and vegetation cells (REN-29 task 4).
 */
class InstanceBatch(
    val part: MeshPart,
    val material: Material = part.material,
    private val device: () -> RhiDevice = RhiContext::require,
) : AutoCloseable {
    var count = 0
        private set

    /** Packed records, [InstancePacking.FLOATS] per instance; valid up to [count]. */
    var data = FloatArray(INITIAL_CAPACITY * InstancePacking.FLOATS)
        private set

    /** The GPU instance buffer, created by the first [upload]. */
    var buffer: RhiBuffer? = null
        private set

    private var gpuCapacity = 0
    private var staging: FloatBuffer? = null
    private var dirty = false

    /** Called with the instance buffer when [close] releases it (the mesh loader drops its instanced inputs). */
    var onRelease: ((RhiBuffer) -> Unit)? = null

    fun clear() {
        if (count != 0) dirty = true
        count = 0
    }

    fun add(world: Matrix4f, tint: Float = 0f, random: Float = 0f, windPhase: Float = 0f, lodFade: Float = 1f) {
        val needed = (count + 1) * InstancePacking.FLOATS
        if (needed > data.size) data = data.copyOf(maxOf(needed, data.size * 2))
        InstancePacking.put(world, data, count * InstancePacking.FLOATS, tint, random, windPhase, lodFade)
        count++
        dirty = true
    }

    /** Uploads the records if they changed since the last upload; returns the buffer (null when empty). */
    fun upload(): RhiBuffer? {
        if (count == 0) return buffer
        val gpu = buffer ?: device().createBuffer(BufferUsage.INSTANCE, BufferUpdate.DYNAMIC, FloatArray(0)).also { buffer = it }
        if (!dirty) return gpu
        if (count > gpuCapacity) {
            gpuCapacity = maxOf(count, gpuCapacity * 2, INITIAL_CAPACITY)
            gpu.resize(gpuCapacity.toLong() * InstancePacking.FLOATS * Float.SIZE_BYTES)
            staging?.let { MemoryUtil.memFree(it) }
            staging = MemoryUtil.memAllocFloat(gpuCapacity * InstancePacking.FLOATS)
        }
        val s = staging!!
        s.clear()
        s.put(data, 0, count * InstancePacking.FLOATS)
        s.flip()
        gpu.update(0L, s)
        dirty = false
        return gpu
    }

    override fun close() {
        buffer?.let { b -> onRelease?.invoke(b); b.close() }
        buffer = null
        staging?.let { MemoryUtil.memFree(it) }
        staging = null
        gpuCapacity = 0
        count = 0
    }

    companion object {
        const val INITIAL_CAPACITY = 64
    }
}
