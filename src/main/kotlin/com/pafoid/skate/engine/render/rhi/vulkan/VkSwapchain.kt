package com.pafoid.skate.engine.render.rhi.vulkan

import org.lwjgl.glfw.GLFW.glfwGetFramebufferSize
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
import org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR
import org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR
import org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR
import org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR
import org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR
import org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR
import org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR
import org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR
import org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR
import org.lwjgl.vulkan.KHRSwapchain.vkCreateSwapchainKHR
import org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR
import org.lwjgl.vulkan.KHRSwapchain.vkGetSwapchainImagesKHR
import org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkFramebufferCreateInfo
import org.lwjgl.vulkan.VkImageMemoryBarrier
import org.lwjgl.vulkan.VkImageViewCreateInfo
import org.lwjgl.vulkan.VkPresentInfoKHR
import org.lwjgl.vulkan.VkSemaphoreCreateInfo
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR
import org.lwjgl.vulkan.VkSurfaceFormatKHR
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR

/** Swapchain choices from what the surface reports (VK-01). Pure: unit-tested without a device. */
internal object SwapchainPolicy {
    /** `currentExtent` value meaning "the window decides" (0xFFFFFFFF). */
    const val EXTENT_FROM_WINDOW = -1

    /**
     * Vsync presents with FIFO (always supported, tear-free, capped at the refresh rate). Without vsync, MAILBOX
     * (tear-free, uncapped), then IMMEDIATE (may tear), then FIFO.
     */
    fun presentMode(available: IntArray, vsync: Boolean): Int = when {
        vsync -> VK_PRESENT_MODE_FIFO_KHR
        VK_PRESENT_MODE_MAILBOX_KHR in available -> VK_PRESENT_MODE_MAILBOX_KHR
        VK_PRESENT_MODE_IMMEDIATE_KHR in available -> VK_PRESENT_MODE_IMMEDIATE_KHR
        else -> VK_PRESENT_MODE_FIFO_KHR
    }

    /**
     * B8G8R8A8_UNORM (then R8G8B8A8_UNORM) with sRGB non-linear colour space: a linear-write target like GL's default
     * framebuffer, whose gamma is applied by the post-processing shader. Otherwise the surface's first format.
     */
    fun surfaceFormat(formats: List<Pair<Int, Int>>): Pair<Int, Int> {
        require(formats.isNotEmpty()) { "The surface reports no formats" }
        for (wanted in intArrayOf(VK_FORMAT_B8G8R8A8_UNORM, VK_FORMAT_R8G8B8A8_UNORM)) {
            formats.firstOrNull { it.first == wanted && it.second == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR }?.let { return it }
        }
        // A single UNDEFINED entry means any format is fine
        if (formats.size == 1 && formats[0].first == VK_FORMAT_UNDEFINED) return VK_FORMAT_B8G8R8A8_UNORM to VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
        return formats[0]
    }

    /**
     * Swapchain size: the surface's current extent, or the window's framebuffer size clamped to the surface limits
     * when the surface lets the swapchain decide. A zero dimension means the window is minimized.
     */
    fun extent(current: IntArray, min: IntArray, max: IntArray, framebuffer: IntArray): IntArray =
        if (current[0] != EXTENT_FROM_WINDOW) intArrayOf(current[0], current[1])
        else intArrayOf(framebuffer[0].coerceIn(min[0], max[0]), framebuffer[1].coerceIn(min[1], max[1]))

    /** One more image than the minimum, so the CPU rarely waits on the presentation engine; 0 = no maximum. */
    fun imageCount(min: Int, max: Int): Int = if (max > 0) minOf(min + 1, max) else min + 1

    fun isMinimized(width: Int, height: Int): Boolean = width <= 0 || height <= 0
}

/**
 * The window's swapchain (VK-01): images, views, per-image "render finished" semaphores (waited by present), and
 * framebuffers per render pass and image. [acquire] recreates it when the window size changed or presentation
 * reported it out of date; a minimized window has no swapchain image and frames skip the back buffer.
 */
