package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.events.JumpPressed
import com.pafoid.skate.engine.events.JumpReleased
import com.pafoid.skate.engine.events.MovementInput
import com.pafoid.skate.engine.events.TrickInput
import com.pafoid.skate.engine.input.InputBinding
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.game.skateboard.TrickType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

class InputSystemTest {

    private lateinit var inputProvider: InputProvider
    private lateinit var mouseListener: MouseListener
    private lateinit var settingsManager: EditorSettingsManager
    private lateinit var stringManager: StringManager
    private lateinit var eventSystem: EventSystem
    private lateinit var scene: Scene
    private lateinit var inputSystem: InputSystem

    @BeforeEach
    fun setup() {
        inputProvider = mockk(relaxed = true)
        mouseListener = mockk(relaxed = true)
        settingsManager = mockk(relaxed = true)
        stringManager = mockk(relaxed = true)
        eventSystem = EventSystem()

        scene = Scene("TestScene")
        scene.isRunning = true

        val inputMappings = InputMappings().apply {
            jump = InputBinding(gamepadButton = 0, keyboardKey = GLFW.GLFW_KEY_SPACE)
            kickflip = InputBinding(gamepadButton = 2, keyboardKey = GLFW.GLFW_KEY_W)
            moveUp = InputBinding(gamepadAxis = 1)
            moveDown = InputBinding(gamepadAxis = 1)
            moveLeft = InputBinding(gamepadAxis = 0)
            moveRight = InputBinding(gamepadAxis = 0)
        }

        every { settingsManager.loadInputMappings() } returns inputMappings
        every { inputProvider.isCursorDisabled() } returns true

        inputSystem = InputSystem(
            inputProvider = inputProvider,
            eventSystem = eventSystem
        )
        inputSystem.init(scene)
    }

    @Test
    fun `InputSystem initializes correctly`() {
        assertNotNull(inputSystem)
        assertEquals(SystemManager.ExecutionPriority.EARLY, inputSystem.priority)
        verify(exactly = 1) { inputProvider.initializeGamepad() }
    }

    @Test
    fun `gamepad state refresh runs before polling`() {
        val player = GameObject("TestPlayer").addComponent(InputStateComponent())
        scene.gameObjects.add(player)

        every { inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1) } returns false

        inputSystem.update(0.016f)

        verifyOrder {
            inputProvider.refreshGamepadState()
            inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1)
        }
    }

    @Test
    fun `JumpPressed event published when jump button pressed`() {
        val player = GameObject("TestPlayer").addComponent(InputStateComponent())
        scene.gameObjects.add(player)

        every { inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1) } returns true
        every { inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1) } returns FloatArray(6)
        val buttons = BooleanArray(10)
        buttons[0] = true
        every { inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1) } returns buttons

        var jumpPressedReceived = false
        eventSystem.subscribe<JumpPressed> { jumpPressedReceived = true }

        inputSystem.update(0.016f)

        assertTrue(jumpPressedReceived)
    }

    @Test
    fun `JumpReleased event published when jump button released`() {
        val player = GameObject("TestPlayer").addComponent(InputStateComponent())
        scene.gameObjects.add(player)

        every { inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1) } returns true
        every { inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1) } returns FloatArray(6)

        val buttonsPressed = BooleanArray(10)
        buttonsPressed[0] = true
        every { inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1) } returns buttonsPressed
        inputSystem.update(0.016f)

        every { inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1) } returns BooleanArray(10)

        var jumpReleasedCount = 0
        eventSystem.subscribe<JumpReleased> { jumpReleasedCount++ }

        inputSystem.update(0.016f)

        assertEquals(1, jumpReleasedCount)
    }

    @Test
    fun `movement emits neutral input once on stick release`() {
        val player = GameObject("TestPlayer").addComponent(InputStateComponent())
        scene.gameObjects.add(player)

        every { inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1) } returns true
        every { inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1) } returns BooleanArray(10)

        val activeAxes = FloatArray(6)
        activeAxes[0] = 0.8f
        every { inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1) } returns activeAxes andThen FloatArray(6)

        val movementMagnitudes = mutableListOf<Float>()
        eventSystem.subscribe<MovementInput> { movementMagnitudes.add(it.magnitude) }

        inputSystem.update(0.016f)
        inputSystem.update(0.016f)

        assertEquals(2, movementMagnitudes.size)
        assertTrue(movementMagnitudes[0] > 0f)
        assertEquals(0f, movementMagnitudes[1])
    }

    @Test
    fun `TrickInput events published for trick buttons`() {
        val player = GameObject("TestPlayer").addComponent(InputStateComponent())
        scene.gameObjects.add(player)

        every { inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1) } returns true
        every { inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1) } returns FloatArray(6)
        val buttons = BooleanArray(10)
        buttons[2] = true
        every { inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1) } returns buttons

        val receivedTricks = mutableListOf<TrickInput>()
        eventSystem.subscribe<TrickInput> { receivedTricks.add(it) }

        inputSystem.update(0.016f)

        assertTrue(receivedTricks.any { it.trickType == TrickType.KICKFLIP && it.isPressed })
    }

    @Test
    fun `system handles missing joystick gracefully`() {
        val player = GameObject("TestPlayer").addComponent(InputStateComponent())
        scene.gameObjects.add(player)
        every { inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1) } returns false

        inputSystem.update(0.016f)
    }

    @Test
    fun `mouse look contributes to camera look when cursor is disabled`() {
        val inputState = InputStateComponent()
        val player = GameObject("TestPlayer").addComponent(inputState)
        scene.gameObjects.add(player)

        every { inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1) } returns false
        every { mouseListener.dx } returns 4f
        every { mouseListener.dy } returns -2f
        every { inputProvider.isCursorDisabled() } returns true

        inputSystem.update(0.016f)

        assertEquals(0.4f, inputState.cameraLook.x, 0.0001f)
        assertEquals(-0.2f, inputState.cameraLook.y, 0.0001f)
    }

    @Test
    fun `mouse look ignored when cursor is not disabled`() {
        val inputState = InputStateComponent()
        val player = GameObject("TestPlayer").addComponent(inputState)
        scene.gameObjects.add(player)

        every { inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1) } returns false
        every { mouseListener.dx } returns 5f
        every { mouseListener.dy } returns 5f
        every { inputProvider.isCursorDisabled() } returns false

        inputSystem.update(0.016f)

        assertEquals(0f, inputState.cameraLook.x, 0.0001f)
        assertEquals(0f, inputState.cameraLook.y, 0.0001f)
    }
}
