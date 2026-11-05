package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.editor.data.EditorFocus
import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.data.EditorInputState
import com.pafoid.skate.editor.data.EditorShortcut
import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.events.EditorEvent
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.systems.PlayModeGuards
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.InputSystem
import com.pafoid.skate.engine.events.EngineAction
import com.pafoid.skate.engine.events.SceneAction
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.DefaultActions
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW

/**
 * The editor's global keyboard shortcuts (EDT-10). Combos come from [EditorShortcuts], so menus, tooltips and the
 * Key Bindings window show what this handler does. Modifiers must match exactly, and a combo the focused window
 * claimed ([EditorFocus]) is left to that window.
 */
class EditorInputHandler(
    private val clipboardService: ClipboardService,
    private val undoRedoManager: UndoRedoManager,
    private val editorInputState: EditorInputState,
    private val engine: Engine,
    private val settingsManager: EditorSettingsManager,
) {
    private val logger = engine.logger
    private val eventSystem = engine.eventSystem
    private val inputProvider = engine.inputProvider

    /** Read live: settings load after start-up and a rebinding replaces the object. */
    private val inputMappings: EditorInputMappings get() = settingsManager.editorSettings.editorInputMappings

    private fun shiftDown() = inputProvider.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT) || inputProvider.isKeyPressed(GLFW.GLFW_KEY_RIGHT_SHIFT)

    /** True on the frame [shortcut]'s combo is pressed with exactly its modifiers, unless a window claimed it. */
    private fun pressed(shortcut: EditorShortcut): Boolean {
        val combo = shortcut.combo(inputMappings)
        if (combo.ctrl != inputProvider.isControlKeyDown() || combo.shift != shiftDown()) return false
        if (EditorFocus.isClaimed(combo)) return false
        return inputProvider.keyBeginPress(combo.key)
    }

    /** Shortcuts that also work while the game runs (Play/Stop). Called every frame. */
    fun updateAlways() {
        if (engine.sceneManager.currentScene == null || !pressed(EditorShortcuts.PLAY_TOGGLE)) return
        if (engine.runtimePlaying) {
            eventSystem.publish(EngineAction.SetRuntimePlaying(false))
            eventSystem.publish(ViewportAction.StopSimulation)
        } else {
            eventSystem.publish(EngineAction.SetRuntimePlaying(true))
        }
    }

    /** Edit-mode shortcuts and camera input; not called while the game runs. */
    fun update() {
        editorInputState.reset()

        pollEditorInput()
        handleProjectShortcuts()
        val scene = engine.sceneManager.currentScene ?: return
        handleSceneShortcuts(scene)
        handleSelectionShortcuts(scene)
        handleClipboardAndUndo(scene)
    }

    private fun pollEditorInput() {
        editorInputState.isInsideViewport = inputProvider.isInsideViewport()

        if (!inputProvider.isControlKeyDown()) {
            val m = inputMappings
            val moveInput = Vector2f()
            if (inputProvider.isKeyPressed(m.moveForward.keyboardKey)) moveInput.y += 1f
            if (inputProvider.isKeyPressed(m.moveBackward.keyboardKey)) moveInput.y -= 1f
            if (inputProvider.isKeyPressed(m.moveLeft.keyboardKey)) moveInput.x -= 1f
            if (inputProvider.isKeyPressed(m.moveRight.keyboardKey)) moveInput.x += 1f
            if (moveInput.lengthSquared() > 1f) moveInput.normalize()
            editorInputState.moveDirection.set(moveInput)

            var verticalInput = 0f
            if (inputProvider.isKeyPressed(m.moveUp.keyboardKey)) verticalInput += 1f
            if (inputProvider.isKeyPressed(m.moveDown.keyboardKey)) verticalInput -= 1f
            editorInputState.verticalMovement = verticalInput
        }

        val dx = inputProvider.getMouseDx()
        val dy = inputProvider.getMouseDy()
        if (inputProvider.isMiddleMouseButtonDown(true) && editorInputState.isInsideViewport) {
            editorInputState.mouseLook.set(dx, dy)
        } else {
            editorInputState.mouseLook.set(0f, 0f)
        }

        editorInputState.orbitPressed =
            inputProvider.middleMouseButtonBeginPress() && editorInputState.isInsideViewport
        editorInputState.orbitHeld =
            inputProvider.isMiddleMouseButtonDown(true) && editorInputState.isInsideViewport

        if (editorInputState.isInsideViewport) {
            editorInputState.mouseScroll = inputProvider.getMouseScrollY()
        }

        if (pressed(EditorShortcuts.CAMERA_RESET)) editorInputState.resetPressed = true
    }

    private fun handleProjectShortcuts() {
        if (pressed(EditorShortcuts.SEARCH)) eventSystem.publish(EditorEvent.OpenSearch)
        if (pressed(EditorShortcuts.OPEN_PROJECT)) eventSystem.publish(ProjectEvent.OpenProjectFileRequested)
        if (pressed(EditorShortcuts.FULLSCREEN)) eventSystem.publish(ViewportAction.ToggleFullScreen)
    }

    private fun handleSceneShortcuts(scene: Scene) {
        if (pressed(EditorShortcuts.SAVE_SCENE)) eventSystem.publish(SceneAction.SaveRequested(scene))
        if (pressed(EditorShortcuts.SAVE_SCENE_AS)) eventSystem.publish(SceneAction.SaveAsRequested(scene))
        if (pressed(EditorShortcuts.NEW_SCENE)) eventSystem.publish(SceneAction.CreateRequested)
        if (pressed(EditorShortcuts.CREATE_EMPTY)) eventSystem.publish(ViewportAction.CreateEmpty(scene))
    }

    private fun handleSelectionShortcuts(scene: Scene) {
        val selected = scene.selectedGameObject ?: return
        when {
            pressed(EditorShortcuts.DUPLICATE) -> eventSystem.publish(ViewportAction.Duplicate(selected))
            pressed(EditorShortcuts.DELETE) -> eventSystem.publish(ViewportAction.Delete(selected, scene))
            pressed(EditorShortcuts.RENAME) -> eventSystem.publish(ViewportAction.StartRename(selected))
            pressed(EditorShortcuts.FOCUS) -> eventSystem.publish(ViewportAction.FocusSelected)
            pressed(EditorShortcuts.TOGGLE_VISIBILITY) -> eventSystem.publish(ViewportAction.ToggleVisibility(selected, !selected.isVisible))
            pressed(EditorShortcuts.TOGGLE_LOCK) -> eventSystem.publish(ViewportAction.ToggleLock(selected, !selected.isLocked))
            pressed(EditorShortcuts.DESELECT) &&
                !PlayModeGuards.deselectClaimedByGame(engine.runtimePlaying, inputMappings.deselectAll.keyboardKey, cancelBindings()) ->
                eventSystem.publish(ViewportAction.SelectionCleared)
        }
    }

    /** The game's UI cancel bindings (Esc / gamepad B by default), which open the pause menu in Play. */
    private fun cancelBindings(): List<ActionBinding> =
        engine.systemManager.getSystem<InputSystem>()?.actionMap?.bindings(DefaultActions.UI_CANCEL).orEmpty()

    private fun handleClipboardAndUndo(scene: Scene) {
        val selected = scene.selectedGameObject
        when {
            pressed(EditorShortcuts.COPY) -> selected?.let { clipboardService.copy(it) }
            pressed(EditorShortcuts.CUT) -> selected?.let {
                clipboardService.copy(it)
                eventSystem.publish(ViewportAction.Delete(it, scene))
            }
            pressed(EditorShortcuts.PASTE) -> eventSystem.publish(ViewportAction.PasteClipboard())
            pressed(EditorShortcuts.UNDO) -> undoRedoManager.undo()
            pressed(EditorShortcuts.REDO) || pressed(EditorShortcuts.REDO_ALT) -> undoRedoManager.redo()
        }
    }
}
