package com.pafoid.skate.editor.ui.handlers

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
import com.pafoid.skate.editor.systems.EditorMutationGate
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
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
import com.pafoid.skate.engine.events.SceneAction.OpenSucceeded
import com.pafoid.skate.engine.events.SceneAction.RenameRequested
import com.pafoid.skate.engine.events.SceneAction.SaveAsRequested
import com.pafoid.skate.engine.events.SceneAction.SaveRequested
import java.io.File

/**
 * Handles [SceneAction] events by executing the appropriate commands
 * or calling SceneManager/SceneSerializer methods.
 * 
 * This decouples UI components (like EditorScenesTabBar) from orchestration logic.
 */
class SceneActionHandler(
    private val engine: Engine,
    private val projectManager: ProjectManager,
    private val undoRedoManager: UndoRedoManager,
    private val mutationGate: EditorMutationGate,

    ) {
    private val logger = engine.logger
    private val eventSystem = engine.eventSystem
    private val sceneManager = engine.sceneManager
    private val serializer = engine.serializer

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
            handleCreateRequested()
        }
        eventSystem.subscribe<Created> { event ->
            handleSceneCreated(event.scene)
        }
        eventSystem.subscribe<TabSelected> { event ->
            handleTabSelected(event.scene)
        }
        eventSystem.subscribe<DeleteRequested> { event ->
            handleDeleteRequested(event.scene)
        }
        eventSystem.subscribe<SceneAction.OpenSceneFile> { event ->
            handleOpenSceneFile(event.sceneFile)
        }
        eventSystem.subscribe<OpenSucceeded> { event ->
            handleOpenSucceeded(event.scene)
        }
        eventSystem.subscribe<OpenCancelled> {
            handleOpenCancelled()
        }
        eventSystem.subscribe<OpenFailed> { event ->
            handleOpenFailed(event.reason)
        }
    }

    private fun handleOpenSceneFile(sceneFile: File) {
        val command = OpenSceneFileCommand(sceneFile, serializer, sceneManager, eventSystem)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene file opened: ${sceneFile.name}")
    }

    private fun handleRenameRequested(scene: Scene, newName: String) {
        if (mutationGate.blockIfPlaying("rename scene")) return
        val oldName = scene.name
        if (newName.isBlank() || newName == oldName) return

        val command = RenameSceneCommand(scene, newName, oldName, projectManager, sceneManager, eventSystem)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene rename requested: '$oldName' -> '$newName'")
    }

    private fun handleSaveRequested(scene: Scene) {
        val command = SaveSceneCommand(scene, projectManager, sceneManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene save requested: ${scene.name}")
    }

    private fun handleSaveAsRequested(scene: Scene) {
        val command = SaveSceneAsCommand(scene, serializer, logger)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene save-as requested: ${scene.name}")
    }

    private fun handleCloseRequested(scene: Scene) {
        if (mutationGate.blockIfPlaying("close scene")) return
        val command = CloseSceneCommand(scene, sceneManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene close requested: ${scene.name}")
    }

    private fun handleCloseOthersRequested(keepScene: Scene) {
        if (mutationGate.blockIfPlaying("close other scenes")) return
        val command = CloseOtherScenesCommand(keepScene, sceneManager, eventSystem)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Close other scenes requested, keeping ${keepScene.name}")
    }

    private fun handleCloseAllRequested() {
        if (mutationGate.blockIfPlaying("close all scenes")) return
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
        if (mutationGate.blockIfPlaying("create scene")) return
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
        logger.logEditor("Scene opened from file: ${scene.name}")
    }

    private fun handleOpenCancelled() {
        logger.logEditor("Scene open cancelled")
    }

    private fun handleOpenFailed(reason: String) {
        logger.logEditor("Scene open failed: $reason", LogLevel.ERROR)
    }

    private fun generateUniqueScenePath(projectDir: File): String {
        val scenesDir = File(projectDir, "Scenes")
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
        if (mutationGate.blockIfPlaying("switch scene tab")) return
        val command = SwitchSceneCommand(scene, sceneManager)
        undoRedoManager.executeCommand(command)
    }

    private fun handleDeleteRequested(scene: Scene) {
        if (mutationGate.blockIfPlaying("delete scene")) return
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
