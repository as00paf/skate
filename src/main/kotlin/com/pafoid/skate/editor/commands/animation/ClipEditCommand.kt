package com.pafoid.skate.editor.commands.animation

import com.pafoid.skate.editor.animation.clipeditor.ClipDocument
import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.commands.KeyedCommand
import com.pafoid.skate.engine.animation.edit.SkClip

/**
 * One edit of a clip in the animation editor (ANI-14): moving, scaling, keying, deleting or pasting keys, events and
 * clip properties. It swaps the whole [SkClip] value (clips are immutable and small), so every action undoes the same
 * way. A drag is previewed live and committed once on release, so it is one undo step. [action] names the edit
 * (`command.ClipEditCommand.<action>`).
 */
class ClipEditCommand(
    private val document: ClipDocument,
    private val before: SkClip,
    private val after: SkClip,
    val action: String,
) : Command, KeyedCommand {
    override fun execute() = document.replace(after)
    override fun undo() = document.replace(before)
    override fun getDisplayName(): String = action.replace('_', ' ').replaceFirstChar { it.uppercase() }
    override fun getTargetName(): String = document.file.name
    override val displayNameKey: String get() = "command.ClipEditCommand.$action"
}
