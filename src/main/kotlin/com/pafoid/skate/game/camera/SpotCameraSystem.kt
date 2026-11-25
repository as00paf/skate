package com.pafoid.skate.game.camera

import com.pafoid.skate.engine.ecs.EcsChanges
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.MountFollowSystem
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemPhase
import com.pafoid.skate.engine.ecs.systems.TransformSystem
import com.pafoid.skate.engine.gameplay.camera.SpotFollow
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.game.player.SkateBehavior
import com.pafoid.skate.game.player.SpotCameraTuning
import org.joml.Vector3f

/**
 * Spot cameras that follow the skater (GPL-23 §7, D7): the camera named [SpotCameraTuning.cameraName] stands at the
 * object tagged [SpotCameraTuning.spotTag] nearest the skater (one spot per park zone), then follows the skater from
 * that side ([SpotFollow]) and keeps aiming at them. Entering another zone cuts to its spot. Tuning comes from the
 * skater's config. Runs while the scene plays, after the rider has moved.
 */
class SpotCameraSystem : System() {
    override val phase = SystemPhase.LATE_UPDATE
    override val runsAfter = listOf(MountFollowSystem::class)
    override val runsBefore = listOf(TransformSystem::class)

    private val riders = query(PlayerController::class, Transform::class)
    private var spots: List<GameObject> = emptyList()
    private var spotsVersion = -1L
    private var currentSpot: GameObject? = null

    /** The spot the camera films from now (null before the first frame or without spots). */
    val spot: GameObject? get() = currentSpot

    override fun init(scene: Scene) {
        super.init(scene)
        spotsVersion = -1L
        currentSpot = null
    }

    override fun update(dt: Float) {
        if (!scene.isRunning || scene.isPaused) return
        val (rider, behavior) = riders.entities.firstNotNullOfOrNull { go ->
            (go.getComponent<PlayerController>()?.behavior as? SkateBehavior)?.let { go to it }
        } ?: return
        val tuning = behavior.config.spotCamera
        val camera = scene.findFirstByName(tuning.cameraName)?.getComponent<Transform>() ?: return
        val target = rider.getComponent<Transform>()?.translation ?: return
        if (spotsVersion != EcsChanges.version) {
            spotsVersion = EcsChanges.version
            spots = scene.findByTag(tuning.spotTag)
        }
        val positions = spots.map { Vector3f(it.getComponent<Transform>()?.translation ?: Vector3f()) }
        val index = SpotFollow.nearest(positions, target)
        if (index < 0) return
        val goal = SpotFollow.desired(positions[index], target, tuning.minDistance, tuning.maxDistance)
        if (spots[index] !== currentSpot) {
            currentSpot = spots[index]
            camera.translation.set(goal) // a cut to the new zone's spot
        } else {
            SpotFollow.ease(camera.translation, goal, tuning.followSharpness, dt)
        }
        camera.lookAt(Vector3f(target).add(0f, tuning.aimHeight, 0f))
    }
}
