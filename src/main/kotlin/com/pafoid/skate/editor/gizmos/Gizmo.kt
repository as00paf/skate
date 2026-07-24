package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.input.InputProvider

open class Gizmo(
    protected val inputProvider: InputProvider,
    protected val undoRedoManager: UndoRedoManager,
) {

    protected var xAxisActive = false
    protected var yAxisActive = false
    protected var zAxisActive = false

    var inUse = false
}
