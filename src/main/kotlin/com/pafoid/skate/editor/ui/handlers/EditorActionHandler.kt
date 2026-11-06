package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.loading.LoadingRunner
import com.pafoid.skate.editor.loading.StepExecutor
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
    /** One long operation at a time behind the loading screen, whichever handler starts it (EDT-56). */
    private val loading = LoadingRunner(engine.eventSystem, StepExecutor.Jobs(engine.jobSystem))
    val sceneActionHandler = SceneActionHandler(engine, projectManager, undoRedoManager, editorCamera, loading)
    val projectActionHandler =
        ProjectActionHandler(engine, projectManager, settingsManager, undoRedoManager, stringManager, loading)
    val environmentActionHandler = EnvironmentActionHandler(undoRedoManager, engine.eventSystem)
    val consoleActionHandler = ConsoleActionHandler(engine.eventSystem, engine.logger, undoRedoManager)
    val inputSettingsActionHandler = InputSettingsActionHandler(engine.eventSystem, projectManager, undoRedoManager)
    val scriptEditorActionHandler = ScriptEditorActionHandler(engine.eventSystem, undoRedoManager, engine.logger)
    val jointActionHandler = JointActionHandler(engine.eventSystem, undoRedoManager)
    val animatorActionHandler = AnimatorActionHandler(engine.eventSystem, undoRedoManager, engine.logger)
    val componentFieldActionHandler = ComponentFieldActionHandler(engine.eventSystem, undoRedoManager, engine.sceneManager)
    val transformActionHandler = TransformActionHandler(engine.eventSystem, undoRedoManager, engine.sceneManager)
    val audioActionHandler = AudioActionHandler(engine.eventSystem, projectManager, undoRedoManager, engine.audioEngine)
    val materialActionHandler = MaterialActionHandler(
        engine.eventSystem, undoRedoManager, engine.logger,
        projectDir = { projectManager.currentProject?.getProjectFile()?.absoluteFile?.parentFile },
        resolveTexture = engine.assetsManager::getTexture,
        dialogs = TinyMaterialFileDialogs(engine.stringManager),
    )
    val keyBindingsActionHandler = KeyBindingsActionHandler(
        engine.eventSystem, projectManager, undoRedoManager
    ) { engine.systemManager.getSystem<com.pafoid.skate.engine.ecs.systems.InputSystem>()?.actionMap }
}