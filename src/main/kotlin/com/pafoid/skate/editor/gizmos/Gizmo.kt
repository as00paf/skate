package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.input.listeners.MouseListener
import org.koin.core.component.KoinComponent

open class Gizmo(
    protected val mouseListener: MouseListener,
    protected val undoRedoManager: UndoRedoManager,
) : KoinComponent {

    protected var xAxisActive = false
    protected var yAxisActive = false
    protected var zAxisActive = false

    var inUse = false
}
