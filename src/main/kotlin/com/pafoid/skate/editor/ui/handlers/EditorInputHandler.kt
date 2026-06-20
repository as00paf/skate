package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.events.EditorEvent
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.logEditor
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.input.IInputBuffer
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.listeners.GamepadListener
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.utils.Time
import org.joml.Vector2f
import org.koin.core.component.KoinComponent
import org.lwjgl.glfw.GLFW

class EditorInputHandler(
    private val keyListener: KeyListener,
    private val mouseListener: MouseListener,
    private val joystickListener: GamepadListener,
    private val inputBuffer: IInputBuffer,
    private val clipboardService: ClipboardService,
    private val undoRedoManager: UndoRedoManager,
    private val logger: LoggerService,
    private val editorInputState: EditorInputState,
    private val sceneManager: SceneManager,
    private val engine: Engine,
    private val projectManager: ProjectManager,
    private val eventSystem: EventSystem
) : KoinComponent {

    private var inputMappings = projectManager.currentProject?.gameplaySettings?.inputMappings ?: InputMappings()

    private var pendingRenameUid: Int? = null

    fun init(glfwWindow: Long) {
        GLFW.glfwSetCursorPosCallback(glfwWindow, mouseListener::mousePosCallback)
        GLFW.glfwSetMouseButtonCallback(glfwWindow, mouseListener::mouseButtonCallback)
        GLFW.glfwSetScrollCallback(glfwWindow, mouseListener::mouseScrollCallback)
        GLFW.glfwSetKeyCallback(glfwWindow, keyListener::keyCallback)
    }

    fun update() {
        editorInputState.reset()
        if (engine.runtimePlaying) {
            handleInputs()
            return
        }

        pollEditorInput()
        val scene = sceneManager.currentScene ?: return


        val selected = scene.selectedGameObject

        // Global hierarchy actions (work regardless of window focus)
        handleGlobalHierarchyActions(scene, selected, inputMappings)

        // Standard clipboard/undo operations
        handleClipboardAndUndo(scene, selected, inputMappings)

        handleInputs()
    }

    private fun pollEditorInput() {
        editorInputState.isInsideViewport = mouseListener.isInsideViewport()
        editorInputState.isFocused = true // Simplify focus for now

        // Polling Keyboard
        val moveInput = Vector2f()
        if (keyListener.isKeyPressed(GLFW.GLFW_KEY_W)) moveInput.y += 1f
        if (keyListener.isKeyPressed(GLFW.GLFW_KEY_S)) moveInput.y -= 1f
        if (keyListener.isKeyPressed(GLFW.GLFW_KEY_A)) moveInput.x -= 1f
        if (keyListener.isKeyPressed(GLFW.GLFW_KEY_D)) moveInput.x += 1f
        if (moveInput.lengthSquared() > 1f) moveInput.normalize()
        editorInputState.moveDirection.set(moveInput)

        var verticalInput = 0f
        if (keyListener.isKeyPressed(GLFW.GLFW_KEY_SPACE)) verticalInput += 1f
        if (keyListener.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)) verticalInput -= 1f
        editorInputState.verticalMovement = verticalInput

        // Polling Mouse
        val dx = mouseListener.getDx()
        val dy = mouseListener.getDy()
        if (mouseListener.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT) && editorInputState.isInsideViewport) {
            editorInputState.mouseLook.set(dx, dy)
        } else if (mouseListener.isMouseButtonDown(
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                true
            ) && editorInputState.isInsideViewport
        ) {
            editorInputState.mouseLook.set(dx, dy)
        } else {
            editorInputState.mouseLook.set(0f, 0f)
        }

        editorInputState.orbitPressed =
            mouseListener.mouseButtonBeginPress(GLFW.GLFW_MOUSE_BUTTON_MIDDLE) && editorInputState.isInsideViewport
        editorInputState.orbitHeld =
            mouseListener.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, true) && editorInputState.isInsideViewport

        if (editorInputState.isInsideViewport) {
            editorInputState.mouseScroll = mouseListener.getScrollY()
        }

        if (keyListener.keyBeginPress(GLFW.GLFW_KEY_HOME)) {
            editorInputState.resetPressed = true
        }
    }

    private fun handleInputs() {
        // Record high-frequency input
        inputBuffer.push(
            Time.getTime(),
            Vector2f(mouseListener.getX(), mouseListener.getY()),
            joystickListener.getAxes(GLFW.GLFW_JOYSTICK_1)
        )
        keyListener.endFrame()
        mouseListener.endFrame()
    }

    /**
     * Handle global hierarchy action shortcuts.
     * These work regardless of which window is focused, but require a selected GameObject for some actions.
     */
    private fun handleGlobalHierarchyActions(
        scene: Scene,
        selected: GameObject?,
        inputMappings: InputMappings
    ) {
        val ctrlDown = keyListener.isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL) || keyListener.isKeyPressed(GLFW.GLFW_KEY_RIGHT_CONTROL)

        // Search
        if (ctrlDown && keyListener.keyBeginPress(GLFW.GLFW_KEY_P)) {
            eventSystem.publish(EditorEvent.OpenSearch)
        }

        // Full Screen (By passes WindowRegistry)
        if (keyListener.isKeyPressed(GLFW.GLFW_KEY_F12)) {
            eventSystem.publish(ViewportAction.ToggleFullScreen)
        }

        // Delete selected object
        if (keyListener.keyBeginPress(inputMappings.hierarchyDelete.keyboardKey) && selected != null) {
            eventSystem.publish(ViewportAction.Delete(selected, scene))
            logger.logEditor("Deleted GameObject: ${selected.name}")
        }

        // Create new GameObject (Insert)
        if (keyListener.keyBeginPress(inputMappings.hierarchyCreateNew.keyboardKey)) {
            eventSystem.publish(ViewportAction.CreateEmpty(scene))
            logger.logEditor("Create empty GameObject requested")
        }

        // Duplicate (D without Ctrl)
        if (keyListener.keyBeginPress(inputMappings.hierarchyDuplicate.keyboardKey) &&
            !ctrlDown && selected != null
        ) {
            eventSystem.publish(ViewportAction.Duplicate(selected))
            logger.logEditor("Duplicate GameObject requested: ${selected.name}")
        }

        // Toggle Visibility (V without Ctrl)
        if (keyListener.keyBeginPress(inputMappings.hierarchyToggleVisibility.keyboardKey) &&
            !ctrlDown && selected != null
        ) {
            val newVis = !selected.isVisible
            eventSystem.publish(ViewportAction.ToggleVisibility(selected, newVis))
            logger.logEditor("Toggled visibility for ${selected.name}: $newVis")
        }

        // Toggle Lock (L without Ctrl)
        if (keyListener.keyBeginPress(inputMappings.hierarchyToggleLock.keyboardKey) &&
            !ctrlDown && selected != null
        ) {
            val newLock = !selected.isLocked
            eventSystem.publish(ViewportAction.ToggleLock(selected, newLock))
            logger.logEditor("Toggled lock for ${selected.name}: $newLock")
        }

        // Rename
        if (keyListener.keyBeginPress(inputMappings.hierarchyRename.keyboardKey) && selected != null) {
            // Signal to SceneHierarchyWindow that rename should start
            pendingRenameUid = selected.getUid()
        }

        // Deselect
        if (keyListener.isKeyPressed(inputMappings.editorDeselect.keyboardKey) && selected != null) {
            eventSystem.publish(ViewportAction.SelectionCleared)
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
                    eventSystem.publish(ViewportAction.Delete(selected, currentScene))
                    logger.logEditor("Cut GameObject: ${selected.name}")
                }
            }
            // Paste
            else if (keyListener.keyBeginPress(GLFW.GLFW_KEY_V)) {
                eventSystem.publish(ViewportAction.PasteClipboard())
                logger.logEditor("Paste requested")
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
