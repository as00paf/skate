package com.pafoid.skate.engine.render.rhi.vulkan

/**
 * GPU progress as submission serials (VK-01). Each queue submission gets the next serial; [completed] is the highest
 * serial known to be finished. Because a queue executes in submission order, a fence or idle wait that covers serial
 * `n` also covers every serial before it. Pure bookkeeping: no Vulkan calls, unit-tested without a device.
 */
internal class SubmissionTracker {
    var submitted = 0L
        private set
    var completed = 0L
        private set

    /** True while commands are recorded but not submitted yet; they will land in serial [submitted] + 1. */
    var recording = false

    private val releases = ArrayDeque<Pending>()

    private class Pending(val serial: Long, val action: () -> Unit)

    /** Number of releases still waiting for the GPU. */
    val pendingReleases: Int get() = releases.size

    /** Records a submission and returns its serial. Its commands are no longer [recording]. */
    fun submit(): Long {
        recording = false
        return ++submitted
    }

    /** The GPU finished [serial] (and so everything before it); runs the releases that became safe. */
    fun complete(serial: Long) {
        if (serial > completed) completed = minOf(serial, submitted)
        drain()
    }

    /** The queue is idle: every submission is finished. */
    fun completeAll() = complete(submitted)

    /**
     * Runs [action] once the GPU can no longer use what it frees: after every submission made so far, plus the
     * commands still being recorded. Runs at once when nothing is in flight.
     */
    fun release(action: () -> Unit) {
        val serial = submitted + (if (recording) 1 else 0)
        if (serial <= completed) action() else releases.addLast(Pending(serial, action))
    }

    /** Runs every pending release regardless of GPU progress (device shutdown, after a device wait idle). */
    fun releaseAll() {
        while (releases.isNotEmpty()) releases.removeFirst().action()
    }

    private fun drain() {
        while (releases.isNotEmpty() && releases.first().serial <= completed) releases.removeFirst().action()
    }
}

/**
 * Frames in flight (VK-01): which slot (fence, semaphores, command buffer, transient rings) the CPU records into, and
 * which submission each slot last fenced. A slot is reused only after its previous frame's fence has signalled, so
 * at most [framesInFlight] frames are queued on the GPU. The fence calls are injected; the logic is unit-tested.
 */
internal class FrameRing(
    val framesInFlight: Int,
    private val tracker: SubmissionTracker,
    private val waitFence: (slot: Int) -> Unit,
) {
    init {
        require(framesInFlight >= 1) { "framesInFlight must be at least 1, got $framesInFlight" }
    }

    /** Slot of the frame being recorded (or of the next one when no frame is open). */
    var slot = 0
        private set

    /** Frames ended so far. */
    var frameCount = 0L
        private set

    var isOpen = false
        private set

    /** Serial of the last fenced submission per slot (0: never submitted, the fence is free). */
    private val fenced = LongArray(framesInFlight)

    /** True when the slot's fence guards a submission that is not known to be finished: [begin] will wait on it. */
    fun mustWait(slot: Int): Boolean = fenced[slot] > tracker.completed

    /**
     * Opens a frame in the current slot: waits for the slot's previous frame if the GPU may still use it, so its
     * command buffer and transient memory can be reset. Returns the slot. Does nothing when a frame is already open.
     */
    fun begin(): Int {
        if (isOpen) return slot
        if (mustWait(slot)) {
            waitFence(slot)
            tracker.complete(fenced[slot])
        }
        isOpen = true
        return slot
    }

    /** The frame's last submission, signalling this slot's fence; returns its serial. */
    fun submitFenced(): Long {
        check(isOpen) { "submitFenced outside a frame" }
        val serial = tracker.submit()
        fenced[slot] = serial
        return serial
    }

    /** Closes the frame and moves to the next slot. */
    fun end() {
        check(isOpen) { "end outside a frame" }
        isOpen = false
        frameCount++
        slot = (slot + 1) % framesInFlight
    }

    companion object {
        const val DEFAULT_FRAMES_IN_FLIGHT = 2
    }
}
