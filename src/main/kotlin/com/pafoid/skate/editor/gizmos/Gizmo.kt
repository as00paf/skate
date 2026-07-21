package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.input.listeners.MouseListener

open class Gizmo(
    protected val mouseListener: MouseListener,
    protected val undoRedoManager: UndoRedoManager,
) {

    protected var xAxisActive = false
    protected var yAxisActive = false
    protected var zAxisActive = false

    var inUse = false
}
