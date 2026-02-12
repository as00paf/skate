package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.controls.input.IInputBuffer
import com.pafoid.skate.engine.controls.input.IInputProvider
import com.pafoid.skate.engine.controls.listeners.GamepadConstants
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.player.PlayerState
import com.pafoid.skate.engine.prefabs.PrefabsGenerator
import com.pafoid.skate.engine.prefabs.Skateboard
import com.pafoid.skate.engine.prefabs.Skater
import com.pafoid.skate.engine.render.Camera
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.utils.StringManager
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.lwjgl.glfw.GLFW.GLFW_JOYSTICK_1
import kotlin.test.assertEquals

class PlayerControllerTest {

    private var skateboard: GameObject = Skateboard(mockk(relaxed = true))
    private var skater: GameObject = Skater("Skater", mockk(relaxed = true), skateboard)
    private lateinit var controller: PlayerController
    private val sceneManager = mockk<SceneManager>()
    
    @MockK
    private lateinit var inputProvider: IInputProvider
    
    @MockK
    private lateinit var scene: Scene

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        
        startKoin {
            modules(module {
                single { mockk<ResourceManager>(relaxed = true) }
                single { sceneManager }
                single<IInputProvider> { inputProvider }
                single { mockk<IInputBuffer>(relaxed = true) }
                single { mockk<PrefabsGenerator>(relaxed = true) }
                single { mockk<DebugDraw>(relaxed = true) }
                single { mockk<StringManager>(relaxed = true) }
                single { mockk<LoggerService>(relaxed = true) }
            })
        }
        
        val camera = Camera()
        every { scene.camera } returns camera
        every { scene.gameObjectManager.gameObjects } returns mutableListOf()
        every { scene.physics3d } returns mockk(relaxed = true)
        every { sceneManager.currentScene } returns scene

        every { inputProvider.keyBeginPress(any()) } returns false
        every { inputProvider.buttonBeginPress(any(), any()) } returns false

        val axes = FloatArray(6) { 0f }
        axes[GamepadConstants.AXIS_LEFT_TRIGGER] = -1.0f
        axes[GamepadConstants.AXIS_RIGHT_TRIGGER] = -1.0f
        every { inputProvider.getAxes(any()) } returns axes
        every { inputProvider.getButtons(any()) } returns BooleanArray(15) { false }
        every { inputProvider.isCursorDisabled() } returns false

        controller = skateboard.getComponent<PlayerController>()!!
        controller.start()
    }

    @AfterEach
    fun teardown() {
        stopKoin()
        unmockkAll()
    }

    /* @Test
     fun `test toggle state from riding to walking`() {
         controller.stateManager.transitionToState(PlayerState.RIDING)

         // Clear the specific mock and set a new one
         every { inputProvider.buttonBeginPress(GLFW_JOYSTICK_1, GamepadConstants.BUTTON_Y) } returns true

         controller.update(0.016f)
         controller.update(0.016f) // Update again to ensure state transition

         assertEquals(PlayerState.WALKING, controller.stateManager.currentState)
     }*/

    @Test
    fun `test toggle state from walking to riding`() {
        controller.stateManager.transitionToState(PlayerState.WALKING)

        every { inputProvider.buttonBeginPress(GLFW_JOYSTICK_1, GamepadConstants.BUTTON_Y) } returns true

        controller.update(0.016f)
        controller.update(0.016f) // Update again to ensure state transition

        assertEquals(PlayerState.RIDING, controller.stateManager.currentState)
    }

    /*  @Test
      fun `test snap to board logic during riding`() {
          controller.stateManager.transitionToState(PlayerState.RIDING)

          val transform = skater.getComponent<Transform>() ?: throw Error("No transform")
          transform.translation.set(1f, 1f, 1f)
          transform.rotation.set(45f, 45f, 45f)

          controller.update(0.016f)

          assertEquals(0f, transform.translation.x)
          assertEquals(0.02f, transform.translation.y)
          assertEquals(0f, transform.translation.z)

          assertEquals(0f, transform.rotation.x)
          assertEquals(90f, transform.rotation.y)
          assertEquals(0f, transform.rotation.z)
      }*/
}
