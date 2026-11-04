package com.pafoid.skate.engine.input

import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.Time
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.input.listeners.GamepadListener
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.utils.Ray
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_CURSOR
import org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT
import org.lwjgl.glfw.GLFW.glfwGetCurrentContext
import org.lwjgl.glfw.GLFW.glfwGetInputMode
import org.lwjgl.glfw.GLFW.glfwSetInputMode

class InputProvider(
    val logger: LoggerService
) {
    private val inputBuffer: InputBuffer = InputBuffer()

    val gamepadListener: GamepadListener = GamepadListener(logger)
    val keyListener: KeyListener = KeyListener()
    val mouseListener: MouseListener = MouseListener()

    fun initializeGamepad() {
        gamepadListener.init()
    }

    fun refreshGamepadState() {
        gamepadListener.update()
    }

    fun endFrame() {
        inputBuffer.push(
            Time.getTime(),
            getMouseTargetPos(),
            getAxes(joystickForPad(0))
        )
        keyListener.endFrame()
        mouseListener.endFrame()
    }

    // Keys
    fun isKeyPressed(key: Int): Boolean = keyListener.isKeyPressed(key)
    fun keyBeginPress(key: Int): Boolean = keyListener.keyBeginPress(key)
    /** Text typed since the last call (Unicode, respects layout/IME); for text fields. */
    fun consumeTypedText(): String = keyListener.text.consumeText()
    fun isKeyRepeated(key: Int): Boolean = keyListener.text.wasRepeated(key)
    fun isControlKeyDown(): Boolean =
        isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyPressed(GLFW.GLFW_KEY_RIGHT_CONTROL)

    // Mouse (pointer contract: docs/design/INP-09.md)
    /** Cursor motion this frame in window (screen) units; for look/drag sensitivity, not for positions. */
    fun getMouseDx() = mouseListener.dx
    fun getMouseDy() = mouseListener.dy

    /** Pointer offset from the game view's top-left, in pointer units, clamped to the view. */
    fun getMouseViewPos() = mouseListener.viewLocal().let { (x, y) -> Vector2f(x, y) }

    /** Pointer as a fraction of the game view in [0, 1]. Picking reads the target pixel at this position. */
    fun getNormalizedMousePos() = mouseListener.viewNormalized().let { (x, y) -> Vector2f(x, y) }

    /** Pointer in game render-target pixels (top-left origin); what runtime UI and game scripts see. */
    fun getMouseTargetPos() = mouseListener.viewTarget().let { (x, y) -> Vector2f(x, y) }

    fun isLeftMouseButtonDown(ignoreImGui: Boolean = false) =
        mouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT, ignoreImGui)

    fun isRightMouseButtonDown(ignoreImGui: Boolean = false) =
        mouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_RIGHT, ignoreImGui)

    fun isMiddleMouseButtonDown(ignoreImGui: Boolean = false) =
        mouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_MIDDLE, ignoreImGui)

    fun isMouseButtonDown(button: Int, ignoreImGui: Boolean = false) =
        mouseListener.isMouseButtonDown(button, ignoreImGui)

    fun mouseButtonBeginPress(button: Int) = mouseListener.mouseButtonBeginPress(button)
    fun leftMouseButtonBeginPress() = mouseListener.mouseButtonBeginPress(GLFW_MOUSE_BUTTON_LEFT)
    fun rightMouseButtonBeginPress() = mouseListener.mouseButtonBeginPress(GLFW_MOUSE_BUTTON_RIGHT)
    fun middleMouseButtonBeginPress() = mouseListener.mouseButtonBeginPress(GLFW_MOUSE_BUTTON_MIDDLE)

    fun isInsideViewport(): Boolean {
        return mouseListener.isInsideView()
    }

    fun getMouseScrollY() = mouseListener.getScrollY()

    // Gamepad
    /** Joystick id of logical pad [pad] (0 = primary), or -1 when no such pad is connected. */
    fun joystickForPad(pad: Int = 0): Int = gamepadListener.joystickForPad(pad)
    fun isJoystickPresent(jid: Int): Boolean = gamepadListener.isGamepadPresent(jid)
    fun getAxes(jid: Int): FloatArray? = gamepadListener.getAxes(jid)
    fun getButtons(jid: Int): BooleanArray? = gamepadListener.getButtons(jid)
    fun buttonPressed(jid: Int, button: Int): Boolean = gamepadListener.buttonPressed(jid, button)
    fun buttonWasPressed(jid: Int, button: Int): Boolean = gamepadListener.buttonWasPressed(jid, button)
    fun buttonBeginPress(jid: Int, button: Int): Boolean = gamepadListener.buttonBeginPress(jid, button)
    fun getCursorMode(): CursorMode {
        val window = glfwGetCurrentContext()
        if (window == 0L) return CursorMode.NORMAL
        return CursorMode.fromGlfw(glfwGetInputMode(window, GLFW_CURSOR))
    }

    /** Sets the cursor mode; the first mouse move afterwards is not reported as a delta, so the camera doesn't jump. */
    fun setCursorMode(mode: CursorMode) {
        val window = glfwGetCurrentContext()
        if (window == 0L || getCursorMode() == mode) return
        glfwSetInputMode(window, GLFW_CURSOR, mode.glfwValue)
        mouseListener.discardNextDelta()
    }

    fun isCursorDisabled(): Boolean {
        val window = glfwGetCurrentContext()
        if (window == 0L) return false
        return glfwGetInputMode(window, GLFW_CURSOR) == GLFW_CURSOR_DISABLED
    }

    // Utils
    /** World ray through the pointer. Built from NDC, so it does not depend on the camera's pixel size or DPI. */
    fun screenToRay(camera: CameraComponent): Ray {
        val (x, y) = mouseListener.viewNdc()

        val invProjView = Matrix4f(camera.projection).mul(camera.view).invert()

        // Ray start (near plane) and end (far plane) in world space
        val near = Vector4f(x, y, -1f, 1f).mul(invProjView)
        val far = Vector4f(x, y, 1f, 1f).mul(invProjView)

        near.div(near.w)
        far.div(far.w)

        val rayOrigin = Vector3f(near.x, near.y, near.z)
        val rayDirection = Vector3f(far.x - near.x, far.y - near.y, far.z - near.z).normalize()

        return Ray(rayOrigin, rayDirection)
    }
}
