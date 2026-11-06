package com.pafoid.skate.engine.render.rhi.vulkan

import com.pafoid.skate.engine.render.rhi.TextureType
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VK11.VK_ERROR_OUT_OF_POOL_MEMORY
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkDescriptorBufferInfo
import org.lwjgl.vulkan.VkDescriptorImageInfo
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo
import org.lwjgl.vulkan.VkDescriptorPoolSize
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo
import org.lwjgl.vulkan.VkWriteDescriptorSet
import java.nio.ByteBuffer

/**
 * One binding of a program's descriptor set, merged from both stages' SPIR-V reflection: [stages] says which stages
 * read it ([VERTEX] / [FRAGMENT] bits), so Vulkan only makes it visible where it is used.
 */
data class LayoutBinding(
    val binding: Int,
    val kind: SpirvReflection.DescriptorKind,
    val count: Int,
    val stages: Int,
    val descriptor: SpirvReflection.Descriptor,
) {
    companion object {
        const val VERTEX = 1
        const val FRAGMENT = 2
    }
}

/** Builds descriptor set layouts from reflection (VK-02). Pure Kotlin, unit-tested. */
object DescriptorLayouts {
    /**
     * The program's bindings: the union of both stages, each visible to the stages that declare it. A binding both
     * stages declare must agree on its kind and count; a uniform block keeps the larger size (each stage's SPIR-V
     * only sizes up to the members it uses).
     */
    fun merge(vertex: List<SpirvReflection.Descriptor>, fragment: List<SpirvReflection.Descriptor>): List<LayoutBinding> {
        val out = sortedMapOf<Int, LayoutBinding>()
        fun add(d: SpirvReflection.Descriptor, stage: Int) {
            val prev = out[d.binding]
            if (prev == null) {
                out[d.binding] = LayoutBinding(d.binding, d.kind, d.count, stage, d)
                return
            }
            require(prev.kind == d.kind && prev.count == d.count) {
                "Binding ${d.binding} is ${prev.descriptor.name} (${prev.kind} x${prev.count}) in one stage and ${d.name} (${d.kind} x${d.count}) in the other"
            }
            val keep = if (d.size > prev.descriptor.size) d else prev.descriptor
            out[d.binding] = prev.copy(stages = prev.stages or stage, descriptor = keep)
        }
        vertex.forEach { add(it, LayoutBinding.VERTEX) }
        fragment.forEach { add(it, LayoutBinding.FRAGMENT) }
        return out.values.toList()
    }

    /** Layout identity: programs with the same bindings share one `VkDescriptorSetLayout`. */
    fun key(bindings: List<LayoutBinding>): List<Int> =
        bindings.flatMap { listOf(it.binding, it.kind.ordinal, it.count, it.stages) }

    /** Descriptors of each type a set with [bindings] consumes: (uniform buffers, combined image samplers). */
    fun counts(bindings: List<LayoutBinding>): Pair<Int, Int> =
        bindings.filter { it.kind == SpirvReflection.DescriptorKind.UNIFORM_BUFFER }.sumOf { it.count } to
            bindings.filter { it.kind == SpirvReflection.DescriptorKind.COMBINED_IMAGE_SAMPLER }.sumOf { it.count }
}

/**
 * Bookkeeping of a per-frame uniform ring (VK-02): aligned sub-allocations from a list of chunks, a new (larger)
 * chunk when the current one is full, and [reset] when the frame's GPU work is done. Pure Kotlin, unit-tested; the
 * Vulkan side ([UniformRing]) maps each chunk to a host buffer.
 */
class RingAllocator(private val alignment: Long, private val chunkSize: Long) {
    init {
        require(alignment > 0 && alignment and (alignment - 1) == 0L) { "Alignment must be a power of two, got $alignment" }
    }

    /** Capacity of each chunk, in creation order. */
    val chunks = ArrayList<Long>()
    private var chunk = -1
    private var offset = 0L

    /** Highest number of bytes used in one frame so far. */
    var peak = 0L
        private set
    private var used = 0L

    /**
     * Returns (chunk index, byte offset) of [size] fresh bytes. Chunks are only ever appended (indices stay stable for
     * the buffers behind them); a block too big for the next reused chunk skips to one that fits.
     */
    fun allocate(size: Long): Pair<Int, Long> {
        require(size > 0) { "Empty allocation" }
        var start = align(offset)
        if (chunk < 0 || start + size > chunks[chunk]) {
            do {
                chunk++
                if (chunk == chunks.size) chunks += maxOf(chunkSize, align(size))
            } while (size > chunks[chunk])
            start = 0
        }
        offset = start + size
        used += size
        peak = maxOf(peak, used)
        return chunk to start
    }

