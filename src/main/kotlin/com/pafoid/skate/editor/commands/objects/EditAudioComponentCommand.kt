package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.audio.AttenuationModel
import com.pafoid.skate.engine.audio.BusId
import com.pafoid.skate.engine.audio.StreamMode
import com.pafoid.skate.engine.ecs.components.AudioComponent

/** Every saved field of an [AudioComponent], edited as one value so the inspector commits one undoable step. */
data class AudioComponentSettings(
    val soundFilePath: String,
    val clips: List<String>,
    val bus: BusId,
    val volume: Float,
    val loops: Boolean,
    val playOnAwake: Boolean,
    val streamMode: StreamMode,
    val priority: Int,
    val is3D: Boolean,
    val attenuationModel: AttenuationModel,
    val minDistance: Float,
    val maxDistance: Float,
    val rolloff: Float,
    val dopplerFactor: Float,
) {
    /** Clamps to the ranges the audio system accepts. */
    fun validated(): AudioComponentSettings {
        val min = finite(minDistance, 1f).coerceAtLeast(0f)
        return copy(
            clips = clips.filter { it.isNotBlank() },
            volume = finite(volume, 1f).coerceIn(0f, 1f),
            priority = priority.coerceIn(0, MAX_PRIORITY),
            minDistance = min,
            maxDistance = finite(maxDistance, 50f).coerceAtLeast(min),
            rolloff = finite(rolloff, 1f).coerceAtLeast(0f),
            dopplerFactor = finite(dopplerFactor, 1f).coerceAtLeast(0f),
        )
    }

    fun applyTo(c: AudioComponent) {
        c.soundFilePath = soundFilePath; c.clips = clips; c.bus = bus; c.volume = volume; c.loops = loops
        c.playOnAwake = playOnAwake; c.streamMode = streamMode; c.priority = priority; c.is3D = is3D
        c.attenuationModel = attenuationModel; c.minDistance = minDistance; c.maxDistance = maxDistance
        c.rolloff = rolloff; c.dopplerFactor = dopplerFactor
    }

    companion object {
        const val MAX_PRIORITY = 255

        fun of(c: AudioComponent) = AudioComponentSettings(
            c.soundFilePath, c.clips.toList(), c.bus, c.volume, c.loops, c.playOnAwake, c.streamMode, c.priority,
            c.is3D, c.attenuationModel, c.minDistance, c.maxDistance, c.rolloff, c.dopplerFactor,
        )

        private fun finite(v: Float, fallback: Float) = if (v.isFinite()) v else fallback
    }
}

class EditAudioComponentCommand(private val component: AudioComponent, settings: AudioComponentSettings) : Command {
    private val before = AudioComponentSettings.of(component)
    private val after = settings.validated()

    val isNoOp: Boolean get() = before == after

    override fun execute() = after.applyTo(component)
    override fun undo() = before.applyTo(component)
    override fun getDisplayName(): String = "Edit Audio Source"
    override fun getTargetName(): String? = component.gameObject?.name
}
