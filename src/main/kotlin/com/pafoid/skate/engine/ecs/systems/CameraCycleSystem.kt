package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.CameraCycleComponent
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
 * In edit mode nothing switches and first-person cameras stay where they were placed.
 */
class CameraCycleSystem(
    private val reader: InputReader?,
    private val activeCamera: () -> CameraComponent?,
    private val switchTo: (CameraComponent, Transform?) -> Unit,
) : System() {

    override val phase = SystemPhase.LATE_UPDATE
    override val runsAfter = listOf(MountFollowSystem::class)
    override val runsBefore = listOf(TransformSystem::class)

    private val cycles = query(CameraCycleComponent::class)
    private val firstPerson = query(FirstPersonCameraComponent::class, Transform::class)
    private val buttons = query(UIButtonComponent::class)

    override fun update(dt: Float) {
        bindButtons()
        if (!scene.isRunning) {
            cycles.forEach { it.getComponent<CameraCycleComponent>()?.pendingClicks = 0 }
            firstPerson.forEach { it.getComponent<FirstPersonCameraComponent>()?.state?.initialized = false }
            return
        }
        cycles.forEach { go ->
            val cycle = go.getComponent<CameraCycleComponent>() ?: return@forEach
            if (!cycle.enabled) return@forEach
            var presses = cycle.pendingClicks
            cycle.pendingClicks = 0
            if (cycle.actionId.isNotEmpty() && reader?.wasActionPressed(cycle.actionId) == true) presses++
            if (presses > 0) advance(cycle, presses)
        }
        firstPerson.forEach { go ->
            val camera = go.getComponent<FirstPersonCameraComponent>() ?: return@forEach
            val transform = go.getComponent<Transform>() ?: return@forEach
            if (!camera.enabled) return@forEach
            val target = scene.findFirstByName(camera.targetName, exclude = go) ?: return@forEach
            FirstPersonCameraRig.update(camera, FollowCameraSystem.followTarget(target), dt, transform)
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
}
