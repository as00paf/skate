package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.commands.CloseSceneCommand
import com.pafoid.skate.editor.commands.CreateSceneCommand
import com.pafoid.skate.editor.commands.RenameSceneCommand
import com.pafoid.skate.editor.commands.SaveSceneAsCommand
import com.pafoid.skate.editor.commands.SaveSceneCommand
import com.pafoid.skate.editor.project.SceneSerializer
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.EventSystem
import com.pafoid.skate.engine.events.*
import com.pafoid.skate.engine.events.SceneChanged
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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

    fun init() {
        eventSystem.subscribe<SceneRenameRequested> { event ->
            handleRenameRequested(event.sceneIndex, event.newName)
        }
        eventSystem.subscribe<SceneSaveRequested> { event ->
            handleSaveRequested(event.sceneIndex)
        }
        eventSystem.subscribe<SceneSaveAsRequested> { event ->
            handleSaveAsRequested(event.sceneIndex)
        }
        eventSystem.subscribe<SceneCloseRequested> { event ->
            handleCloseRequested(event.sceneIndex)
        }
        eventSystem.subscribe<SceneCloseOthersRequested> { event ->
            handleCloseOthersRequested(event.keepIndex)
        }
        eventSystem.subscribe<SceneCloseAllRequested> {
            handleCloseAllRequested()
        }
        eventSystem.subscribe<SceneCreateRequested> {
            handleCreateRequested()
        }
    }

    private fun handleRenameRequested(index: Int, newName: String) {
        val scene = sceneManager.openScenes.getOrNull(index) ?: return
        val oldName = scene.name
        if (newName.isBlank() || newName == oldName) return

        val command = RenameSceneCommand(scene, newName, oldName, sceneManager, eventSystem)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene rename requested: '$oldName' -> '$newName'")
    }

    private fun handleSaveRequested(index: Int) {
        val scene = sceneManager.openScenes.getOrNull(index) ?: return
        val command = SaveSceneCommand(scene, sceneSerializer)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene save requested: ${scene.name}")
    }

    private fun handleSaveAsRequested(index: Int) {
        val scene = sceneManager.openScenes.getOrNull(index) ?: return
        val command = SaveSceneAsCommand(scene, sceneSerializer)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene save-as requested: ${scene.name}")
    }

    private fun handleCloseRequested(index: Int) {
        val scene = sceneManager.openScenes.getOrNull(index) ?: return
        val command = CloseSceneCommand(scene, index, sceneManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene close requested: ${scene.name}")
    }

    private fun handleCloseOthersRequested(keepIndex: Int) {
        sceneManager.closeOtherScenes(keepIndex)
        eventSystem.publish(SceneChanged)
        logger.logEditor("Close other scenes requested, keeping index $keepIndex")
    }

    private fun handleCloseAllRequested() {
        sceneManager.closeAllScenes()
        logger.logEditor("Close all scenes requested")
    }

    private fun handleCreateRequested() {
        val command = CreateSceneCommand("New Scene", sceneInitializer, sceneManager)
        undoRedoManager.executeCommand(command)
        logger.logEditor("Scene create requested")
    }
}
