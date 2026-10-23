package com.pafoid.skate.engine.animation.graph

/** Named float/bool/trigger parameters driving transitions and blend trees. Gameplay writes, animation reads. */
class AnimParameters {
    private val floats = mutableMapOf<String, Float>()
    private val bools = mutableMapOf<String, Boolean>()
    private val triggers = mutableSetOf<String>()

    fun setFloat(name: String, value: Float) { floats[name] = value }
    fun getFloat(name: String): Float = floats[name] ?: 0f
    fun setBool(name: String, value: Boolean) { bools[name] = value }
    fun getBool(name: String): Boolean = bools[name] ?: false
    fun setTrigger(name: String) { triggers.add(name) }
    fun hasTrigger(name: String): Boolean = name in triggers
    fun consumeTrigger(name: String) { triggers.remove(name) }
}
