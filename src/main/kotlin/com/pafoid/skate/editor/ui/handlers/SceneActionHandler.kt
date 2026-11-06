package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.events.EditorError
import com.pafoid.skate.editor.commands.project.CloseAllScenesCommand
import com.pafoid.skate.editor.commands.project.CloseOtherScenesCommand
import com.pafoid.skate.editor.commands.project.CloseSceneCommand
import com.pafoid.skate.editor.commands.project.CreateSceneCommand
import com.pafoid.skate.editor.commands.project.DeleteSceneCommand
import com.pafoid.skate.editor.commands.project.OpenSceneFileCommand
import com.pafoid.skate.editor.commands.project.RenameSceneCommand
import com.pafoid.skate.editor.commands.project.ReopenAllScenesCommand
import com.pafoid.skate.editor.commands.project.SaveSceneAsCommand
import com.pafoid.skate.editor.commands.project.SaveSceneCommand
import com.pafoid.skate.editor.commands.scene.SwitchSceneCommand
import com.pafoid.skate.editor.events.ViewportAction.TabSelected
import com.pafoid.skate.editor.events.PlayModeAction
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.gizmos.EditorCameraMemory
import com.pafoid.skate.editor.systems.PlayModeGuards
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.assets.ProjectLayout
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.events.SceneAction
import com.pafoid.skate.engine.events.SceneAction.CloseAllRequested
import com.pafoid.skate.engine.events.SceneAction.CloseOthersRequested
import com.pafoid.skate.engine.events.SceneAction.CloseRequested
import com.pafoid.skate.engine.events.SceneAction.CreateRequested
import com.pafoid.skate.engine.events.SceneAction.Created
import com.pafoid.skate.engine.events.SceneAction.DeleteRequested
import com.pafoid.skate.engine.events.SceneAction.OpenCancelled
import com.pafoid.skate.engine.events.SceneAction.OpenFailed
import com.pafoid.skate.engine.events.SceneAction.RenameRequested
import com.pafoid.skate.engine.events.SceneAction.SaveAsRequested
import com.pafoid.skate.engine.events.SceneAction.SaveRequested
import java.io.File

