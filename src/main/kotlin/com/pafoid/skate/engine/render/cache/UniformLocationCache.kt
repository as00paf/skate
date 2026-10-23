package com.pafoid.skate.engine.render.cache

/** Caches uniform locations for one shader program; [lookup] is the (GL) query, called once per name. */
class UniformLocationCache(private val lookup: (String) -> Int) {
    private val locations = HashMap<String, Int>()

    val size: Int get() = locations.size

    fun get(name: String): Int = locations.getOrPut(name) { lookup(name) }

    fun clear() = locations.clear()
}
