package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.commands.project.CloseAllScenesCommand
import com.pafoid.skate.editor.commands.project.CloseOtherScenesCommand
import com.pafoid.skate.editor.commands.project.CloseSceneCommand
import com.pafoid.skate.editor.commands.project.CreateSceneCommand
import com.pafoid.skate.editor.commands.project.DeleteSceneCommand
import com.pafoid.skate.editor.commands.project.OpenSceneCommand
import com.pafoid.skate.editor.commands.project.RenameSceneCommand
import com.pafoid.skate.editor.commands.project.SaveSceneAsCommand
import com.pafoid.skate.editor.commands.project.SaveSceneCommand
import com.pafoid.skate.editor.commands.scene.SwitchSceneCommand
import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.editor.project.ProjectManager
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneAction
import com.pafoid.skate.engine.events.SceneCloseAllRequested
import com.pafoid.skate.engine.events.SceneCloseOthersRequested
import com.pafoid.skate.engine.events.SceneCloseRequested
import com.pafoid.skate.engine.events.SceneCreateRequested
import com.pafoid.skate.engine.events.SceneCreated
import com.pafoid.skate.engine.events.SceneDeleteRequested
import com.pafoid.skate.engine.events.SceneOpenCancelled
import com.pafoid.skate.engine.events.SceneOpenFailed
import com.pafoid.skate.engine.events.SceneOpenRequested
import com.pafoid.skate.engine.events.SceneOpenSucceeded
import com.pafoid.skate.engine.events.SceneRenameRequested
import com.pafoid.skate.engine.events.SceneSaveAsRequested
import com.pafoid.skate.engine.events.SceneSaveRequested
import com.pafoid.skate.engine.events.SceneTabSelected
import com.pafoid.skate.engine.utils.IJobSystem
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * Handles [SceneAction] events by executing the appropriate commands
 * or calling SceneManager/SceneSerializer methods.
 * 
 * This decouples UI components (like EditorScenesTabBar) from orchestration logic.
 */
class SceneActionHandler : KoinComponent {
    private val sceneManager: SceneManager by inject()
    private val sceneSerializer: SceneSerializer by inject()
    private val undoRedoManager: UndoRedoManager by inject()
    private val eventSystem: EventSystem by inject()
    private val logger: LoggerService by inject()
    private val sceneInitializer: LevelEditorSceneInitializer by inject()
    private val projectManager: ProjectManager by inject()
    private val jobSystem: IJobSystem by inject()

    fun init() {
        eventSystem.subscribe<SceneRenameRequested> { event ->
            handleRenameRequested(event.scene, event.newName)
        }
        eventSystem.subscribe<SceneSaveRequested> { event ->
            handleSaveRequested(event.scene)
        }
        eventSystem.subscribe<SceneSaveAsRequested> { event ->
            handleSaveAsRequested(event.scene)
        }
        eventSystem.subscribe<SceneCloseRequested> { event ->
            handleCloseRequested(event.scene)
        }
        eventSystem.subscribe<SceneCloseOthersRequested> { event ->
            handleCloseOthersRequested(event.keepScene)
        }
        eventSystem.subscribe<SceneCloseAllRequested> {
            handleCloseAllRequested()
        }
        eventSystem.subscribe<SceneCreateRequested> {
            handleCreateRequested()
        }
        eventSystem.subscribe<SceneOpenRequested> {
            handleOpenRequested()
        }
        eventSystem.subscribe<SceneCreated> { event ->
            handleSceneCreated(event.scene)
        }
        eventSystem.subscribe<SceneTabSelected> { event ->
            handleTabSelected(event.scene)
        }
        eventSystem.subscribe<SceneDeleteRequested> { event ->
            handleDeleteRequested(event.scene)
        }
        eventSystem.subscribe<SceneOpenSucceeded> { event ->
            handleOpenSucceeded(event.scene)
        }
        eventSystem.subscribe<SceneOpenCancelled> {
            handleOpenCancelled()
        }
        eventSystem.subscribe<SceneOpenFailed> { event ->
            handleOpenFailed(event.reason)
        }
    }

    private fun handleRenameRequested(scene: Scene, newName: String) {
        val oldName = scene.name
        if (newName.isBlank() || newName == oldName) return

        val command = RenameSceneCommand(scene, newName, oldName, sceneManager, eventSystem)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene rename requested: '$oldName' -> '$newName'")
    }

    private fun handleSaveRequested(scene: Scene) {
        val command = SaveSceneCommand(scene, sceneSerializer)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene save requested: ${scene.name}")
    }

    private fun handleSaveAsRequested(scene: Scene) {
        val command = SaveSceneAsCommand(scene, sceneSerializer)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene save-as requested: ${scene.name}")
    }

    private fun handleCloseRequested(scene: Scene) {
        val command = CloseSceneCommand(scene, sceneManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene close requested: ${scene.name}")
    }

    private fun handleCloseOthersRequested(keepScene: Scene) {
        val command = CloseOtherScenesCommand(keepScene, sceneManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Close other scenes requested, keeping ${keepScene.name}")
    }

    private fun handleCloseAllRequested() {
        val command = CloseAllScenesCommand(sceneManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Close all scenes requested")
    }

    private fun handleCreateRequested() {
        val projectDir = projectManager.getProjectDirectory()
        if (projectDir == null) {
            logger.logEditor("Cannot create scene: no project directory", LogLevel.WARN)
            return
        }

        val fullPath = generateUniqueScenePath(projectDir)
        val sceneName = File(fullPath).nameWithoutExtension

        val command = CreateSceneCommand(sceneName, sceneInitializer, sceneSerializer, fullPath, jobSystem, eventSystem)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene create requested: $sceneName -> $fullPath")
    }

    private fun handleSceneCreated(scene: Scene) {
        sceneManager.openSceneBlocking(scene)
        logger.logEditor("New scene created and saved: ${scene.name} -> ${scene.sceneData.levelPath}")
    }

    private fun handleOpenRequested() {
        val command = OpenSceneCommand(sceneInitializer, sceneSerializer, sceneManager, jobSystem, eventSystem)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene open requested")
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
        val command = SwitchSceneCommand(scene, sceneManager)
        undoRedoManager.executeCommand(command)
    }

    private fun handleDeleteRequested(scene: Scene) {
        // Cannot delete if it's the only scene
        if (sceneManager.openScenes.size <= 1) {
            logger.logEditor("Cannot delete the last remaining scene")
            return
        }
        val command = DeleteSceneCommand(scene, sceneManager, logger)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene delete requested: ${scene.name}")
    }
}
