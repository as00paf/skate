package com.pafoid.skate.editor.systems

/** Where the editor's simulation is (EDT-45). */
enum class PlayMode { EDIT, PLAYING, PAUSED }

/**
 * The editor's Play/Pause/Stop/Reset state machine (EDT-45).
 *
 * - **Play** (Edit): captures the scene, then starts the simulation. From Paused it resumes.
 * - **Pause** (Playing): freezes simulation, animation and audio; the scene keeps its current state.
 * - **Stop** (Playing or Paused): ends the simulation and restores the pre-play state.
 * - **Reset** (Playing or Paused): restores the pre-play state and stays in the same mode.
 *
 * The mode is read from the [Host] (the engine), so a pause made elsewhere (the in-game pause menu) shows as Paused.
 */
class PlayModeController(private val host: Host) {

    /** A captured pre-play scene. */
    fun interface Snapshot {
        /** Puts the scene back to the captured state. */
        fun restore()
    }

    interface Host {
        val isPlaying: Boolean
        val isPaused: Boolean
        fun setRuntimePlaying(playing: Boolean)
        fun setRuntimePaused(paused: Boolean)

        /** Captures the current scene; null when no scene is open. */
        fun capture(): Snapshot?
    }

    private var snapshot: Snapshot? = null

    val mode: PlayMode
        get() = when {
            !host.isPlaying -> PlayMode.EDIT
            host.isPaused -> PlayMode.PAUSED
            else -> PlayMode.PLAYING
        }

    val canReset: Boolean get() = mode != PlayMode.EDIT && snapshot != null

    fun play() {
        when (mode) {
            PlayMode.EDIT -> {
                snapshot = host.capture() ?: return
                host.setRuntimePlaying(true)
            }
            PlayMode.PAUSED -> host.setRuntimePaused(false)
            PlayMode.PLAYING -> Unit
        }
    }

    fun pause() {
        if (mode == PlayMode.PLAYING) host.setRuntimePaused(true)
    }

    /** Pause while playing, resume while paused; nothing in Edit. */
    fun togglePause() {
        when (mode) {
            PlayMode.PLAYING -> pause()
            PlayMode.PAUSED -> play()
            PlayMode.EDIT -> Unit
        }
    }

    /**
     * Back to Edit. [restore] puts the pre-play state back; it is false only when the scenes are about to be thrown
     * away (quitting the editor).
     */
    fun stop(restore: Boolean = true) {
        val saved = snapshot
        snapshot = null
        if (mode == PlayMode.EDIT && saved == null) return
        host.setRuntimePaused(false)
        host.setRuntimePlaying(false)
        if (restore) saved?.restore()
    }

    /** Play from Edit, Stop otherwise (the Play shortcut). */
    fun togglePlay() = if (mode == PlayMode.EDIT) play() else stop()

    /** Restores the pre-play state without leaving Play; a paused scene stays paused. */
    fun reset() {
        if (!canReset) return
        val wasPaused = mode == PlayMode.PAUSED
        snapshot?.restore()
        // Re-enter Play on the restored scene: its own camera, and loading a scene clears the pause.
        host.setRuntimePlaying(true)
        host.setRuntimePaused(wasPaused)
    }
}
