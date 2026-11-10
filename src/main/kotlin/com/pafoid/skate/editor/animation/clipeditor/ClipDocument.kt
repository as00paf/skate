package com.pafoid.skate.editor.animation.clipeditor

import com.pafoid.skate.engine.animation.edit.SkClip
import java.io.File

/**
 * A `.skanim` clip open in the animation editor (ANI-14). Edits replace [clip] (through `ClipEditCommand`, or live
 * during a drag); [revision] grows on every change so views can cache what they derive from the clip.
 */
class ClipDocument(val file: File, initial: SkClip) {
    var clip: SkClip = initial; private set
    /** The clip as last read from or written to [file]. */
    var saved: SkClip = initial; private set
    var revision = 0L; private set

    val dirty: Boolean get() = clip != saved

    fun replace(next: SkClip) {
        if (next == clip) return
        clip = next
        revision++
    }

    fun markSaved(written: SkClip) { saved = written }
}
