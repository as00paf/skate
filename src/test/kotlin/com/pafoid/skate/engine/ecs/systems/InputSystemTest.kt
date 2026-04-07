package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.EditorCamera
import com.pafoid.skate.editor.data.EditorInputMappings
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.scene.SceneInitializer
import com.pafoid.skate.engine.ecs.scene.createGameObject
import com.pafoid.skate.engine.events.JumpPressed
import com.pafoid.skate.engine.events.JumpReleased
import com.pafoid.skate.engine.events.TrickInput
import com.pafoid.skate.engine.input.IInputProvider
import com.pafoid.skate.engine.input.InputBinding
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.settings.GameplaySettings
import com.pafoid.skate.engine.settings.HardwareSettings
import com.pafoid.skate.game.skateboard.TrickType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

/**
 * Unit tests for InputSystem with event-driven architecture.
 *
 * Tests cover:
 * - System initialization
 * - Event publishing (JumpPressed, JumpReleased, TrickInput)
 * - Editor input handling
 * - Joystick absence handling
 */
class InputSystemTest {

    // Mock dependencies
    private lateinit var inputProvider: IInputProvider
    private lateinit var mouseListener: MouseListener
    private lateinit var settingsManager: SettingsManager
    private lateinit var stringManager: StringManager
    private lateinit var sceneInitializer: SceneInitializer
    private lateinit var scene: Scene

    // Test subject
    private lateinit var inputSystem: InputSystem

    // Test settings
    private lateinit var hardwareSettings: HardwareSettings
    private lateinit var gameplaySettings: GameplaySettings
    private lateinit var inputMappings: InputMappings
    private lateinit var editorInputMappings: EditorInputMappings

    @BeforeEach
    fun setup() {
        // Create mocks
        inputProvider = mockk(relaxed = true)
        mouseListener = mockk(relaxed = true)
        stringManager = mockk(relaxed = true)
        settingsManager = mockk(relaxed = true)
        sceneInitializer = mockk(relaxed = true)

        // Create test scene
        scene = Scene("TestScene", sceneInitializer)

        // Setup settings with defaults
        hardwareSettings = HardwareSettings().apply {
            leftStickDeadzone = 0.1f
            rightStickDeadzone = 0.1f
            triggerThreshold = 0.5f
            mouseSensitivity = 0.1f
            controllerSensitivity = 1.0f
        }

        gameplaySettings = GameplaySettings().apply {
            movementThreshold = 0.1f
            sprintThreshold = 0.5f
            jumpImpulse = 300f
            walkSpeed = 2f
            runSpeed = 5f
            rotationSpeed = 10f
            inputSmoothing = 5f
        }

        // Setup input mappings
        inputMappings = InputMappings().apply {
            jump = InputBinding(gamepadButton = 0, keyboardKey = GLFW.GLFW_KEY_SPACE)
            kickflip = InputBinding(gamepadButton = 2, keyboardKey = GLFW.GLFW_KEY_W)
            moveUp = InputBinding(gamepadAxis = 1)
            moveDown = InputBinding(gamepadAxis = -1)
            moveLeft = InputBinding(gamepadAxis = -1)
            moveRight = InputBinding(gamepadAxis = -1)
        }

        editorInputMappings = EditorInputMappings()

        // Setup settings manager mock
        every { settingsManager.engine } returns mockk {
            every { hardware } returns this@InputSystemTest.hardwareSettings
            every { editor } returns mockk {
                every { editorInputMappings } returns this@InputSystemTest.editorInputMappings
            }
        }
        every { settingsManager.project } returns mockk {
            every { gameplay } returns this@InputSystemTest.gameplaySettings
            every { inputMappings } returns this@InputSystemTest.inputMappings
        }

        // Ensure game input is processed by default
        every { inputProvider.isCursorDisabled() } returns true

        // Create input system
        inputSystem = InputSystem(
            inputProvider = inputProvider,
            mouseListener = mouseListener,
            settingsManager = settingsManager,
            stringManager = stringManager
        )

        // Initialize system
        inputSystem.init(scene)
    }

    // =========================================================================
    // INITIALIZATION TESTS
    // =========================================================================

    @Test
    fun `InputSystem initializes correctly`() {
        // Assert
        assertNotNull(inputSystem, "System should be created")
        assertEquals(ExecutionPriority.EARLY, inputSystem.priority, "System should run EARLY")
    }

    // =========================================================================
    // EVENT PUBLISHING TESTS
    // =========================================================================

