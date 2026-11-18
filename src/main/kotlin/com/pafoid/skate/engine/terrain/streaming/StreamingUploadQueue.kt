package com.pafoid.skate.engine.terrain.streaming

/**
 * Main-thread upload budget (REN-28 §15.5): items are drained in order while the frame's time ([timeBudgetNs],
 * measured with [clock]) and byte ([byteBudget]) budgets last. The first item of a frame always runs, so a large
 * upload can't stall forever. Items left over wait for the next frame.
 */
class StreamingUploadQueue<T : Any>(
    var timeBudgetNs: Long = 1_500_000L,
    var byteBudget: Long = 4L * 1024 * 1024,
    private val clock: () -> Long = System::nanoTime,
) {
    private class Item<T>(val value: T, val bytes: Long)

    private val items = ArrayDeque<Item<T>>()

    val size: Int get() = items.size

    /** Nanoseconds spent by the last [drain]. */
    var lastDrainNs = 0L
        private set
    var lastDrained = 0
        private set

    fun add(value: T, bytes: Long) { items.addLast(Item(value, bytes)) }

    fun contains(value: T): Boolean = items.any { it.value === value }

    fun removeIf(predicate: (T) -> Boolean) { items.removeAll { predicate(it.value) } }

    fun clear() = items.clear()

    /** Runs [upload] on queued items within the budgets; returns how many ran. */
    fun drain(upload: (T) -> Unit): Int {
        val start = clock()
        var bytes = 0L
        var done = 0
        while (items.isNotEmpty()) {
            val next = items.first()
            if (done > 0 && (clock() - start >= timeBudgetNs || bytes + next.bytes > byteBudget)) break
            items.removeFirst()
            upload(next.value)
            bytes += next.bytes
            done++
        }
        lastDrainNs = clock() - start
        lastDrained = done
        return done
    }
}
