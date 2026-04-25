package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.EditorWorkspace
import com.pafoid.skate.editor.commands.CreateGameObjectCommand
import com.pafoid.skate.editor.commands.DeleteGameObjectCommand
import com.pafoid.skate.editor.commands.LockToggleCommand
import com.pafoid.skate.editor.commands.VisibilityToggleCommand
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.EventSystem
import com.pafoid.skate.engine.events.GameObjectSelected
import com.pafoid.skate.engine.events.SelectionCleared
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.listeners.KeyListener
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW

class EditorInputHandler(
    private val keyListener: KeyListener,
    private val clipboardService: ClipboardService,
    private val undoRedoManager: UndoRedoManager,
    private val logger: LoggerService
) : KoinComponent {

    private val settingsManager: SettingsManager by inject()
    private val eventSystem: EventSystem by inject()

    private var pendingRenameUid: Int? = null
    private var renameInputMappings: InputMappings? = null

    fun update(scene: Scene?, workspace: EditorWorkspace) {
        if (scene == null) return

        val inputMappings = getInputMappings()
        val selected = workspace.getSelectedGameObject()

        // Global hierarchy actions (work regardless of window focus)
        handleGlobalHierarchyActions(scene, workspace, selected, inputMappings)

        // Standard clipboard/undo operations
        handleClipboardAndUndo(scene, selected, inputMappings)
    }

    /**
     * Handle global hierarchy action shortcuts.
     * These work regardless of which window is focused, but require a selected GameObject for some actions.
     */
    private fun handleGlobalHierarchyActions(
        scene: Scene,
        workspace: EditorWorkspace,
        selected: GameObject?,
        inputMappings: InputMappings
    ) {
        val ctrlDown = keyListener.isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL) || keyListener.isKeyPressed(GLFW.GLFW_KEY_RIGHT_CONTROL)

        // Delete selected object
        if (keyListener.keyBeginPress(inputMappings.hierarchyDelete.keyboardKey) && selected != null) {
            undoRedoManager.executeCommand(DeleteGameObjectCommand(selected, scene))
            eventSystem.publish(SelectionCleared)
            logger.logEditor("Deleted GameObject: ${selected.name}")
        }

        // Create new GameObject (Insert)
        if (keyListener.keyBeginPress(inputMappings.hierarchyCreateNew.keyboardKey)) {
            val newObj = GameObject("GameObject")
            undoRedoManager.executeCommand(CreateGameObjectCommand(newObj, scene))
            workspace.setSelectedGameObject(newObj)
            eventSystem.publish(GameObjectSelected(newObj))
            logger.logEditor("Created new GameObject: ${newObj.name}")
        }

        // Duplicate (D without Ctrl)
        if (keyListener.keyBeginPress(inputMappings.hierarchyDuplicate.keyboardKey) &&
            !ctrlDown && selected != null
        ) {
            val clone = cloneGameObject(selected)
            undoRedoManager.executeCommand(CreateGameObjectCommand(clone, scene))
            workspace.setSelectedGameObject(clone)
            eventSystem.publish(GameObjectSelected(clone))
            logger.logEditor("Duplicated GameObject: ${selected.name} -> ${clone.name}")
        }

        // Toggle Visibility (V without Ctrl)
        if (keyListener.keyBeginPress(inputMappings.hierarchyToggleVisibility.keyboardKey) &&
            !ctrlDown && selected != null
        ) {
            val newVis = !selected.isVisible
            undoRedoManager.executeCommand(VisibilityToggleCommand(selected, newVis))
            logger.logEditor("Toggled visibility for ${selected.name}: $newVis")
        }

        // Toggle Lock (L without Ctrl)
        if (keyListener.keyBeginPress(inputMappings.hierarchyToggleLock.keyboardKey) &&
            !ctrlDown && selected != null
        ) {
            val newLock = !selected.isLocked
            undoRedoManager.executeCommand(LockToggleCommand(selected, newLock))
            logger.logEditor("Toggled lock for ${selected.name}: $newLock")
        }

        // Rename (F2)
        if (keyListener.keyBeginPress(inputMappings.hierarchyRename.keyboardKey) && selected != null) {
            // Signal to SceneHierarchyWindow that rename should start
            pendingRenameUid = selected.getUid()
            renameInputMappings = inputMappings
        }

        // Deselect (Escape) - only if not already handling something else
        if (keyListener.keyBeginPress(inputMappings.hierarchyDeselect.keyboardKey)) {
            workspace.setSelectedGameObject(null)
            eventSystem.publish(SelectionCleared)
            logger.logEditor("Deselected GameObject")
        }
    }

    /**
     * Handle clipboard operations and undo/redo.
     */
    private fun handleClipboardAndUndo(currentScene: Scene, selected: GameObject?, inputMappings: InputMappings) {
        val ctrlDown = keyListener.isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL) || keyListener.isKeyPressed(GLFW.GLFW_KEY_RIGHT_CONTROL)
        if (ctrlDown) {
            // Copy
            if (keyListener.keyBeginPress(GLFW.GLFW_KEY_C)) {
                if (selected != null) {
                    clipboardService.copy(selected)
                    logger.logEditor("Copied GameObject: ${selected.name}")
                }
            }
            // Cut
            else if (keyListener.keyBeginPress(GLFW.GLFW_KEY_X)) {
                if (selected != null) {
                    clipboardService.copy(selected)
                    undoRedoManager.executeCommand(DeleteGameObjectCommand(selected, currentScene))
                    logger.logEditor("Cut GameObject: ${selected.name}")
                }
            }
            // Paste
            else if (keyListener.keyBeginPress(GLFW.GLFW_KEY_V)) {
                val clonedGameObject = clipboardService.paste()
                if (clonedGameObject != null) {
                    // Add to scene at origin for now
                    val origin = Vector3f(0f, 0f, 0f)
                    clonedGameObject.getComponent<Transform>()?.translation?.set(origin)

                    // Set parent to null, as it's being pasted as a root object
                    clonedGameObject.parent = null

                    undoRedoManager.executeCommand(CreateGameObjectCommand(clonedGameObject, currentScene))
                    logger.logEditor("Pasted GameObject: ${clonedGameObject.name}")
                }
            }
            // Undo
            else if (keyListener.keyBeginPress(GLFW.GLFW_KEY_Z)) {
                undoRedoManager.undo()
                logger.logEditor("Undo")
            }
            // Redo
            else if (keyListener.keyBeginPress(GLFW.GLFW_KEY_Y)) {
                undoRedoManager.redo()
                logger.logEditor("Redo")
            }
        }
    }

    private fun getInputMappings(): InputMappings {
        return settingsManager.loadInputMappings() ?: InputMappings()
    }

    /**
     * Deep clone a GameObject with its transform.
     */
    private fun cloneGameObject(go: GameObject): GameObject {
        val cloned = GameObject("${go.name}_clone")
        val originalTransform = go.getComponent<Transform>()
        val newTransform = Transform()
        originalTransform?.let { orig ->
            newTransform.copyFrom(orig)
        }
        newTransform.translation.x += 0.5f
        newTransform.translation.z += 0.5f
        cloned.addComponent(newTransform)
        return cloned
    }

    /**
     * Check if there's a pending rename request and return the GameObject UID.
     * Called by SceneHierarchyWindow during its render loop.
     */
    fun consumePendingRename(): Int? {
        val uid = pendingRenameUid
        pendingRenameUid = null
        return uid
    }
}
