package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.CameraFocusComponent
import com.pafoid.skate.engine.ecs.components.FreeFlyCameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.input.InputReader
import com.pafoid.skate.engine.input.Keys
import com.pafoid.skate.engine.ui.UIButtonComponent

/** One frame of free-fly camera input: move axes in -1..1, Shift, and the look drag in pixels (0 when not looking). */
data class FlyInput(
    val right: Float = 0f,
    val forward: Float = 0f,
    val up: Float = 0f,
    val fast: Boolean = false,
    val lookDx: Float = 0f,
    val lookDy: Float = 0f,
)

fun interface FlyInputSource {
    fun read(): FlyInput
}

/** WASD / E-Q / Shift from [reader]; mouse look while [lookHeld] (the engine passes the raw right button over the view). */
class ReaderFlyInputSource(private val reader: InputReader, private val lookHeld: () -> Boolean) : FlyInputSource {
    private fun axis(plus: Int, minus: Int) = (if (reader.isKeyDown(plus)) 1f else 0f) - (if (reader.isKeyDown(minus)) 1f else 0f)

    override fun read(): FlyInput {
        val looking = lookHeld()
        return FlyInput(
            right = axis(Keys.D, Keys.A),
            forward = axis(Keys.W, Keys.S),
            up = axis(Keys.E, Keys.Q),
            fast = reader.isKeyDown(Keys.LEFT_SHIFT),
            lookDx = if (looking) reader.mouseDeltaX() else 0f,
            lookDy = if (looking) reader.mouseDeltaY() else 0f,
        )
    }
}

/**
 * Runtime camera control while the scene runs (editor Play or the game):
 * - binds runtime UI buttons to [CameraFocusComponent] targets by button name, found anywhere in the scene
 *   (buttons need not share a canvas or ancestor with the camera);
 * - advances focus moves;
 * - applies [FreeFlyCameraComponent] input; any fly or look input cancels a focus move, so flying resumes from
 *   wherever the camera is.
 */
class CameraControlSystem(private val input: FlyInputSource) : System() {

    override val phase = SystemPhase.UPDATE

    private val focusCameras = query(CameraFocusComponent::class, Transform::class)
    private val flyCameras = query(FreeFlyCameraComponent::class, Transform::class)
    private val buttons = query(UIButtonComponent::class)

    override fun update(dt: Float) {
        bindButtons()
        if (!scene.isRunning) return
        val fly = if (flyCameras.entities.isNotEmpty()) input.read() else FlyInput()
        flyCameras.forEach { go ->
            val transform = go.getComponent<Transform>() ?: return@forEach
            val ff = go.getComponent<FreeFlyCameraComponent>() ?: return@forEach
            val moved = ff.fly(transform, fly.right, fly.forward, fly.up, fly.fast, dt)
            val turned = ff.look(transform, fly.lookDx, fly.lookDy)
            if (moved || turned) go.getComponent<CameraFocusComponent>()?.active = null
        }
        focusCameras.forEach { go ->
            val transform = go.getComponent<Transform>() ?: return@forEach
            go.getComponent<CameraFocusComponent>()?.advance(transform, dt)
        }
    }

    /** Binds every unbound button whose name a focus target names (cheap: only buttons with no handler are looked at). */
    private fun bindButtons() {
        val cameras = focusCameras.entities
        if (cameras.isEmpty()) return
        buttons.forEach { go ->
            val button = go.getComponent<UIButtonComponent>() ?: return@forEach
            if (button.onClick != null) return@forEach
            for (camera in cameras) {
                val focus = camera.getComponent<CameraFocusComponent>() ?: continue
                val target = focus.targets.firstOrNull { it.buttonName == go.name } ?: continue
                button.onClick = { focus.focus(target.name) }
                break
            }
        }
    }
}
