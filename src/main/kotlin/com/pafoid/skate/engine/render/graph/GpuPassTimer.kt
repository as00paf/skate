package com.pafoid.skate.engine.render.graph

import com.pafoid.skate.engine.render.rhi.opengl.GlTimerQueries

/** The timer-query calls [GpuTimerRing] needs; injectable so the ring is testable without a context. */
interface GpuQueryApi {
    fun create(): Int
    fun begin(query: Int)
    fun end()
    fun isAvailable(query: Int): Boolean
    fun resultNs(query: Int): Long
    fun delete(query: Int)

    companion object {
        /** GL 3.3 `GL_TIME_ELAPSED` queries, or null when the context has no timer queries. */
        fun openGl(): GpuQueryApi? = GlTimerQueries.create()
    }
}

/**
 * GPU time of one render pass, measured with a ring of [depth] timer queries so results are read a few frames
 * later without stalling the pipeline (REN-22). [latestNs] is -1 until the first result arrives.
 */
class GpuTimerRing(private val api: GpuQueryApi, private val depth: Int = 3) {
    private val queries = IntArray(depth)
    private val pending = BooleanArray(depth)
    private val issuedFrame = LongArray(depth)
    private var lastReadFrame = -1L

    var latestNs: Long = -1
        private set

    fun begin(frame: Long) {
        val slot = (frame % depth).toInt()
        if (queries[slot] == 0) queries[slot] = api.create()
        // A still-pending slot is overwritten: its result is dropped rather than waited for
        pending[slot] = false
        issuedFrame[slot] = frame
        api.begin(queries[slot])
    }

    fun end(frame: Long) {
        api.end()
        pending[(frame % depth).toInt()] = true
    }

    /** Reads every finished query (never blocks) and keeps the newest frame's time in [latestNs]. */
    fun collect() {
        for (slot in 0 until depth) {
            if (!pending[slot] || !api.isAvailable(queries[slot])) continue
            pending[slot] = false
            if (issuedFrame[slot] > lastReadFrame) {
                lastReadFrame = issuedFrame[slot]
                latestNs = api.resultNs(queries[slot])
            }
        }
    }

    fun destroy() {
        for (i in queries.indices) if (queries[i] != 0) api.delete(queries[i])
        queries.fill(0)
        pending.fill(false)
    }
}
