package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import com.pafoid.skate.engine.input.listeners.KeyListener
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.lwjgl.glfw.GLFW

class EditorInputHandler(
    private val keyListener: KeyListener,
    private val clipboardService: ClipboardService,
    private val undoRedoManager: UndoRedoManager,
    private val logger: LoggerService
) : KoinComponent {

    fun update(currentScene: Scene?) {
        if (currentScene == null) return

        val ctrlDown = keyListener.isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL) || keyListener.isKeyPressed(GLFW.GLFW_KEY_RIGHT_CONTROL)

        if (ctrlDown) {
            // Copy
            if (keyListener.keyBeginPress(GLFW.GLFW_KEY_C)) {
                val selected = currentScene.getSelectedGameObject()
                if (selected != null) {
                    clipboardService.copy(selected)
                    logger.logEditor("Copied GameObject: ${selected.name}")
                }
            }
            // Cut
            else if (keyListener.keyBeginPress(GLFW.GLFW_KEY_X)) {
                val selected = currentScene.getSelectedGameObject()
                if (selected != null) {
                    val cloned = clipboardService.paste() ?: return // paste here returns the copied object from cut
                    clipboardService.copy(selected) // Redundant but following old logic
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
}
