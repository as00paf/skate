package com.pafoid.skate.engine.physics3d.bullet

/** Open-addressing set of non-zero longs that never boxes and reuses its storage after [clear]. */
internal class LongSet(initialCapacity: Int = 16) {
    @PublishedApi internal var table = LongArray(initialCapacity)
    var size = 0
        private set

    fun contains(key: Long): Boolean {
        require(key != 0L) { "0 is reserved" }
        var i = slot(key)
        while (table[i] != 0L) {
            if (table[i] == key) return true
            i = (i + 1) and (table.size - 1)
        }
        return false
    }

    /** Returns true when [key] was not present. */
    fun add(key: Long): Boolean {
        require(key != 0L) { "0 is reserved" }
        if ((size + 1) * 2 > table.size) grow()
        var i = slot(key)
        while (table[i] != 0L) {
            if (table[i] == key) return false
            i = (i + 1) and (table.size - 1)
        }
        table[i] = key
        size++
        return true
    }

    fun clear() {
        table.fill(0L)
        size = 0
    }

    inline fun forEach(action: (Long) -> Unit) {
        for (key in table) if (key != 0L) action(key)
    }

    /** Keeps only keys matching [predicate]; allocates, so only use it on rare paths. */
    fun retainWhere(predicate: (Long) -> Boolean) {
        val kept = table.filter { it != 0L && predicate(it) }
        clear()
        kept.forEach { add(it) }
    }

    private fun slot(key: Long): Int = ((key * -0x61c8864680b583ebL) ushr 32).toInt() and (table.size - 1)

    private fun grow() {
        val old = table
        table = LongArray(old.size * 2)
        size = 0
        for (key in old) if (key != 0L) add(key)
    }
}
