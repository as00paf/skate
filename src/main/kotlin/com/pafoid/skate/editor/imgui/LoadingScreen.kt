package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.events.LoadingEvent
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.loading.LoadingScreenState
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui

/**
 * The loading screen (EDT-56): a modal with the operation, a progress bar and the current step, fed by
 * [LoadingEvent]s while a project is created or opened, a scene opens, or the project exports. It blocks the editor
 * but not the UI thread: the work runs in steps (see `LoadingRunner`), so the screen keeps drawing. It has no
 * buttons and closes itself when the operation finishes.
 */
class LoadingScreen(
    eventSystem: EventSystem,
    strings: StringManager,
    private val clock: () -> Long = System::currentTimeMillis,
) : EditorModal(strings, Icons.HOURGLASS_HALF, minWidth = 420f) {
    val state = LoadingScreenState()

    /** The title of the popup that is open; kept after the operation ends so that popup can still close itself. */
    private var shownTitleKey = FALLBACK_TITLE

    init {
        // The event system dispatches on the exact class.
        eventSystem.subscribe<LoadingEvent.Started>(::onEvent)
        eventSystem.subscribe<LoadingEvent.Progressed>(::onEvent)
        eventSystem.subscribe<LoadingEvent.Finished>(::onEvent)
    }

    private fun onEvent(event: LoadingEvent) {
        state.apply(event, clock())
        state.titleKey?.let { shownTitleKey = it }
    }

    override val titleKey: String get() = shownTitleKey

    override val wantsOpen: Boolean get() = state.isVisible(clock())

    override fun content() {
        if (!state.isActive) {
            ImGui.closeCurrentPopup()
            return
        }
        if (state.detail.isNotBlank()) ImGui.text(state.detail)
        ImGui.spacing()
        ImGui.progressBar(state.fraction, -1f, 0f, "${(state.fraction * 100).toInt()}%")
        state.stepKey?.let { key ->
            val step = strings.getString(key)
            ImGui.textDisabled(if (state.stepDetail.isBlank()) step else "$step: ${state.stepDetail}")
        }
        ImGui.textDisabled(strings.getString("loading.elapsed", "%.1f".format(state.elapsedSeconds(clock()))))
    }

    override fun buttons(): List<FooterButton> = emptyList()

    private companion object {
        const val FALLBACK_TITLE = "loading.title"
    }
}
