package com.pafoid.skate.editor.animation.mocap

/**
 * Playback and trim range of the video preview in Video to Animation (ANI-20). Frames are source video frames
 * (0 until [frameCount]); [inFrame]..[outFrame] (both included) is the part that becomes the animation, so the clip
 * lasts `(outFrame - inFrame) / fps` seconds. Playback loops inside the range. Pure: the window draws it, tests drive it.
 */
class VideoPlayer(val fps: Float, frameCount: Int) {
    var frameCount: Int = frameCount.coerceAtLeast(1); private set
    /** Current frame (fractional while playing, so slow frame rates still advance smoothly). */
    private var position = 0f
    val frame: Int get() = position.toInt().coerceIn(0, frameCount - 1)
    var playing = false; private set
    var inFrame = 0; private set
    var outFrame = this.frameCount - 1; private set

    /** True when the markers cover less than the whole video. */
    val trimmed: Boolean get() = inFrame > 0 || outFrame < frameCount - 1
    val rangeFrames: Int get() = outFrame - inFrame + 1
    /** Length of the clip the range makes, in seconds. */
    val rangeSeconds: Float get() = (outFrame - inFrame) / fps
    val time: Float get() = frame / fps

    /** The probe's frame count is an estimate; the preview frames give the real one. Markers stay inside it. */
    fun updateFrameCount(count: Int) {
        val full = outFrame == frameCount - 1
        frameCount = count.coerceAtLeast(1)
        outFrame = if (full) frameCount - 1 else outFrame.coerceAtMost(frameCount - 1)
        inFrame = inFrame.coerceAtMost(outFrame)
        position = position.coerceAtMost((frameCount - 1).toFloat())
    }

    fun seek(frame: Int) { position = frame.coerceIn(0, frameCount - 1).toFloat() }

    fun step(frames: Int) { playing = false; seek(frame + frames) }

    fun togglePlay() {
        if (!playing && (frame < inFrame || frame >= outFrame)) seek(inFrame)
        playing = !playing
    }

    fun pause() { playing = false }

    /** Advances by [dt] seconds; wraps from the out marker back to the in marker. */
    fun tick(dt: Float) {
        if (!playing) return
        position += dt * fps
        if (position > outFrame + 1 || position < inFrame) {
            val span = rangeFrames.toFloat()
            position = inFrame + ((position - inFrame) % span + span) % span
        }
    }

    /** Sets the in marker (at the current frame by default); the out marker moves along if it would come first. */
    fun markIn(frame: Int = this.frame) {
        inFrame = frame.coerceIn(0, frameCount - 1)
        if (outFrame < inFrame) outFrame = inFrame
    }

    fun markOut(frame: Int = this.frame) {
        outFrame = frame.coerceIn(0, frameCount - 1)
        if (inFrame > outFrame) inFrame = outFrame
    }

    fun clearRange() { inFrame = 0; outFrame = frameCount - 1 }

    /**
     * The preview image to show for [frame]: the last extracted preview frame at or before it ([available] sorted),
     * or null when none is extracted yet.
     */
    fun previewFrameFor(available: IntArray, frame: Int = this.frame): Int? {
        if (available.isEmpty()) return null
        var lo = 0
        var hi = available.size - 1
        if (frame < available[0]) return available[0]
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (available[mid] <= frame) lo = mid else hi = mid - 1
        }
        return available[lo]
    }
}
