package com.pafoid.skate.engine.net

/** Buffers received object states per tick and samples them at a fractional render tick. */
class SnapshotInterpolator(private val schema: ReplicationSchema, private val maxSamples: Int = 16) {
    private class Sample(val tick: Int, val state: IntArray)

    private val buffers = HashMap<Int, ArrayDeque<Sample>>()

    fun add(netId: Int, tick: Int, state: IntArray) {
        val q = buffers.getOrPut(netId) { ArrayDeque() }
        if (q.isNotEmpty() && q.last().tick >= tick) return
        q.addLast(Sample(tick, state))
        while (q.size > maxSamples) q.removeFirst()
    }

    fun remove(netId: Int) { buffers.remove(netId) }

    /** Interpolated state at [renderTick]; clamps (holds) outside the buffered range. Null if nothing is buffered. */
    fun sample(netId: Int, renderTick: Double): IntArray? {
        val q = buffers[netId]?.takeIf { it.isNotEmpty() } ?: return null
        if (renderTick <= q.first().tick) return q.first().state
        if (renderTick >= q.last().tick) return q.last().state
        for (i in 1 until q.size) {
            val b = q[i]
            if (renderTick <= b.tick) {
                val a = q[i - 1]
                val t = ((renderTick - a.tick) / (b.tick - a.tick)).toFloat()
                return schema.blend(a.state, b.state, t)
            }
        }
        return q.last().state
    }
}

/**
 * Estimates the server tick on the client. It advances with local time and is kept within one snapshot
 * interval of the newest received snapshot, so rendering at `estimate - interpolationDelay` always lies inside
 * the buffered range (no extrapolation; objects freeze if snapshots stop).
 */
class ClientClock(private val config: NetConfig) {
    var latestSnapshotTick = 0
        private set
    var estimatedServerTick = 0.0
        private set

    fun onSnapshot(tick: Int) {
        if (tick <= latestSnapshotTick) return
        if (latestSnapshotTick == 0) estimatedServerTick = tick.toDouble()
        latestSnapshotTick = tick
        estimatedServerTick = maxOf(estimatedServerTick, tick - config.snapshotIntervalTicks.toDouble())
    }

    fun advance(dtSeconds: Float) {
        if (latestSnapshotTick == 0) return
        estimatedServerTick = minOf(estimatedServerTick + dtSeconds * config.tickRate, latestSnapshotTick + config.snapshotIntervalTicks.toDouble())
    }

    val renderTick: Double get() = estimatedServerTick - config.interpolationDelayTicks
}
