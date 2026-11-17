package com.pafoid.skate.engine.ui

import java.util.concurrent.locks.LockSupport

/**
 * The player's frame-rate choice (GUI-17): a [GameOption] shown in the pause menu and saved in
 * [GameSettings.options] under [OPTION_ID]. [PROJECT] keeps the project's target FPS (AST-29); the other values
 * override it.
 */
object FrameRateOption {
    const val OPTION_ID = "engine.frameRate"
    const val PROJECT = "project"
    const val UNCAPPED = "uncapped"
    val VALUES = listOf(PROJECT, "30", "60", "120", "144", UNCAPPED)

    /** Runtime UI string key of the button for [value], e.g. `frameRate.60`. */
    fun labelKey(value: String): String = "frameRate.$value"

    /**
     * The cap for [choice] in frames per second, 0 meaning uncapped. [projectFps] is the project's own cap (already
     * normalized, 0 = uncapped); unknown choices fall back to it.
     */
    fun resolve(choice: String?, projectFps: Int): Int = when (choice) {
        UNCAPPED -> 0
        PROJECT, null -> projectFps.coerceAtLeast(0)
        else -> choice.toIntOrNull()?.takeIf { it > 0 } ?: projectFps.coerceAtLeast(0)
    }

    /** The saved choice in [settings], or [PROJECT] when none (or an unknown one) is saved. */
    fun saved(settings: GameSettings): String = settings.options[OPTION_ID]?.takeIf { it in VALUES } ?: PROJECT

    /** The pause menu option; choosing a value sets [limiter]'s cap from it and [projectFps]. */
    fun option(limiter: FrameLimiter, projectFps: Int): GameOption = GameOption(
        id = OPTION_ID,
        values = VALUES,
        default = PROJECT,
        labelKey = ::labelKey,
        apply = { limiter.targetFps = resolve(it, projectFps) },
    )
}

/**
 * Paces a render loop to [targetFps] (GUI-17): call [endFrame] once per frame and it waits out what is left of the
 * frame's time slot. 0 is uncapped. Frames that run late do not make later frames rush to catch up.
 */
class FrameLimiter(
    private val nanoTime: () -> Long = System::nanoTime,
    private val sleep: (Long) -> Unit = ::preciseSleep,
) {
    /** Frames per second, 0 (or less) uncapped. Changing it starts a new cadence on the next frame. */
    var targetFps: Int = 0
        set(value) {
            val fps = value.coerceAtLeast(0)
            if (fps != field) next = NONE
            field = fps
        }

    private var next = NONE

    /** Waits until the end of the current frame's slot; returns the nanoseconds it slept (0 when it did not). */
    fun endFrame(): Long {
        if (targetFps <= 0) {
            next = NONE
            return 0
        }
        val period = 1_000_000_000L / targetFps
        val now = nanoTime()
        if (next == NONE) {
            next = now + period
            return 0
        }
        val wait = next - now
        if (wait > 0) sleep(wait)
        // Within one period of the deadline keeps the cadence; further behind starts a new one from now.
        next = if (wait < -period) now + period else next + period
        return wait.coerceAtLeast(0)
    }

    private companion object {
        const val NONE = Long.MIN_VALUE
    }
}

/** Sleeps [nanos]: coarse sleeps (Windows timers are ~1-15 ms) then short parks until the deadline. */
fun preciseSleep(nanos: Long) {
    val deadline = System.nanoTime() + nanos
    while (true) {
        val left = deadline - System.nanoTime()
        if (left <= 0) return
        if (left > 2_000_000L) Thread.sleep(1) else LockSupport.parkNanos(left.coerceAtMost(100_000L))
    }
}