    @Test
    fun `JumpPressed event published when jump button pressed`() {
        // Arrange
        val player = scene.gameObjectManager.createGameObject("TestPlayer")
        player.addComponent(InputStateComponent())
        scene.gameObjectManager.addGameObject(player)

        // Add EventSystem to scene
        val eventSystem = EventSystem()
        eventSystem.init(scene)
        scene.systemManager.addSystem(eventSystem)

        // Mock jump button press
        every { inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1) } returns true
        every { inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1) } returns FloatArray(6)
        val buttons = BooleanArray(10)
        buttons[0] = true // Jump button pressed
        every { inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1) } returns buttons

        var jumpPressedReceived = false
        eventSystem.subscribe<JumpPressed> { jumpPressedReceived = true }

        // Act
        inputSystem.update(0.016f)

        // Assert - verify event was published
        assertTrue(jumpPressedReceived, "JumpPressed event should be published on first press")
    }

    @Test
    fun `JumpReleased event published when jump button released`() {
        // Arrange
        val player = scene.createGameObject("TestPlayer")
        player.addComponent(InputStateComponent())
        scene.gameObjectManager.addGameObject(player)

        val eventSystem = EventSystem()
        eventSystem.init(scene)
        scene.systemManager.addSystem(eventSystem)

        // First press
        every { inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1) } returns true
        every { inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1) } returns FloatArray(6)
        val buttonsPressed = BooleanArray(10)
        buttonsPressed[0] = true
        every { inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1) } returns buttonsPressed

        // First update (press)
        inputSystem.update(0.016f)

        // Now prepare to release
        val buttonsReleased = BooleanArray(10)
        every { inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1) } returns buttonsReleased

        var jumpReleasedReceived = false
        eventSystem.subscribe<JumpReleased> { jumpReleasedReceived = true }

        // Act - release update
        inputSystem.update(0.016f)

        // Assert
        assertTrue(jumpReleasedReceived, "JumpReleased event should be published on release")
    }

    @Test
    fun `TrickInput events published for trick buttons`() {
        // Arrange
        val player = scene.createGameObject("TestPlayer")
        player.addComponent(InputStateComponent())
        scene.gameObjectManager.addGameObject(player)

        val eventSystem = EventSystem()
        eventSystem.init(scene)
        scene.systemManager.addSystem(eventSystem)

        every { inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1) } returns true
        every { inputProvider.getAxes(GLFW.GLFW_JOYSTICK_1) } returns FloatArray(6)
        val buttons = BooleanArray(10)
        buttons[2] = true // Kickflip button
        every { inputProvider.getButtons(GLFW.GLFW_JOYSTICK_1) } returns buttons

        val receivedTricks = mutableListOf<TrickInput>()
        eventSystem.subscribe<TrickInput> { receivedTricks.add(it) }

        // Act
        inputSystem.update(0.016f)

        // Assert
        assertTrue(
            receivedTricks.any { it.trickType == TrickType.KICKFLIP && it.isPressed },
            "Kickflip TrickInput event should be published"
        )
    }

    // =========================================================================
    // INPUT STATE TESTS
    // =========================================================================

    @Test
    fun `InputStateComponent is created for game objects`() {
        // Arrange
        val gameObject = scene.gameObjectManager.createGameObject("TestPlayer")
        val inputState = gameObject.addComponent(InputStateComponent())

        // Assert
        assertNotNull(inputState, "InputStateComponent should be created")
    }

    @Test
    fun `Multiple game objects can have input state`() {
        // Arrange
        val player1 = scene.gameObjectManager.createGameObject("Player1")
        val player2 = scene.gameObjectManager.createGameObject("Player2")
        val inputState1 = player1.addComponent(InputStateComponent())
        val inputState2 = player2.addComponent(InputStateComponent())

        // Assert
        assertNotNull(inputState1, "Player1 should have input state")
        assertNotNull(inputState2, "Player2 should have input state")
    }

    // =========================================================================
    // EDITOR INPUT TESTS
    // =========================================================================

    @Test
    fun `editorUpdate polls editor keyboard input`() {
        // Arrange
        val editorCamera = mockk<EditorCamera>(relaxed = true)
        scene.systemManager.addSystem(editorCamera)

        every { inputProvider.isKeyPressed(GLFW.GLFW_KEY_W) } returns true

        // Act
        inputSystem.editorUpdate(0.016f)

        // Assert
        verify { editorCamera.editorInput.reset() }
    }

    @Test
    fun `editorUpdate polls editor mouse input`() {
        // Arrange
        val editorCamera = mockk<EditorCamera>(relaxed = true)
        scene.systemManager.addSystem(editorCamera)

        every { mouseListener.isInsideViewport() } returns true
        every { mouseListener.getDx() } returns 10f
        every { mouseListener.getDy() } returns 5f

        // Act
        inputSystem.editorUpdate(0.016f)

        // Assert
        verify { editorCamera.editorInput.reset() }
    }

    // =========================================================================
    // JOYSTICK HANDLING TESTS
    // =========================================================================

    @Test
    fun `system handles missing joystick gracefully`() {
        // Arrange
        scene.gameObjectManager.createGameObject("TestPlayer").addComponent(InputStateComponent())
        every { inputProvider.isJoystickPresent(GLFW.GLFW_JOYSTICK_1) } returns false

        // Act & Assert - should not crash
        inputSystem.update(0.016f)
    }
}
