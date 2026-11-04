package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.gizmos.EditorCamera
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
    editorCamera: EditorCamera
) {
    val sceneActionHandler = SceneActionHandler(engine, projectManager, undoRedoManager, editorCamera)
    val projectActionHandler =
        ProjectActionHandler(engine, projectManager, settingsManager, undoRedoManager, stringManager)
    val environmentActionHandler = EnvironmentActionHandler(undoRedoManager, engine.eventSystem)
    val consoleActionHandler = ConsoleActionHandler(engine.eventSystem, engine.logger, undoRedoManager)
    val inputSettingsActionHandler = InputSettingsActionHandler(engine.eventSystem, projectManager, undoRedoManager)
    val scriptEditorActionHandler = ScriptEditorActionHandler(engine.eventSystem, undoRedoManager, engine.logger)
    val jointActionHandler = JointActionHandler(engine.eventSystem, undoRedoManager)
    val audioActionHandler = AudioActionHandler(engine.eventSystem, projectManager, undoRedoManager, engine.audioEngine)
    val keyBindingsActionHandler = KeyBindingsActionHandler(
        engine.eventSystem, projectManager, undoRedoManager
    ) { engine.systemManager.getSystem<com.pafoid.skate.engine.ecs.systems.InputSystem>()?.actionMap }
}