    /** The frame is done on the GPU: everything can be reused (chunks are kept). */
    fun reset() {
        chunk = -1
        offset = 0
        used = 0
    }

    private fun align(v: Long) = (v + alignment - 1) and (alignment - 1).inv()
}

/**
 * A frame's ring of host-visible memory (uniform data, or dynamic vertex/index data): chunks that [RingAllocator]
 * hands out, all with buffer [usage].
 */
internal class HostRing(
    private val allocator: VkAllocator,
    private val usage: Int,
    alignment: Long,
) : AutoCloseable {
    private val ring = RingAllocator(alignment, CHUNK_SIZE)
    private val buffers = ArrayList<VkAllocBuffer>()

    /** Copies [size] bytes of [src] from [from] (zero-filled past its end, all zeros when null); returns (buffer, offset). */
    fun push(src: ByteBuffer?, from: Int, size: Long): Pair<Long, Long> {
        val (chunk, offset) = ring.allocate(size)
        while (buffers.size < ring.chunks.size) {
            val index = buffers.size
            buffers += allocator.buffer(ring.chunks[index], usage, MemoryKind.HOST)
        }
        val b = buffers[chunk]
        val address = b.address + offset
        MemoryUtil.memSet(address, 0, size)
        if (src != null) {
            val n = minOf(size.toInt(), src.capacity() - from).coerceAtLeast(0)
            if (n > 0) {
                val dst = MemoryUtil.memByteBuffer(address, n)
                val view = src.duplicate().also { it.position(from).limit(from + n) }
                dst.put(view)
            }
        }
        return b.buffer to offset
    }

    fun reset() = ring.reset()

    val peak: Long get() = ring.peak

    override fun close() = buffers.forEach { it.close() }

    private companion object {
        const val CHUNK_SIZE = 4L shl 20
    }
}

/**
 * Descriptor pools of one frame in flight: sets are allocated per draw and all freed at once by [reset]. A new pool
 * is added when the current one runs out, so a busy frame never fails.
 */
internal class DescriptorPools(private val core: VkCore) : AutoCloseable {
    private val pools = ArrayList<Long>()
    private var current = 0

    fun allocate(layout: Long): Long = stackPush().use { s ->
        while (true) {
            if (current == pools.size) pools += createPool(s)
            val info = VkDescriptorSetAllocateInfo.calloc(s).`sType$Default`()
                .descriptorPool(pools[current]).pSetLayouts(s.longs(layout))
            val p = s.mallocLong(1)
            when (val r = vkAllocateDescriptorSets(core.device, info, p)) {
                VK_SUCCESS -> return@use p[0]
                VK_ERROR_OUT_OF_POOL_MEMORY, VK_ERROR_FRAGMENTED_POOL -> current++
                else -> vk(r, "vkAllocateDescriptorSets")
            }
        }
        @Suppress("UNREACHABLE_CODE") 0L
    }

    fun reset() {
        for (pool in pools) vkResetDescriptorPool(core.device, pool, 0)
        current = 0
    }

    private fun createPool(s: MemoryStack): Long {
        val sizes = VkDescriptorPoolSize.calloc(2, s)
        sizes[0].type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(SETS_PER_POOL * 4)
        sizes[1].type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(SETS_PER_POOL * 16)
        val info = VkDescriptorPoolCreateInfo.calloc(s).`sType$Default`().maxSets(SETS_PER_POOL).pPoolSizes(sizes)
        val p = s.mallocLong(1)
        vk(vkCreateDescriptorPool(core.device, info, null, p), "vkCreateDescriptorPool")
        return p[0]
    }

    override fun close() = pools.forEach { vkDestroyDescriptorPool(core.device, it, null) }

    private companion object {
        const val SETS_PER_POOL = 1024
    }
}

/** Shared `VkDescriptorSetLayout`s, one per distinct [DescriptorLayouts.key]. */
internal class DescriptorLayoutCache(private val core: VkCore) : AutoCloseable {
    private val layouts = HashMap<List<Int>, Long>()

