package com.pafoid.skate.engine.render.state

/**
 * Reusable list of draw items ordered by [SortKey]. [sort] is stable, so equal keys keep submission
 * order (e.g. transparent items stay in scene order). Entries are pooled to avoid per-frame allocation.
 */
class DrawQueue<T : Any> {
    private class Entry<T> {
        var key = 0L
        var item: T? = null
    }

    private val pool = ArrayList<Entry<T>>()
    private val active = ArrayList<Entry<T>>()

    val size: Int get() = active.size

    fun add(key: Long, item: T) {
        val entry = if (active.size < pool.size) pool[active.size] else Entry<T>().also { pool.add(it) }
        entry.key = key
        entry.item = item
        active.add(entry)
    }

    fun sort() {
        active.sortWith(compareBy { it.key })
    }

    fun keyAt(index: Int): Long = active[index].key

    @Suppress("UNCHECKED_CAST")
    fun itemAt(index: Int): T = active[index].item as T

    fun clear() {
        for (e in active) e.item = null
        active.clear()
    }
}
