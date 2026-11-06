package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.gameplay.MotionMath
import com.pafoid.skate.engine.gameplay.MountMath
import com.pafoid.skate.engine.gameplay.Mountable
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.toDegreesF
import com.pafoid.skate.engine.utils.toRadiansF
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Places a ridden mount's transform under its rider after physics has written the rider's interpolated pose for
 * this frame, so the drawn board never trails the rider by a physics step. Runs before world matrices are built.
 */
class MountFollowSystem : System(priority = ExecutionPriority.LATE) {

    override val runsBefore = listOf(TransformSystem::class)

    private val riders = query(PlayerController::class, Transform::class, topLevelOnly = true)

    private val rotation = Quaternionf()
    private val euler = Vector3f()
    private val position = Vector3f()

    override fun update(dt: Float) {
        if (!scene.isRunning) return
        riders.forEach { go ->
            val mountable = go.getComponent<PlayerController>()?.behavior as? Mountable ?: return@forEach
            if (!mountable.mounted) return@forEach
            val rider = go.getComponent<Transform>() ?: return@forEach
            val mount = findMountObject(scene, mountable.mountTargetName, go)?.getComponent<Transform>() ?: return@forEach
            follow(rider, mount, mountable.mountOffset, mountable.mountSpin)
        }
    }

    /**
     * Sets [mount] to the pose under [rider] given the mount [offset] (see [MountMath.mountPosition]), turned by
     * [spin] in the mount's own frame when given ([Mountable.mountSpin]).
     */
    fun follow(rider: Transform, mount: Transform, offset: Vector3f, spin: Quaternionf? = null) {
        val r = rider.rotation
        rotation.rotationXYZ(r.x.toRadiansF(), r.y.toRadiansF(), r.z.toRadiansF())
        val yaw = MotionMath.yawOfRotation(rotation)
        mount.translation.set(MountMath.mountPosition(rider.translation, yaw, offset, position))
        rotation.rotationY(yaw)
        spin?.let { rotation.mul(it) }
        MotionMath.eulerXYZ(rotation, euler)
        mount.rotation.set(euler.x.toDegreesF(), euler.y.toDegreesF(), euler.z.toDegreesF())
    }
}