    fun layout(bindings: List<LayoutBinding>): Long = layouts.getOrPut(DescriptorLayouts.key(bindings)) {
        stackPush().use { s ->
            val vk = VkDescriptorSetLayoutBinding.calloc(bindings.size, s)
            bindings.forEachIndexed { i, b ->
                var stages = 0
                if (b.stages and LayoutBinding.VERTEX != 0) stages = stages or VK_SHADER_STAGE_VERTEX_BIT
                if (b.stages and LayoutBinding.FRAGMENT != 0) stages = stages or VK_SHADER_STAGE_FRAGMENT_BIT
                vk[i].binding(b.binding).descriptorType(descriptorType(b.kind)).descriptorCount(b.count).stageFlags(stages)
            }
            val info = VkDescriptorSetLayoutCreateInfo.calloc(s).`sType$Default`().pBindings(vk)
            val p = s.mallocLong(1)
            vk(vkCreateDescriptorSetLayout(core.device, info, null, p), "vkCreateDescriptorSetLayout")
            p[0]
        }
    }

    override fun close() = layouts.values.forEach { vkDestroyDescriptorSetLayout(core.device, it, null) }

    companion object {
        fun descriptorType(kind: SpirvReflection.DescriptorKind) = when (kind) {
            SpirvReflection.DescriptorKind.UNIFORM_BUFFER -> VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
            SpirvReflection.DescriptorKind.COMBINED_IMAGE_SAMPLER -> VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
        }
    }
}

/**
 * Transient per-frame resources of one frame in flight (VK-02): a uniform ring, a vertex ring and descriptor pools.
 * [VkFrameResources.beginFrame] resets the slot about to be recorded, once its fence says the GPU is done with it.
 */
internal class FrameSlot(core: VkCore, allocator: VkAllocator) : AutoCloseable {
    val ring = HostRing(allocator, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, core.minUniformAlignment)
    /** Versions of dynamic vertex/index buffers written this frame (16-byte aligned: index offsets need 4). */
    val vertexRing = HostRing(allocator, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT or VK_BUFFER_USAGE_INDEX_BUFFER_BIT, 16)
    val pools = DescriptorPools(core)

    fun reset() {
        ring.reset()
        vertexRing.reset()
        pools.reset()
    }

    override fun close() {
        pools.close()
        ring.close()
        vertexRing.close()
    }
}

/**
 * The per-frame-in-flight transient resources and the per-draw descriptor set: named parameters and uniform blocks
 * are snapshotted into the frame's ring (GL's "value at draw time"), samplers read the texture bound to their unit,
 * and a unit holding nothing usable gets a dummy (opaque black, or far depth for shadow samplers).
 */
