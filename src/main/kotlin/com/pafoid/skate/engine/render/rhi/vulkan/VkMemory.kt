package com.pafoid.skate.engine.render.rhi.vulkan

import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.util.vma.Vma.*
import org.lwjgl.util.vma.VmaAllocationCreateInfo
import org.lwjgl.util.vma.VmaAllocationInfo
import org.lwjgl.util.vma.VmaAllocatorCreateInfo
import org.lwjgl.util.vma.VmaVulkanFunctions
import org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE
import org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1
import org.lwjgl.vulkan.VkBufferCreateInfo
import org.lwjgl.vulkan.VkImageCreateInfo

/**
 * GPU memory for the Vulkan backend (VK-02): AMD's Vulkan Memory Allocator (VMA, MIT license) through LWJGL's
 * `lwjgl-vma` binding (BSD-3-Clause). VMA sub-allocates large device blocks, so a scene's hundreds of buffers and
 * images stay far below `maxMemoryAllocationCount`.
 *
 * Two kinds of memory: [MemoryKind.DEVICE] (fastest for the GPU; filled through staging copies) and
 * [MemoryKind.HOST] (mapped for its whole life, written sequentially by the CPU: staging, uniform rings, buffers
 * rewritten every frame).
 */
internal class VkAllocator(private val core: VkCore) : AutoCloseable {
    val handle: Long = stackPush().use { s ->
        val functions = VmaVulkanFunctions.calloc(s).set(core.instance, core.device)
        val info = VmaAllocatorCreateInfo.calloc(s)
            .physicalDevice(core.physical).device(core.device).instance(core.instance)
            .pVulkanFunctions(functions).vulkanApiVersion(VK_API_VERSION_1_1)
        val p = s.mallocPointer(1)
        vk(vmaCreateAllocator(info, p), "vmaCreateAllocator")
        p[0]
    }

    /** Live allocations (buffers and images), for leak checks in tests. */
    var liveAllocations = 0
        private set

    fun buffer(size: Long, usage: Int, kind: MemoryKind): VkAllocBuffer = stackPush().use { s ->
        val info = VkBufferCreateInfo.calloc(s).`sType$Default`()
            .size(maxOf(size, MIN_SIZE)).usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE)
        val alloc = allocationInfo(s, kind)
        val pBuffer = s.mallocLong(1)
        val pAllocation = s.mallocPointer(1)
        val result = VmaAllocationInfo.calloc(s)
        vk(vmaCreateBuffer(handle, info, alloc, pBuffer, pAllocation, result), "vmaCreateBuffer")
        liveAllocations++
        VkAllocBuffer(this, pBuffer[0], pAllocation[0], result.pMappedData(), maxOf(size, MIN_SIZE))
    }

    fun image(info: VkImageCreateInfo): VkAllocImage = stackPush().use { s ->
        val alloc = allocationInfo(s, MemoryKind.DEVICE)
        val pImage = s.mallocLong(1)
        val pAllocation = s.mallocPointer(1)
        vk(vmaCreateImage(handle, info, alloc, pImage, pAllocation, null), "vmaCreateImage")
        liveAllocations++
        VkAllocImage(this, pImage[0], pAllocation[0])
    }

    private fun allocationInfo(s: org.lwjgl.system.MemoryStack, kind: MemoryKind) = VmaAllocationCreateInfo.calloc(s).apply {
        when (kind) {
            MemoryKind.DEVICE -> usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE)
            MemoryKind.HOST -> usage(VMA_MEMORY_USAGE_AUTO)
                .flags(VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT or VMA_ALLOCATION_CREATE_MAPPED_BIT)
        }
    }

    internal fun free(buffer: Long, allocation: Long) {
        vmaDestroyBuffer(handle, buffer, allocation)
        liveAllocations--
    }

    internal fun freeImage(image: Long, allocation: Long) {
        vmaDestroyImage(handle, image, allocation)
        liveAllocations--
    }

    override fun close() = vmaDestroyAllocator(handle)

    private companion object {
        const val MIN_SIZE = 16L
    }
}

enum class MemoryKind { DEVICE, HOST }

/** A buffer and its memory; [address] is the persistent mapping of a [MemoryKind.HOST] buffer (0 otherwise). */
internal class VkAllocBuffer(
    private val allocator: VkAllocator,
    val buffer: Long,
    private val allocation: Long,
    val address: Long,
    val size: Long,
) : AutoCloseable {
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        allocator.free(buffer, allocation)
    }
}

internal class VkAllocImage(private val allocator: VkAllocator, val image: Long, private val allocation: Long) : AutoCloseable {
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        allocator.freeImage(image, allocation)
    }
}

/**
 * Releases GPU objects once the GPU no longer uses them. VK-01 adds `VkCore.destroyLater` (a submission-serial queue);
 * until it is merged here, the spike's every-pass wait makes running the block at once safe.
 */
internal fun VkCore.destroyLaterOrNow(block: () -> Unit) = block()
