package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.PlayerState
import com.pafoid.skate.engine.controls.IInputProvider
import com.pafoid.skate.engine.controls.JoystickListener
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneManager
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1
import org.lwjgl.glfw.GLFW.GLFW_KEY_Y
import kotlin.test.assertEquals

class PlayerControllerTest {

    private lateinit var gameObject: GameObject
    private lateinit var controller: PlayerController
    
    @MockK
    private lateinit var inputProvider: IInputProvider
    
    @MockK
    private lateinit var scene: Scene

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(SceneManager)
        
        val camera = com.pafoid.skate.engine.render.Camera()
        every { scene.camera } returns camera
        every { scene.gameObjects } returns mutableListOf()
        every { scene.physics3d } returns mockk(relaxed = true)
        every { SceneManager.getCurrentScene() } returns scene
        
        gameObject = GameObject("Player")
        val skater = GameObject("Skater")
        gameObject.addChild(skater)
        
        controller = PlayerController()
        controller.inputProvider = inputProvider
        gameObject.addComponent(controller)

        // Default behavior
        every { inputProvider.keyBeginPress(any()) } returns false
        every { inputProvider.buttonBeginPress(any(), any()) } returns false
        every { inputProvider.isKeyPressed(any()) } returns false
        every { inputProvider.getAxes(any()) } returns FloatArray(6) { 0f }
        every { inputProvider.getButtons(any()) } returns BooleanArray(15) { false }
        every { inputProvider.isCursorDisabled() } returns false
        
        // Initialize controller (to find the skater)
        controller.start()
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `test toggle state from riding to walking`() {
        // Given: Initially RIDING
        controller.state = PlayerState.RIDING
        
        // When: Y button is pressed (BUTTON_Y = 3)
        every { inputProvider.buttonBeginPress(GLFW_JOYSTICK_1, JoystickListener.BUTTON_Y) } returns true
        
        controller.update(0.016f)
        
        // Then: State should be WALKING
        assertEquals(PlayerState.WALKING, controller.state)
    }

    @Test
    fun `test toggle state from walking to riding`() {
        // Given: Initially WALKING
        controller.state = PlayerState.WALKING
        
        // When: Y button is pressed
        every { inputProvider.buttonBeginPress(GLFW_JOYSTICK_1, JoystickListener.BUTTON_Y) } returns true
        
        controller.update(0.016f)
        
        // Then: State should be RIDING
        assertEquals(PlayerState.RIDING, controller.state)
    }
}
