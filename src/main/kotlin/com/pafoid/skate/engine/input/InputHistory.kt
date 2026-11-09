package com.pafoid.skate.engine.input

import org.joml.Vector2f

/**
 * Time history of named 2D input sources (INP-10). Games [track] what they need (an action pair, a raw stick, the
 * pointer) and query motion over a time window, e.g. to recognize gestures. Nothing is tracked by default.
 * Each track is a preallocated ring buffer; queries do not allocate. Pure and not thread safe.
 */
class InputHistory(private val capacity: Int = 60) {

    init {
        require(capacity >= 2) { "capacity must be at least 2" }
    }

    private class Track(val x: () -> Float, val y: () -> Float, capacity: Int) {
        val t = FloatArray(capacity)
        val xs = FloatArray(capacity)
        val ys = FloatArray(capacity)
        var start = 0
        var size = 0
    }

    private val tracks = LinkedHashMap<String, Track>()

    val trackedNames: Set<String> get() = tracks.keys

    /** Starts (or restarts, clearing its samples) recording [name] from the [x]/[y] suppliers. */
    fun track(name: String, x: () -> Float, y: () -> Float) {
        tracks[name] = Track(x, y, capacity)
    }

    fun untrack(name: String) {
        tracks.remove(name)
    }

    /** Records one sample of every track at [time] seconds; call once per frame. */
    fun sample(time: Float) {
        for (tr in tracks.values) {
            val i = (tr.start + tr.size) % capacity
            tr.t[i] = time; tr.xs[i] = tr.x(); tr.ys[i] = tr.y()
            if (tr.size < capacity) tr.size++ else tr.start = (tr.start + 1) % capacity
        }
    }

    fun clear() = tracks.values.forEach { it.start = 0; it.size = 0 }

    fun sampleCount(name: String): Int = tracks[name]?.size ?: 0

    /** Newest minus oldest sample within [window] seconds of the newest one; zero with fewer than 2 samples. */
    fun displacement(name: String, window: Float, out: Vector2f = Vector2f()): Vector2f {
        val tr = tracks[name] ?: return out.zero()
        val first = firstInWindow(tr, window) ?: return out.zero()
        val last = index(tr, tr.size - 1)
        return out.set(tr.xs[last] - tr.xs[first], tr.ys[last] - tr.ys[first])
    }

    /** [displacement] divided by the time it spans, in units per second; zero when no time elapsed. */
    fun velocity(name: String, window: Float, out: Vector2f = Vector2f()): Vector2f {
        val tr = tracks[name] ?: return out.zero()
        val first = firstInWindow(tr, window) ?: return out.zero()
        val last = index(tr, tr.size - 1)
        val dt = tr.t[last] - tr.t[first]
        if (dt <= 0f) return out.zero()
        return out.set((tr.xs[last] - tr.xs[first]) / dt, (tr.ys[last] - tr.ys[first]) / dt)
    }

    /** Visits the samples within [window] seconds of the newest one, oldest first. */
    fun forEachSample(name: String, window: Float, fn: (t: Float, x: Float, y: Float) -> Unit) {
        val tr = tracks[name] ?: return
        if (tr.size == 0) return
        val now = tr.t[index(tr, tr.size - 1)]
        for (k in 0 until tr.size) {
            val i = index(tr, k)
            if (now - tr.t[i] <= window) fn(tr.t[i], tr.xs[i], tr.ys[i])
        }
    }

    private fun index(tr: Track, k: Int) = (tr.start + k) % capacity

    /** Ring index of the oldest sample within the window (the oldest overall if none is), or null with < 2 samples. */
    private fun firstInWindow(tr: Track, window: Float): Int? {
        if (tr.size < 2) return null
        val now = tr.t[index(tr, tr.size - 1)]
        for (k in 0 until tr.size) {
            val i = index(tr, k)
            if (now - tr.t[i] <= window) return i
        }
        return index(tr, 0)
    }
}
