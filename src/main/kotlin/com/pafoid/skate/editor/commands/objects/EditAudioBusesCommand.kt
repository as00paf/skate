package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.settings.AudioSettings

/** Changes the project's mixer settings (volume, mute, bus low-pass, reverb send) and applies them to the live mixer. */
class EditAudioBusesCommand(
    private val read: () -> AudioSettings,
    private val write: (AudioSettings) -> Unit,
    settings: AudioSettings,
) : Command {
    private val before = read()
    private val after = settings.validate()

    val isNoOp: Boolean get() = before == after

    override fun execute() = write(after)
    override fun undo() = write(before)
    override fun getDisplayName(): String = "Edit Audio Mixer"
    override fun getTargetName(): String = "Mixer"
}
