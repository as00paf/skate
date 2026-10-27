package com.pafoid.skate.engine.input

/** Sends motor speeds (0..1) to a device. GLFW has no haptics API, so a backend must come from elsewhere. */
interface RumbleBackend {
    val supported: Boolean
    fun setRumble(pad: Int, strong: Float, weak: Float)
}

object NoOpRumbleBackend : RumbleBackend {
    override val supported = false
    override fun setRumble(pad: Int, strong: Float, weak: Float) = Unit
}

/** Timed rumble effects per pad on top of a [RumbleBackend]; call [update] once per frame. */
class RumbleManager(var backend: RumbleBackend = NoOpRumbleBackend, private val maxPads: Int = 16) {
    private class Effect(var strong: Float, var weak: Float, var remaining: Float)

    private val effects = arrayOfNulls<Effect>(maxPads)
    private val sent = Array(maxPads) { floatArrayOf(0f, 0f) }

    val supported: Boolean get() = backend.supported

    /** Starts (or replaces) the effect on [pad]. A non-positive [duration] is ignored. */
    fun play(pad: Int, strong: Float, weak: Float = strong, duration: Float = 0.2f) {
        if (pad !in effects.indices || duration <= 0f) return
        effects[pad] = Effect(strong.coerceIn(0f, 1f), weak.coerceIn(0f, 1f), duration)
    }

    fun stop(pad: Int) {
        if (pad in effects.indices) effects[pad] = null
        flush(pad)
    }

    fun stopAll() = effects.indices.forEach(::stop)

    fun update(dt: Float) {
        for (pad in effects.indices) {
            val e = effects[pad]
            if (e != null) {
                e.remaining -= dt
                if (e.remaining <= 0f) effects[pad] = null
            }
            flush(pad)
        }
    }

    /** Sends only changes, so an idle backend is never called. */
    private fun flush(pad: Int) {
        val e = effects[pad]
        val strong = e?.strong ?: 0f
        val weak = e?.weak ?: 0f
        if (sent[pad][0] == strong && sent[pad][1] == weak) return
        sent[pad][0] = strong; sent[pad][1] = weak
        backend.setRumble(pad, strong, weak)
    }
}
