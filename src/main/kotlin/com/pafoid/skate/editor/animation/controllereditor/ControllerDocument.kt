package com.pafoid.skate.editor.animation.controllereditor

import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import java.io.File

/**
 * A controller (`*.controller.json`) open in the Animator window (ANI-21 §5.1). Edits replace [asset] through
 * `ControllerEditCommand` (or live during a drag); [revision] grows on every change so views can cache what they
 * derive from it.
 *
 * [path] is how Animators refer to it (`Animator.controllerPath`). [file] is where it is saved; it is null for a
 * bundled engine default, which is [readOnly] and is edited through a project copy (§5.2).
 */
class ControllerDocument(val path: String, val file: File?, initial: AnimationControllerAsset) {
    var asset: AnimationControllerAsset = initial; private set
    /** The asset as last read from or written to [file]. */
    var saved: AnimationControllerAsset = initial; private set
    var revision = 0L; private set

    val readOnly: Boolean get() = file == null
    val dirty: Boolean get() = asset != saved
    val displayName: String get() = file?.name ?: path.substringAfterLast('/')

    fun replace(next: AnimationControllerAsset) {
        if (next == asset) return
        asset = next
        revision++
    }

    fun markSaved(written: AnimationControllerAsset) { saved = written }
}