internal class VkSwapchain(
    private val core: VkCore,
    private val window: Long,
    vsync: Boolean,
    /** One semaphore per frame in flight, signalled by acquire and waited by that frame's submission. */
    framesInFlight: Int,
) : AutoCloseable {
    var vsync: Boolean = vsync
        set(value) {
            if (field != value) { field = value; outOfDate = true }
        }

    var handle = VK_NULL_HANDLE
        private set
    var format = VK_FORMAT_UNDEFINED
        private set
    var width = 0
        private set
    var height = 0
        private set
    var presentMode = VK_PRESENT_MODE_FIFO_KHR
        private set

    /** Whether swapchain images can be read back ([VulkanDevice.readPixels] with a null target). */
    var readable = false
        private set

    /** Times the swapchain was (re)created; tests read it to check resize handling. */
    var generation = 0
        private set

    var images = LongArray(0)
        private set
    private var views = LongArray(0)
    private var renderFinished = LongArray(0)
    val imageAvailable = LongArray(framesInFlight) { createSemaphore() }
    /** Per image: render pass -> framebuffer. */
    private var framebuffers = emptyArray<HashMap<Long, Long>>()
    private var outOfDate = true

    /** Marks the swapchain for recreation before the next acquire (resize, vsync change). */
    fun invalidate() {
        outOfDate = true
    }

    private fun framebufferSize(): IntArray = stackPush().use { s ->
        val w = s.mallocInt(1)
        val h = s.mallocInt(1)
        glfwGetFramebufferSize(window, w, h)
        intArrayOf(w[0], h[0])
    }

    /**
     * The index of the next image, signalling [imageAvailable] of [slot], or -1 when there is none (minimized).
     * Recreates the swapchain first when needed.
     */
    fun acquire(slot: Int): Int {
        val fb = framebufferSize()
        if (SwapchainPolicy.isMinimized(fb[0], fb[1])) return -1
        if (outOfDate || handle == VK_NULL_HANDLE || fb[0] != width || fb[1] != height) {
            if (!recreate()) return -1
        }
        repeat(2) {
            val index = stackPush().use { s ->
                val p = s.mallocInt(1)
                when (val r = vkAcquireNextImageKHR(core.device, handle, -1L, imageAvailable[slot], VK_NULL_HANDLE, p)) {
                    VK_SUCCESS -> p[0]
                    VK_SUBOPTIMAL_KHR -> { outOfDate = true; p[0] }
                    VK_ERROR_OUT_OF_DATE_KHR -> -1
                    else -> { vk(r, "vkAcquireNextImageKHR"); -1 }
                }
            }
            if (index >= 0) return index
            if (!recreate()) return -1
        }
        return -1
    }

    /** Queues [image] for presentation once [renderFinished] of it is signalled. */
    fun present(image: Int) = stackPush().use { s ->
        val info = VkPresentInfoKHR.calloc(s).`sType$Default`()
            .pWaitSemaphores(s.longs(renderFinished[image]))
            .swapchainCount(1).pSwapchains(s.longs(handle)).pImageIndices(s.ints(image))
        when (val r = vkQueuePresentKHR(core.queue, info)) {
            VK_SUCCESS -> {}
            VK_SUBOPTIMAL_KHR, VK_ERROR_OUT_OF_DATE_KHR -> outOfDate = true
            else -> vk(r, "vkQueuePresentKHR")
        }
    }

    fun renderFinished(image: Int): Long = renderFinished[image]

    /** Framebuffer of [renderPass] over image [index]. */
    fun framebuffer(renderPass: Long, index: Int): Long = framebuffers[index].getOrPut(renderPass) {
        stackPush().use { s ->
            val info = VkFramebufferCreateInfo.calloc(s).`sType$Default`()
                .renderPass(renderPass).pAttachments(s.longs(views[index])).width(width).height(height).layers(1)
            val p = s.mallocLong(1)
            vk(vkCreateFramebuffer(core.device, info, null, p), "vkCreateFramebuffer")
            p[0]
        }
    }

    /** Layout barrier for swapchain image [index]. */
    fun transition(cmd: VkCommandBuffer, index: Int, from: Int, to: Int) = stackPush().use { s ->
        val barrier = VkImageMemoryBarrier.calloc(1, s)
        barrier[0].`sType$Default`().oldLayout(from).newLayout(to)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).image(images[index])
            .subresourceRange { it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1) }
            .srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT).dstAccessMask(VK_ACCESS_MEMORY_READ_BIT or VK_ACCESS_MEMORY_WRITE_BIT)
        vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barrier)
    }

    /**
     * Builds a swapchain for the window's current size, replacing the old one (the device is idled first, so nothing
     * in flight uses the old images). Returns false when the window is minimized; the old swapchain is then kept
     * marked out of date.
     */
    private fun recreate(): Boolean = stackPush().use { s ->
        val caps = VkSurfaceCapabilitiesKHR.malloc(s)
        vk(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(core.physical, core.surface, caps), "vkGetPhysicalDeviceSurfaceCapabilitiesKHR")
        val extent = SwapchainPolicy.extent(
            intArrayOf(caps.currentExtent().width(), caps.currentExtent().height()),
            intArrayOf(caps.minImageExtent().width(), caps.minImageExtent().height()),
            intArrayOf(caps.maxImageExtent().width(), caps.maxImageExtent().height()),
            framebufferSize(),
        )
        if (SwapchainPolicy.isMinimized(extent[0], extent[1])) {
            outOfDate = true
            return@use false
        }
        core.waitIdle()

        val n = s.mallocInt(1)
        vkGetPhysicalDeviceSurfaceFormatsKHR(core.physical, core.surface, n, null)
        val formats = VkSurfaceFormatKHR.malloc(n[0], s)
        vkGetPhysicalDeviceSurfaceFormatsKHR(core.physical, core.surface, n, formats)
        val (chosenFormat, colorSpace) = SwapchainPolicy.surfaceFormat((0 until n[0]).map { formats[it].format() to formats[it].colorSpace() })
        vkGetPhysicalDeviceSurfacePresentModesKHR(core.physical, core.surface, n, null)
        val modes = s.mallocInt(n[0])
        vkGetPhysicalDeviceSurfacePresentModesKHR(core.physical, core.surface, n, modes)
        val mode = SwapchainPolicy.presentMode(IntArray(n[0]) { modes[it] }, vsync)

        val supported = caps.supportedUsageFlags()
        var usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT or VK_IMAGE_USAGE_TRANSFER_DST_BIT
        val canRead = supported and VK_IMAGE_USAGE_TRANSFER_SRC_BIT != 0
        if (canRead) usage = usage or VK_IMAGE_USAGE_TRANSFER_SRC_BIT
        check(supported and VK_IMAGE_USAGE_TRANSFER_DST_BIT != 0) { "The swapchain cannot be a blit destination" }

        val old = handle
        val info = VkSwapchainCreateInfoKHR.calloc(s).`sType$Default`()
            .surface(core.surface)
            .minImageCount(SwapchainPolicy.imageCount(caps.minImageCount(), caps.maxImageCount()))
            .imageFormat(chosenFormat).imageColorSpace(colorSpace)
            .imageExtent { it.width(extent[0]).height(extent[1]) }
            .imageArrayLayers(1).imageUsage(usage).imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
            .preTransform(caps.currentTransform()).compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
            .presentMode(mode).clipped(true).oldSwapchain(old)
        val p = s.mallocLong(1)
        vk(vkCreateSwapchainKHR(core.device, info, null, p), "vkCreateSwapchainKHR")
        releaseImages()
        if (old != VK_NULL_HANDLE) vkDestroySwapchainKHR(core.device, old, null)
        handle = p[0]
        format = chosenFormat
        width = extent[0]
        height = extent[1]
        presentMode = mode
        readable = canRead

        vkGetSwapchainImagesKHR(core.device, handle, n, null)
        val pImages = s.mallocLong(n[0])
        vkGetSwapchainImagesKHR(core.device, handle, n, pImages)
        images = LongArray(n[0]) { pImages[it] }
        views = LongArray(images.size) { createView(images[it]) }
        renderFinished = LongArray(images.size) { createSemaphore() }
        framebuffers = Array(images.size) { HashMap() }
        outOfDate = false
        generation++
        true
    }

    private fun createView(image: Long): Long = stackPush().use { s ->
        val info = VkImageViewCreateInfo.calloc(s).`sType$Default`().image(image).viewType(VK_IMAGE_VIEW_TYPE_2D).format(format)
            .subresourceRange { it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1) }
        val p = s.mallocLong(1)
        vk(vkCreateImageView(core.device, info, null, p), "vkCreateImageView")
        p[0]
    }

    private fun createSemaphore(): Long = stackPush().use { s ->
        val p = s.mallocLong(1)
        vk(vkCreateSemaphore(core.device, VkSemaphoreCreateInfo.calloc(s).`sType$Default`(), null, p), "vkCreateSemaphore")
        p[0]
    }

    private fun releaseImages() {
        framebuffers.forEach { m -> m.values.forEach { vkDestroyFramebuffer(core.device, it, null) } }
        framebuffers = emptyArray()
        views.forEach { vkDestroyImageView(core.device, it, null) }
        renderFinished.forEach { vkDestroySemaphore(core.device, it, null) }
        views = LongArray(0)
        renderFinished = LongArray(0)
        images = LongArray(0)
    }

    override fun close() {
        core.waitIdle()
        releaseImages()
        imageAvailable.forEach { vkDestroySemaphore(core.device, it, null) }
        if (handle != VK_NULL_HANDLE) vkDestroySwapchainKHR(core.device, handle, null)
        handle = VK_NULL_HANDLE
    }

    companion object {
        /** Layout a presented image rests in. */
        const val PRESENT_LAYOUT = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
    }
}
