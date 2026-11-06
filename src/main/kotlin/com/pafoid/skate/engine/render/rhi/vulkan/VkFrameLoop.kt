package com.pafoid.skate.engine.render.rhi.vulkan

import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkCommandBufferBeginInfo
import org.lwjgl.vulkan.VkFenceCreateInfo
import org.lwjgl.vulkan.VkSubmitInfo

/**
 * Frames in flight on the device (VK-01). A frame is one primary command buffer per slot ([FrameRing]): every pass
 * of the frame records into it and [endFrame] submits it with the slot's fence, then presents the back buffer if the
 * frame drew to it. Opening a slot waits for its fence, then [onSlotReady] resets that slot's transient memory
 * (uniform ring, descriptor pools). The back buffer is acquired lazily by the first pass or blit that targets it.
 *
 * Frames open implicitly: the first command recorded opens one, and [beginFrame] ends a frame still open (the
 * engine's renderer calls `beginFrame` every frame but nothing calls `endFrame` yet). [flushAndWait] submits what is
 * recorded so far and waits, for readback in the middle of a frame.
 */
internal class VkFrameLoop(
    private val core: VkCore,
    val swapchain: VkSwapchain?,
    framesInFlight: Int,
    private val onSlotReady: (slot: Int) -> Unit,
) : AutoCloseable {
    private val fences = LongArray(framesInFlight) { createFence() }
    private val fenceUsed = BooleanArray(framesInFlight)
    private val commandBuffers = Array(framesInFlight) { core.allocateCommandBuffer() }

    val ring = FrameRing(framesInFlight, core.submissions) { slot ->
        vk(vkWaitForFences(core.device, fences[slot], true, -1L), "vkWaitForFences")
    }

    val slot: Int get() = ring.slot
    val isOpen: Boolean get() = ring.isOpen

    /** Swapchain image this frame draws to, or -1 (not acquired yet, offscreen device or minimized window). */
    var backbuffer = -1
        private set

    /** Current layout of [backbuffer]; passes and blits that target it update it. */
    var backbufferLayout = VK_IMAGE_LAYOUT_UNDEFINED

    /** True once this frame asked for the back buffer and none was available: back buffer work is dropped. */
    var backbufferMissing = false
        private set

    /** The acquire semaphore has not been waited by a submission yet. */
    private var acquireWaitPending = false

    /** The frame's command buffer, opening a frame if none is open. */
    fun commandBuffer(): VkCommandBuffer {
        if (!ring.isOpen) open()
        return commandBuffers[ring.slot]
    }

    /** Starts a frame; a frame still open is ended (submitted and presented) first. */
    fun beginFrame() {
        if (ring.isOpen) endFrame()
        open()
    }

    private fun open() {
        val slot = ring.begin()
        onSlotReady(slot)
        backbuffer = -1
        backbufferLayout = VK_IMAGE_LAYOUT_UNDEFINED
        backbufferMissing = false
        acquireWaitPending = false
        beginRecording(slot)
    }

    private fun beginRecording(slot: Int) = stackPush().use { s ->
        val cmd = commandBuffers[slot]
        vk(vkResetCommandBuffer(cmd, 0), "vkResetCommandBuffer")
        vk(vkBeginCommandBuffer(cmd, VkCommandBufferBeginInfo.calloc(s).`sType$Default`()
            .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)), "vkBeginCommandBuffer")
        core.submissions.recording = true
    }

    /**
     * The swapchain image index for this frame, acquiring it on first use; -1 without a swapchain or when the window
     * is minimized (callers then skip back buffer work).
     */
    fun acquireBackbuffer(): Int {
        commandBuffer()
        if (backbuffer >= 0 || backbufferMissing) return backbuffer
        val sc = swapchain
        val index = sc?.acquire(ring.slot) ?: -1
        if (index < 0) {
            backbufferMissing = true
            return -1
        }
        backbuffer = index
        backbufferLayout = VK_IMAGE_LAYOUT_UNDEFINED
        acquireWaitPending = true
        return index
    }

    /** Submits the frame (signalling the slot's fence), presents its back buffer and moves to the next slot. */
    fun endFrame() {
        if (!ring.isOpen) return
        val slot = ring.slot
        val cmd = commandBuffers[slot]
        val sc = swapchain
        if (backbuffer >= 0 && backbufferLayout != VkSwapchain.PRESENT_LAYOUT) {
            sc!!.transition(cmd, backbuffer, backbufferLayout, VkSwapchain.PRESENT_LAYOUT)
            backbufferLayout = VkSwapchain.PRESENT_LAYOUT
        }
        vk(vkEndCommandBuffer(cmd), "vkEndCommandBuffer")
        if (fenceUsed[slot]) vk(vkResetFences(core.device, fences[slot]), "vkResetFences") // finished: begin() saw to it
        submit(cmd, signal = if (backbuffer >= 0) sc!!.renderFinished(backbuffer) else VK_NULL_HANDLE, fence = fences[slot])
        fenceUsed[slot] = true
        ring.submitFenced()
        if (backbuffer >= 0) sc!!.present(backbuffer)
        backbuffer = -1
        ring.end()
    }

    /**
     * Submits the commands recorded so far in this frame and waits for the GPU, then keeps recording the same frame
     * (same slot, same back buffer). Its transient memory is reset since nothing in flight uses it any more.
     */
    fun flushAndWait() {
        if (!ring.isOpen) {
            core.waitIdle()
            return
        }
        val slot = ring.slot
        val cmd = commandBuffers[slot]
        vk(vkEndCommandBuffer(cmd), "vkEndCommandBuffer")
        submit(cmd, signal = VK_NULL_HANDLE, fence = VK_NULL_HANDLE)
        core.submissions.submit()
        vk(vkQueueWaitIdle(core.queue), "vkQueueWaitIdle")
        core.submissions.completeAll()
        onSlotReady(slot)
        beginRecording(slot)
    }

    private fun submit(cmd: VkCommandBuffer, signal: Long, fence: Long) = stackPush().use { s ->
        val info = VkSubmitInfo.calloc(s).`sType$Default`().pCommandBuffers(s.pointers(cmd))
        if (acquireWaitPending) {
            info.waitSemaphoreCount(1).pWaitSemaphores(s.longs(swapchain!!.imageAvailable[ring.slot]))
                .pWaitDstStageMask(s.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT or VK_PIPELINE_STAGE_TRANSFER_BIT))
            acquireWaitPending = false
        }
        if (signal != VK_NULL_HANDLE) info.pSignalSemaphores(s.longs(signal))
        vk(vkQueueSubmit(core.queue, info, fence), "vkQueueSubmit")
    }

    private fun createFence(): Long = stackPush().use { s ->
        val p = s.mallocLong(1)
        vk(vkCreateFence(core.device, VkFenceCreateInfo.calloc(s).`sType$Default`(), null, p), "vkCreateFence")
        p[0]
    }

    override fun close() {
        if (ring.isOpen) {
            // Submit what was recorded so the command buffer is not left in the recording state; nothing is presented
            vk(vkEndCommandBuffer(commandBuffers[ring.slot]), "vkEndCommandBuffer")
            if (acquireWaitPending) submit(commandBuffers[ring.slot], VK_NULL_HANDLE, VK_NULL_HANDLE)
            core.submissions.submit()
        }
        core.waitIdle()
        swapchain?.close()
        commandBuffers.forEach { vkFreeCommandBuffers(core.device, core.commandPool, it) }
        fences.forEach { vkDestroyFence(core.device, it, null) }
    }
}