internal class VkFrameResources(
    private val core: VkCore,
    allocator: VkAllocator,
    val framesInFlight: Int = 1,
) : AutoCloseable {
    private val slots = List(framesInFlight) { FrameSlot(core, allocator) }
    private var slot = slots[0]

    /**
     * Increments every [beginFrame]: a dynamic buffer pushed under an older serial is pushed again, into the current
     * slot's ring.
     */
    var serial = 0L
        private set

    /** Call when frame [frameIndex] (mod [framesInFlight]) starts recording, after its fence was waited on. */
    fun beginFrame(frameIndex: Int) {
        slot = slots[Math.floorMod(frameIndex, framesInFlight)]
        slot.reset()
        serial++
    }

    /** Copies the first [size] bytes of [data] into this frame's vertex ring; returns (buffer, offset). */
    fun pushVertexData(data: ByteBuffer, size: Long): Pair<Long, Long> = slot.vertexRing.push(data, 0, size)

    /**
     * Binds [input]'s vertex buffers (then [extraBuffers], at offset 0) and its index buffer on [cmd]; dynamic buffers
     * resolve to this frame's version ([VkRhiBuffer.binding]).
     */
    fun bindVertexInput(cmd: VkCommandBuffer, input: VkRhiVertexInput, extraBuffers: LongArray, indexed: Boolean) = stackPush().use { s ->
        val count = input.vertexBuffers.size + extraBuffers.size
        val buffers = s.mallocLong(count)
        val offsets = s.mallocLong(count)
        for (b in input.vertexBuffers) {
            val (buffer, offset) = b.binding(this)
            buffers.put(buffer); offsets.put(offset)
        }
        for (extra in extraBuffers) { buffers.put(extra); offsets.put(0L) }
        buffers.flip(); offsets.flip()
        if (count > 0) vkCmdBindVertexBuffers(cmd, 0, buffers, offsets)
        if (indexed) {
            val (buffer, offset) = input.indexBuffer!!.binding(this)
            vkCmdBindIndexBuffer(cmd, buffer, offset, VK_INDEX_TYPE_UINT32)
        }
    }

    /** Bytes the busiest frame pushed into its uniform ring. */
    val ringPeak: Long get() = slots.maxOf { it.ring.peak }

    /** Bytes the busiest frame pushed into its vertex ring. */
    val vertexRingPeak: Long get() = slots.maxOf { it.vertexRing.peak }

    /**
     * Allocates and fills this draw's descriptor set for [shader] and binds it on [cmd].
     *
     * @param textures Texture per unit ([com.pafoid.skate.engine.render.rhi.RhiCommandList.setTexture])
     * @param uniformBuffers Buffer per engine uniform slot, with [uniformOffsets]
     */
    fun bindDraw(
        cmd: VkCommandBuffer,
        shader: VkRhiShader,
        textures: Array<VkRhiTexture?>,
        uniformBuffers: Array<VkRhiBuffer?>,
        uniformOffsets: LongArray,
        dummyFor: (SpirvReflection.Descriptor) -> VkRhiTexture,
    ) = stackPush().use { s ->
        val compiled = shader.compiled
        val set = slot.pools.allocate(compiled.setLayout)
        val params = shader.parameters() as VkShaderParams
        val writes = VkWriteDescriptorSet.calloc(compiled.bindings.size, s)
        compiled.bindings.forEachIndexed { i, b ->
            val d = b.descriptor
            val w = writes[i].`sType$Default`().dstSet(set).dstBinding(b.binding).dstArrayElement(0)
                .descriptorType(DescriptorLayoutCache.descriptorType(b.kind)).descriptorCount(b.count)
            if (b.kind == SpirvReflection.DescriptorKind.UNIFORM_BUFFER) {
                val size = maxOf(d.size, 16).toLong()
                val (buffer, offset) = if (b.binding == GlslToVulkan.PARAMS_BINDING) {
                    slot.ring.push(params.block.data, 0, size)
                } else {
                    val engineSlot = shader.blockSlots[b.binding]
                    val source = engineSlot?.let { uniformBuffers[it] }
                    if (source == null) slot.ring.push(null, 0, size)
                    else slot.ring.push(source.contents, uniformOffsets[engineSlot].toInt(), size)
                }
                w.pBufferInfo(VkDescriptorBufferInfo.calloc(1, s).also { it[0].buffer(buffer).offset(offset).range(size) })
            } else {
                val units = params.units.of(b.binding)
                val infos = VkDescriptorImageInfo.calloc(b.count, s)
                for (e in 0 until b.count) {
                    val tex = textures.getOrNull(units.getOrElse(e) { 0 })?.takeIf { compatible(it, d) } ?: dummyFor(d)
                    infos[e].sampler(tex.vkSampler).imageView(tex.view).imageLayout(tex.sampledLayout)
                }
                w.pImageInfo(infos)
            }
        }
        vkUpdateDescriptorSets(core.device, writes, null)
        vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, compiled.pipelineLayout, 0, s.longs(set), null)
    }

    override fun close() = slots.forEach { it.close() }

    companion object {
        /** Whether [t] can be read through sampler [d]; see the pure overload. */
        fun compatible(t: VkRhiTexture, d: SpirvReflection.Descriptor): Boolean =
            compatible(t.desc.type, t.isDepth, t.sampler.compare != null, d)

        /**
         * Same image dimension, and: a shadow sampler ([SpirvReflection.Descriptor.depth]) reads a depth texture; a
         * plain sampler reads a colour texture or a depth texture without depth compare (GL and Vulkan both allow
         * sampling depth values directly, as SSAO does with the scene depth; VK-05).
         */
        fun compatible(type: TextureType, isDepth: Boolean, compares: Boolean, d: SpirvReflection.Descriptor): Boolean {
            val dim = when (type) {
                TextureType.TEXTURE_2D -> SpirvReflection.ImageDim.D2
                TextureType.TEXTURE_2D_ARRAY -> SpirvReflection.ImageDim.D2_ARRAY
                TextureType.CUBE -> SpirvReflection.ImageDim.CUBE
            }
            if (dim != d.dim) return false
            return if (d.depth) isDepth else !compares
        }
    }
}
