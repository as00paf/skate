package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event

/**
 * A long operation (create or open a project, open a scene, export) for the loading screen (EDT-56). Published on
 * the main thread only.
 */
sealed class LoadingEvent(name: String) : Event(name) {
    /** [titleKey] names the operation; [detail] is what it works on (project name, file name). */
    data class Started(val titleKey: String, val detail: String = "") : LoadingEvent("loading.started")

    /** [fraction] of the whole operation is done (0..1); [stepKey] is the string key of the current step. */
    data class Progressed(val fraction: Float, val stepKey: String, val stepDetail: String = "") : LoadingEvent("loading.progressed")

    data object Finished : LoadingEvent("loading.finished")
}
