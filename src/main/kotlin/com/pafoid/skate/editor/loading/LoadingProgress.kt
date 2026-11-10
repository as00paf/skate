package com.pafoid.skate.editor.loading

import com.pafoid.skate.editor.events.LoadingEvent

/** One step of a long operation: [weight] is its share of the progress bar, relative to the other steps. */
class LoadingStep(val labelKey: String, val weight: Float = 1f) {
    init {
        require(weight > 0f) { "weight must be > 0" }
    }
}

/** The progress of a sequence of weighted steps (EDT-56). */
object LoadingProgress {
    /** Overall fraction (0..1) when [stepIndex] is running and [stepFraction] of it is done. */
    fun overall(steps: List<LoadingStep>, stepIndex: Int, stepFraction: Float = 0f): Float {
        if (steps.isEmpty()) return 1f
        if (stepIndex >= steps.size) return 1f
        val total = steps.sumOf { it.weight.toDouble() }.toFloat()
        val done = steps.take(stepIndex.coerceAtLeast(0)).sumOf { it.weight.toDouble() }.toFloat()
        val current = steps[stepIndex.coerceAtLeast(0)].weight * stepFraction.coerceIn(0f, 1f)
        return ((done + current) / total).coerceIn(0f, 1f)
    }
}

/**
 * What the loading screen shows (EDT-56), built from [LoadingEvent]s. Main thread only. A short operation does not
 * flash a modal: the screen shows only once the operation has run for [showDelayMs].
 */
class LoadingScreenState(private val showDelayMs: Long = SHOW_DELAY_MS) {
    var titleKey: String? = null
        private set
    var detail: String = ""
        private set
    var fraction: Float = 0f
        private set
    var stepKey: String? = null
        private set
    var stepDetail: String = ""
        private set
    private var startedAtMs = 0L

    val isActive: Boolean get() = titleKey != null

    fun apply(event: LoadingEvent, nowMs: Long) {
        when (event) {
            is LoadingEvent.Started -> {
                titleKey = event.titleKey
                detail = event.detail
                fraction = 0f
                stepKey = null
                stepDetail = ""
                startedAtMs = nowMs
            }
            is LoadingEvent.Progressed -> if (isActive) {
                // Never moves back: a late progress report from an earlier step must not shrink the bar.
                fraction = maxOf(fraction, event.fraction.coerceIn(0f, 1f))
                stepKey = event.stepKey
                stepDetail = event.stepDetail
            }
            LoadingEvent.Finished -> titleKey = null
        }
    }

    fun isVisible(nowMs: Long): Boolean = isActive && nowMs - startedAtMs >= showDelayMs

    fun elapsedSeconds(nowMs: Long): Float = if (isActive) (nowMs - startedAtMs) / 1000f else 0f

    companion object {
        const val SHOW_DELAY_MS = 200L
    }
}
