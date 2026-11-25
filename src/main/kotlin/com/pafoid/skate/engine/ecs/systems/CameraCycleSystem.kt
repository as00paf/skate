package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.CameraCycleComponent
import com.pafoid.skate.engine.ecs.components.ComponentSettings
import com.pafoid.skate.engine.ecs.components.FirstPersonCameraComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.gameplay.camera.CameraCycle
import com.pafoid.skate.engine.gameplay.camera.FirstPersonCameraRig
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.input.InputReader
import com.pafoid.skate.engine.ui.UIButtonComponent

/**
 * Runtime camera switching (GPL-22), in editor Play and the game:
 * - each [CameraCycleComponent] binds its UI button by name and reads its input action; every press makes the next
 *   camera of its list the active one, through [switchTo] (the engine publishes `CameraAction.SetCamera`). The
 *   current position in the cycle is the [activeCamera], so Restart or Stop/Play, which reselect the default
 *   camera, never leave the cycle out of step;
 * - drives every [FirstPersonCameraComponent] (see [FirstPersonCameraRig]).
 *
 * In edit mode nothing switches and first-person cameras stay where they were placed; in a paused Play nothing switches
 * either (clicks wait for Resume) and cameras hold still. In every mode an edit of a camera's settings shows on the
 * next frame (ECS-21): the camera snaps to the pose for the new settings instead of easing toward it (or not moving).
 * The first frame a camera is seen only records its settings, so loading a scene never moves a placed camera.
 */
class CameraCycleSystem(
    private val reader: InputReader?,
    private val activeCamera: () -> CameraComponent?,
    private val switchTo: (CameraComponent, Transform?) -> Unit,
) : System() {

    override val phase = SystemPhase.LATE_UPDATE
    override val runsAfter = listOf(MountFollowSystem::class)
    override val runsBefore = listOf(TransformSystem::class)
    override val runsWhilePaused: Boolean get() = true

    private val cycles = query(CameraCycleComponent::class)
    private val firstPerson = query(FirstPersonCameraComponent::class, Transform::class)
    private val buttons = query(UIButtonComponent::class)

    override fun update(dt: Float) {
        bindButtons()
        val running = scene.isRunning
        val paused = running && scene.isPaused
        if (!running) cycles.forEach { it.getComponent<CameraCycleComponent>()?.pendingClicks = 0 }
        else if (!paused) switchCameras()
        firstPerson.forEach { go ->
            val camera = go.getComponent<FirstPersonCameraComponent>() ?: return@forEach
            val transform = go.getComponent<Transform>() ?: return@forEach
            val settings = ComponentSettings.of(camera, RUNTIME_FIELDS)
            val edited = camera.state.settings.let { it != null && it != settings }
            camera.state.settings = settings
            if (!running) camera.state.initialized = false // the next Play snaps
            if (!camera.enabled) return@forEach
            if (!running && !edited) return@forEach
            if (paused && !edited) return@forEach
            val target = scene.findFirstByName(camera.targetName, exclude = go) ?: return@forEach
            val pose = FollowCameraSystem.followTarget(target)
            if (edited) {
                FirstPersonCameraRig.snap(camera, pose, transform)
                if (!running) camera.state.initialized = false
            } else {
                FirstPersonCameraRig.update(camera, pose, dt, transform)
            }
        }
    }

    private fun switchCameras() {
        cycles.forEach { go ->
            val cycle = go.getComponent<CameraCycleComponent>() ?: return@forEach
            if (!cycle.enabled) return@forEach
            var presses = cycle.pendingClicks
            cycle.pendingClicks = 0
            if (cycle.actionId.isNotEmpty() && reader?.wasActionPressed(cycle.actionId) == true) presses++
            if (presses > 0) advance(cycle, presses)
        }
    }

    /**
     * Camera objects of [cycle] found in the scene, in cycle order (missing names and non-camera objects are skipped).
     * Looked up by name rather than through `Component.gameObject`, which a freshly decoded scene may not have set.
     */
    fun camerasOf(cycle: CameraCycleComponent): List<Pair<GameObject, CameraComponent>> =
        CameraCycle.parseNames(cycle.cameraNames).mapNotNull { name ->
            scene.findFirstByName(name)?.let { go -> go.getComponent<CameraComponent>()?.let { go to it } }
        }

    private fun advance(cycle: CameraCycleComponent, presses: Int) {
        val cameras = camerasOf(cycle)
        if (cameras.isEmpty()) return
        val active = activeCamera()
        var index = cameras.indexOfFirst { it.second === active }
        repeat(presses) { index = CameraCycle.next(index, cameras.size) }
        val (go, next) = cameras[index]
        if (next === active) return
        go.getComponent<FirstPersonCameraComponent>()?.state?.initialized = false
        switchTo(next, go.getComponent<Transform>())
    }

    /** Binds every unbound button that a cycle names (cheap: only buttons with no handler are looked at). */
    private fun bindButtons() {
        val all = cycles.entities
        if (all.isEmpty()) return
        buttons.forEach { go ->
            val button = go.getComponent<UIButtonComponent>() ?: return@forEach
            if (button.onClick != null) return@forEach
            val cycle = all.firstNotNullOfOrNull { c ->
                c.getComponent<CameraCycleComponent>()?.takeIf { it.buttonName.isNotEmpty() && it.buttonName == go.name }
            } ?: return@forEach
            button.onClick = { cycle.pendingClicks++ }
        }
    }

    private companion object {
        /** Runtime state, not settings. */
        val RUNTIME_FIELDS = setOf("state")
    }
}
