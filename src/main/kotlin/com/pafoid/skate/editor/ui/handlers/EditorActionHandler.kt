package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager

class EditorActionHandler(
    engine: Engine,
    undoRedoManager: UndoRedoManager,
    projectManager: ProjectManager,
    stringManager: StringManager,
    settingsManager: EditorSettingsManager,
) {
    val sceneActionHandler = SceneActionHandler(engine, projectManager, undoRedoManager)
    val projectActionHandler =
        ProjectActionHandler(engine, projectManager, settingsManager, undoRedoManager, stringManager)
    val environmentActionHandler = EnvironmentActionHandler(undoRedoManager, engine.eventSystem)
    val consoleActionHandler = ConsoleActionHandler(engine.eventSystem, engine.logger, undoRedoManager)
}