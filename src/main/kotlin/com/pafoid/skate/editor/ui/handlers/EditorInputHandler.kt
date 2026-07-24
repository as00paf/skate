package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.events.EditorEvent
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.Scene
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW

class EditorInputHandler(
    private val clipboardService: ClipboardService,
    private val undoRedoManager: UndoRedoManager,
    private val editorInputState: EditorInputState,
    private val engine: Engine,
    settingsManager: SettingsManager
) {
    private val logger = engine.logger
    private val eventSystem = engine.eventSystem
    private val inputProvider = engine.inputProvider

    private var inputMappings = settingsManager.editor.editorInputMappings

    fun update() {
        editorInputState.reset()

        pollEditorInput()
        val scene = engine.sceneManager.currentScene ?: return

        // Global hierarchy actions (work regardless of window focus)
        handleGlobalHierarchyActions(scene)

        // Standard clipboard/undo operations
        handleClipboardAndUndo(scene)
    }

    private fun pollEditorInput() {
        editorInputState.isInsideViewport = inputProvider.isInsideViewport()

        // Polling Keyboard
        val moveInput = Vector2f()
        if (inputProvider.isKeyPressed(inputMappings.moveForward.keyboardKey)) moveInput.y += 1f
        if (inputProvider.isKeyPressed(inputMappings.moveBackward.keyboardKey)) moveInput.y -= 1f
        if (inputProvider.isKeyPressed(inputMappings.moveLeft.keyboardKey)) moveInput.x -= 1f
        if (inputProvider.isKeyPressed(inputMappings.moveRight.keyboardKey)) moveInput.x += 1f
        if (moveInput.lengthSquared() > 1f) moveInput.normalize()
        editorInputState.moveDirection.set(moveInput)

        var verticalInput = 0f
        if (inputProvider.isKeyPressed(inputMappings.moveUp.keyboardKey)) verticalInput += 1f
        if (inputProvider.isKeyPressed(inputMappings.moveDown.keyboardKey)) verticalInput -= 1f
        editorInputState.verticalMovement = verticalInput

        // Polling Mouse
        val dx = inputProvider.getMouseDx()
        val dy = inputProvider.getMouseDy()
        if (inputProvider.isRightMouseButtonDown() && editorInputState.isInsideViewport) {
            editorInputState.mouseLook.set(dx, dy)
        } else if (inputProvider.isMouseButtonDown(
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                true
            ) && editorInputState.isInsideViewport
        ) {
            editorInputState.mouseLook.set(dx, dy)
        } else {
            editorInputState.mouseLook.set(0f, 0f)
        }

        editorInputState.orbitPressed =
            inputProvider.mouseButtonBeginPress(GLFW.GLFW_MOUSE_BUTTON_MIDDLE) && editorInputState.isInsideViewport
        editorInputState.orbitHeld =
            inputProvider.isMiddleMouseButtonDown(true) && editorInputState.isInsideViewport

        if (editorInputState.isInsideViewport) {
            editorInputState.mouseScroll = inputProvider.getMouseScrollY()
        }

        if (inputProvider.keyBeginPress(GLFW.GLFW_KEY_HOME)) {
            editorInputState.resetPressed = true
        }
    }

    private fun handleGlobalHierarchyActions(scene: Scene) {
        val selected = scene.selectedGameObject
        if (inputProvider.isControlKeyDown()) {
            if (inputProvider.keyBeginPress(inputMappings.openSearchWindow.keyboardKey)) {
                eventSystem.publish(EditorEvent.OpenSearch)
            }
            if (inputProvider.keyBeginPress(inputMappings.hierarchyDuplicate.keyboardKey) && selected != null) {
                eventSystem.publish(ViewportAction.Duplicate(selected))
                logger.logEditor("Duplicate GameObject requested: ${selected.name}")
            }
        } else {
            if (inputProvider.isKeyPressed(inputMappings.toggleFullScreen.keyboardKey)) {
                eventSystem.publish(ViewportAction.ToggleFullScreen)
            }
            if (inputProvider.keyBeginPress(inputMappings.hierarchyDelete.keyboardKey) && selected != null) {
                eventSystem.publish(ViewportAction.Delete(selected, scene))
                logger.logEditor("Deleted GameObject: ${selected.name}")
            }
            if (inputProvider.keyBeginPress(inputMappings.hierarchyCreateNew.keyboardKey)) {
                eventSystem.publish(ViewportAction.CreateEmpty(scene))
                logger.logEditor("Create empty GameObject requested")
            }
            if (inputProvider.keyBeginPress(inputMappings.hierarchyToggleVisibility.keyboardKey) && selected != null
            ) {
                val newVis = !selected.isVisible
                eventSystem.publish(ViewportAction.ToggleVisibility(selected, newVis))
                logger.logEditor("Toggled visibility for ${selected.name}: $newVis")
            }
            if (inputProvider.keyBeginPress(inputMappings.hierarchyToggleLock.keyboardKey) && selected != null
            ) {
                val newLock = !selected.isLocked
                eventSystem.publish(ViewportAction.ToggleLock(selected, newLock))
                logger.logEditor("Toggled lock for ${selected.name}: $newLock")
            }
            if (inputProvider.keyBeginPress(inputMappings.hierarchyRename.keyboardKey) && selected != null) {
                // TODO: publish rename request to focus on name in property window
                //eventSystem.publish(ViewportAction.RenameGameObject)
            }
            if (inputProvider.isKeyPressed(inputMappings.deselectAll.keyboardKey) && selected != null) {
                eventSystem.publish(ViewportAction.SelectionCleared)
                logger.logEditor("Deselected GameObject")
            }
        }
    }

    private fun handleClipboardAndUndo(currentScene: Scene) {
        val selected = currentScene.selectedGameObject
        val ctrlDown = inputProvider.isControlKeyDown()
        if (ctrlDown) {
            // Copy
            if (inputProvider.keyBeginPress(GLFW.GLFW_KEY_C)) {
                if (selected != null) {
                    clipboardService.copy(selected)
                    logger.logEditor("Copied GameObject: ${selected.name}")
                }
            }
            // Cut
            else if (inputProvider.keyBeginPress(GLFW.GLFW_KEY_X)) {
                if (selected != null) {
                    clipboardService.copy(selected)
                    eventSystem.publish(ViewportAction.Delete(selected, currentScene))
                    logger.logEditor("Cut GameObject: ${selected.name}")
                }
            }
            // Paste
            else if (inputProvider.keyBeginPress(GLFW.GLFW_KEY_V)) {
                eventSystem.publish(ViewportAction.PasteClipboard())
                logger.logEditor("Paste requested")
            }
            // Undo
            else if (inputProvider.keyBeginPress(GLFW.GLFW_KEY_Z)) {
                undoRedoManager.undo()
                logger.logEditor("Undo")
            }
            // Redo
            else if (inputProvider.keyBeginPress(GLFW.GLFW_KEY_Y)) {
                undoRedoManager.redo()
                logger.logEditor("Redo")
            }
        }
    }
}