class SceneActionHandler(
    private val engine: Engine,
    private val projectManager: ProjectManager,
    private val undoRedoManager: UndoRedoManager,
    private val editorCamera: EditorCamera
    ) {
    private val logger = engine.logger
    private val eventSystem = engine.eventSystem
    private val sceneManager = engine.sceneManager
    private val serializer = engine.serializer

    /** Each scene's editor camera, kept in the project's editor-only data (EDT-58). */
    val cameraMemory = EditorCameraMemory(
        camera = { editorCamera.camera },
        transform = { editorCamera.transform },
        projectDir = { projectManager.getProjectDirectory() },
        warn = { logger.logEditor(it, LogLevel.WARN) },
    )

    init {
        eventSystem.subscribe<RenameRequested> { event ->
            handleRenameRequested(event.scene, event.newName)
        }
        eventSystem.subscribe<SaveRequested> { event ->
            handleSaveRequested(event.scene)
        }
        eventSystem.subscribe<SaveAsRequested> { event ->
            handleSaveAsRequested(event.scene)
        }
        eventSystem.subscribe<CloseRequested> { event ->
            handleCloseRequested(event.scene)
        }
        eventSystem.subscribe<CloseOthersRequested> { event ->
            handleCloseOthersRequested(event.keepScene)
        }
        eventSystem.subscribe<CloseAllRequested> {
            handleCloseAllRequested()
        }
        eventSystem.subscribe<SceneAction.ReopenAllRequested> { event ->
            handleReopenAllRequested(event.scenes)
        }
        eventSystem.subscribe<CreateRequested> {
            userSceneChange { handleCreateRequested() }
        }
        eventSystem.subscribe<Created> { event ->
            handleSceneCreated(event.scene)
        }
        eventSystem.subscribe<TabSelected> { event ->
            userSceneChange(event.scene) { target -> handleTabSelected(target) }
        }
        eventSystem.subscribe<DeleteRequested> { event ->
            handleDeleteRequested(event.scene)
        }
        eventSystem.subscribe<SceneAction.OpenSceneFile> { event ->
            userSceneChange { handleOpenSceneFile(event.sceneFile) }
        }
        eventSystem.subscribe<SceneAction.Opened> { event ->
            handleOpenSucceeded(event.scene)
        }
        eventSystem.subscribe<OpenCancelled> {
            handleOpenCancelled()
        }
        eventSystem.subscribe<OpenFailed> { event ->
            handleOpenFailed(event.reason)
        }
        eventSystem.subscribe<SceneAction.Changed> { event ->
            if (sceneManager.currentScene?.isRunning == false) {
                engine.cameraManager.camera = editorCamera.camera
                engine.cameraManager.transform = editorCamera.transform
            }
            // Play's reset publishes Changed for a copy of the same scene: same name, so the camera stays put.
            cameraMemory.switchTo(event.scene.name)
        }
        eventSystem.subscribe<SceneAction.Closing> { event ->
            cameraMemory.onClosing(event.scene.name)
        }
        eventSystem.subscribe<ProjectEvent.Closed> {
            cameraMemory.reset()
        }
    }

    /**
     * A scene change the user asked for (tab, Open Scene, Project double-click, New Scene). During Play it stops Play
     * first, restoring every scene (EDT-63); scene switches made by game scripts do not come through here and keep
     * playing.
     */
    private fun userSceneChange(change: () -> Unit) {
        if (engine.runtimePlaying) eventSystem.publish(PlayModeAction.StopThen(change)) else change()
    }

    /**
     * Same for switching to the open [scene]. Stopping replaces every open scene with its restored copy, so the target
     * is found again afterwards: the same object if it survived, else the open scene with its name.
     */
    private fun userSceneChange(scene: Scene, switch: (Scene) -> Unit) = userSceneChange {
        val target = sceneManager.openScenes.firstOrNull { it === scene }
            ?: sceneManager.openScenes.firstOrNull { it.name == scene.name }
        if (target != null && target !== sceneManager.currentScene) switch(target)
    }

    private fun handleOpenSceneFile(sceneFile: File) {
        val command = OpenSceneFileCommand(sceneFile, serializer, sceneManager, eventSystem)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene file opened: ${sceneFile.name}")
    }

    private fun handleRenameRequested(scene: Scene, newName: String) {
        val oldName = scene.name
        if (newName.isBlank() || newName == oldName) return

        val command = RenameSceneCommand(scene, newName, oldName, projectManager, sceneManager, eventSystem)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene rename requested: '$oldName' -> '$newName'")
    }

    private fun handleSaveRequested(scene: Scene) {
        if (!PlayModeGuards.canSaveScene(engine.runtimePlaying)) {
            logger.logEditor("Scene save skipped: stop Play first", LogLevel.WARN)
            return
        }
        val command = SaveSceneCommand(scene, projectManager, sceneManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene save requested: ${scene.name}")
    }

    private fun handleSaveAsRequested(scene: Scene) {
        if (!PlayModeGuards.canSaveScene(engine.runtimePlaying)) {
            logger.logEditor("Scene save-as skipped: stop Play first", LogLevel.WARN)
            return
        }
        val command = SaveSceneAsCommand(
            scene, serializer, logger,
            dialogTitle = engine.stringManager.getString("dialog.scene.save_as_title"),
            filterDescription = engine.stringManager.getString("dialog.scene.filter"),
        )
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene save-as requested: ${scene.name}")
    }

    private fun handleCloseRequested(scene: Scene) {
        val command = CloseSceneCommand(scene, sceneManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene close requested: ${scene.name}")
    }

    private fun handleCloseOthersRequested(keepScene: Scene) {
        val command = CloseOtherScenesCommand(keepScene, sceneManager, eventSystem)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Close other scenes requested, keeping ${keepScene.name}")
    }

    private fun handleCloseAllRequested() {
        val command = CloseAllScenesCommand(sceneManager, eventSystem)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Close all scenes requested")
    }

    private fun handleReopenAllRequested(scenes: List<Scene>) {
        val command = ReopenAllScenesCommand(sceneManager, eventSystem, scenes)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Reopen all scenes requested")
    }

    private fun handleCreateRequested() {
        val projectDir = projectManager.getProjectDirectory()
        if (projectDir == null) {
            logger.logEditor("Cannot create scene: no project directory", LogLevel.WARN)
            return
        }

        val fullPath = generateUniqueScenePath(projectDir)
        val sceneName = File(fullPath).nameWithoutExtension

        val command = CreateSceneCommand(sceneName, fullPath, eventSystem, sceneManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene create requested: $sceneName -> $fullPath")
    }

    private fun handleSceneCreated(scene: Scene) {
        sceneManager.openScene(scene)
        logger.logEditor("New scene created and saved: ${scene.name}")
    }

    private fun handleOpenSucceeded(scene: Scene) {
        editorCamera.init(scene)
        // init copies the scene camera's projection; a remembered editor camera wins (EDT-58).
        cameraMemory.restore(scene.name)
        logger.logEditor("Scene opened from file: ${scene.name}")
    }

    private fun handleOpenCancelled() {
        logger.logEditor("Scene open cancelled")
    }

    private fun handleOpenFailed(reason: String) {
        logger.logEditor("Scene open failed: $reason", LogLevel.ERROR)
        eventSystem.publish(EditorError("error.open_scene.title", reason))
    }

    private fun generateUniqueScenePath(projectDir: File): String {
        val scenesDir = ProjectLayout.scenesDir(projectDir)
        scenesDir.mkdirs()

        var counter = 1
        var candidate = File(scenesDir, "NewScene_$counter.scene")
        while (candidate.exists()) {
            counter++
            candidate = File(scenesDir, "NewScene_$counter.scene")
        }
        return candidate.absolutePath
    }

    private fun handleTabSelected(scene: Scene) {
        val command = SwitchSceneCommand(scene, sceneManager)
        undoRedoManager.executeCommand(command)
    }

    private fun handleDeleteRequested(scene: Scene) {
        // Cannot delete if it's the only scene
        if (sceneManager.openScenes.size <= 1) {
            logger.logEditor("Cannot delete the last remaining scene")
            return
        }
        val command = DeleteSceneCommand(scene, projectManager, sceneManager, logger)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene delete requested: ${scene.name}")
    }
}
