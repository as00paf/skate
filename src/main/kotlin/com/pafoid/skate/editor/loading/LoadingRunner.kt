package com.pafoid.skate.editor.loading

import com.pafoid.skate.editor.events.LoadingEvent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.JobSystem

/** Where a step runs: [BACKGROUND] for file and CPU work, [MAIN] for anything touching GL, AL, the scene or the UI. */
enum class StepThread { BACKGROUND, MAIN }

/** A step of a [LoadingOperation]: [run] does the work on [thread]. */
class OperationStep(val step: LoadingStep, val thread: StepThread, val run: () -> Unit)

/**
 * A long operation shown on the loading screen (EDT-56). Steps run in order; values flow between them through the
 * caller's closures (each step starts after the previous one has finished, so they see its writes).
 */
class LoadingOperation(val titleKey: String, val detail: String, val steps: List<OperationStep>) {
    class Builder(private val titleKey: String, private val detail: String) {
        private val steps = mutableListOf<OperationStep>()

        fun background(labelKey: String, weight: Float = 1f, run: () -> Unit) = apply {
            steps += OperationStep(LoadingStep(labelKey, weight), StepThread.BACKGROUND, run)
        }

        fun main(labelKey: String, weight: Float = 1f, run: () -> Unit) = apply {
            steps += OperationStep(LoadingStep(labelKey, weight), StepThread.MAIN, run)
        }

        fun build() = LoadingOperation(titleKey, detail, steps.toList())
    }
}

/** Runs blocks on a background thread or on the editor's main thread (the frame loop). */
interface StepExecutor {
    fun background(block: () -> Unit)
    fun main(block: () -> Unit)

    /** Background work on the IO pool; main-thread work in the next `JobSystem.update`. */
    class Jobs(private val jobs: JobSystem) : StepExecutor {
        override fun background(block: () -> Unit) {
            jobs.runIO { block() }
        }

        override fun main(block: () -> Unit) {
            jobs.runOnMain { block() }
        }
    }
}

/**
 * Runs [LoadingOperation]s one at a time and reports them as [LoadingEvent]s (EDT-56). Background steps leave the UI
 * thread free, so the loading screen keeps drawing. Call [run] and [publish] on the main thread; every event and
 * callback is delivered there.
 */
class LoadingRunner(private val events: EventSystem, private val executor: StepExecutor) {
    /** True from [run] until the operation's last callback (or [track]'s finish). */
    var isRunning = false
        private set

    /**
     * Starts [operation]. Returns false (and runs nothing) while another operation is running. [onDone] runs after
     * the last step; [onFailed] runs instead when a step throws, and later steps are skipped.
     */
    fun run(operation: LoadingOperation, onDone: () -> Unit = {}, onFailed: (Throwable) -> Unit = {}): Boolean {
        if (isRunning) return false
        isRunning = true
        events.publish(LoadingEvent.Started(operation.titleKey, operation.detail))
        runStep(operation, 0, onDone, onFailed)
        return true
    }

    private fun runStep(operation: LoadingOperation, index: Int, onDone: () -> Unit, onFailed: (Throwable) -> Unit) {
        val steps = operation.steps
        if (index >= steps.size) {
            finish()
            onDone()
            return
        }
        val step = steps[index]
        events.publish(LoadingEvent.Progressed(LoadingProgress.overall(steps.map { it.step }, index), step.step.labelKey))
        val body = {
            val error = try {
                step.run()
                null
            } catch (t: Throwable) {
                t
            }
            executor.main {
                if (error != null) {
                    finish()
                    onFailed(error)
                } else {
                    runStep(operation, index + 1, onDone, onFailed)
                }
            }
        }
        when (step.thread) {
            StepThread.BACKGROUND -> executor.background(body)
            StepThread.MAIN -> executor.main(body)
        }
    }

    /**
     * Shows an operation that reports its own progress (export runs its own background job). Returns null while
     * another operation is running; otherwise a [Tracker] to report progress and finish, from the main thread.
     */
    fun track(titleKey: String, detail: String = ""): Tracker? {
        if (isRunning) return null
        isRunning = true
        events.publish(LoadingEvent.Started(titleKey, detail))
        return Tracker()
    }

    inner class Tracker internal constructor() {
        private var open = true

        fun progress(fraction: Float, stepKey: String, stepDetail: String = "") {
            if (open) events.publish(LoadingEvent.Progressed(fraction, stepKey, stepDetail))
        }

        fun finish() {
            if (!open) return
            open = false
            this@LoadingRunner.finish()
        }
    }

    private fun finish() {
        isRunning = false
        events.publish(LoadingEvent.Finished)
    }
}
