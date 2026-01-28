package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.player.state.PlayerState
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

        every { inputProvider.keyBeginPress(any()) } returns false
        every { inputProvider.buttonBeginPress(any(), any()) } returns false
        every { inputProvider.isKeyPressed(any()) } returns false
        val axes = FloatArray(6) { 0f }
        axes[JoystickListener.AXIS_LEFT_TRIGGER] = -1.0f
        axes[JoystickListener.AXIS_RIGHT_TRIGGER] = -1.0f
        every { inputProvider.getAxes(any()) } returns axes
        every { inputProvider.getButtons(any()) } returns BooleanArray(15) { false }
        every { inputProvider.isCursorDisabled() } returns false
        
        controller.start()
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `test toggle state from riding to walking`() {
        controller.stateManager.transitionToState(PlayerState.RIDING)
        
        every { inputProvider.buttonBeginPress(GLFW_JOYSTICK_1, JoystickListener.BUTTON_Y) } returns true
        
        controller.update(0.016f)
        
        assertEquals(PlayerState.WALKING, controller.stateManager.currentState)
    }

    @Test
    fun `test toggle state from walking to riding`() {
        controller.stateManager.transitionToState(PlayerState.WALKING)
        
        every { inputProvider.buttonBeginPress(GLFW_JOYSTICK_1, JoystickListener.BUTTON_Y) } returns true
        every { inputProvider.isKeyPressed(any()) } returns false
        
        controller.update(0.016f)
        
        assertEquals(PlayerState.RIDING, controller.stateManager.currentState)
    }

    @Test
    fun `test snap to board logic during riding`() {
        controller.stateManager.transitionToState(PlayerState.RIDING)
        val skater = gameObject.children.find { it.name == "Skater" }!!
        
        skater.transform.translation.set(1f, 1f, 1f)
        skater.transform.rotation.set(45f, 45f, 45f)
        
        controller.update(0.016f)
        
        assertEquals(0f, skater.transform.translation.x)
        assertEquals(0.02f, skater.transform.translation.y)
        assertEquals(0f, skater.transform.translation.z)
        
        assertEquals(0f, skater.transform.rotation.x)
        assertEquals(90f, skater.transform.rotation.y)
        assertEquals(0f, skater.transform.rotation.z)
    }
}
