package com.pafoid.skate.engine.gameplay

import com.pafoid.skate.engine.gameplay.ground.GroundInfo
import org.joml.Vector3f

/** What a behavior asks the motion system to do with its mount (board, vehicle, horse...). */
enum class MountRequest { NONE, MOUNT, DISMOUNT }

/**
 * Implemented by a [CharacterBehavior] that can be mounted on and off something in the scene.
 * The motion system resolves [mountTargetName] to an object, reports its distance through
 * [CharacterInput.mountDistance] and carries out the [MountRequest] the behavior returns.
 */
interface Mountable {
    var mounted: Boolean

    /** Name of the scene object that is mounted. */
    val mountTargetName: String

    /**
     * Mount centre relative to the rider's feet, in the rider's heading frame (local axes at yaw 0; +Z is forward, the board's nose).
     * Mounting snaps the rider onto the mount with this offset; while ridden the mount follows it.
     */
    val mountOffset: Vector3f

    /** Height in meters the character stands above the ground while mounted (the mount's thickness). */
    val rideHeight: Float get() = 0f

    /** Camera framing for the current mode (mounted or not), or null to leave the camera alone. */
    val cameraPreset: CameraPreset? get() = null
}

/** Physics state of a mount body: carried (kinematic, collides with nothing) while ridden, a free body otherwise. */
data class MountBodyState(val kinematic: Boolean, val collisionMask: Int)

/** Third-person framing: distance behind the character and height of the look-at point. */
data class CameraPreset(val distance: Float, val height: Float)

object MountMath {

    /** Hover speed per meter of height error, and its cap. */
    private const val HOVER_GAIN = 12f
    private const val HOVER_MAX_SPEED = 2f

    /** Extra height above [rideHeight] past which the rider counts as airborne (ollie) and is not held. */
    const val HOVER_AIRBORNE_MARGIN = 0.03f

    /**
     * Vertical velocity that holds the feet [rideHeight] above the surface, or null when the rider is
     * airborne or no surface is below. [distance] is the feet-to-surface distance.
     */
    fun hoverVelocity(found: Boolean, distance: Float, rideHeight: Float): Float? {
        if (!found || distance > rideHeight + HOVER_AIRBORNE_MARGIN) return null
        return ((rideHeight - distance) * HOVER_GAIN).coerceIn(-HOVER_MAX_SPEED, HOVER_MAX_SPEED)
    }

    /** Layer of mount bodies; characters never collide with it so they can walk through/over their mount. */
    const val MOUNT_LAYER = 2

    /** While ridden the mount is carried by the character, so it collides with nothing; otherwise with everything. */
    fun mountMask(mounted: Boolean): Int = if (mounted) 0 else -1

    /** Character collision mask that ignores [MOUNT_LAYER]. */
    fun characterMask(current: Int): Int = current and MOUNT_LAYER.inv()

    fun mountBodyState(mounted: Boolean) = MountBodyState(kinematic = mounted, collisionMask = mountMask(mounted))

    /** Mount centre under a rider whose feet are at [feet] with heading [yaw] (0 = +Z). */
    fun mountPosition(feet: Vector3f, yaw: Float, offset: Vector3f, dest: Vector3f = Vector3f()): Vector3f =
        dest.set(offset).rotateY(yaw).add(feet)

    /** Rider feet that put the mount at [mountPosition] when the rider takes the mount's heading [mountYaw]. */
    fun riderFeet(mountPosition: Vector3f, mountYaw: Float, offset: Vector3f, dest: Vector3f = Vector3f()): Vector3f =
        dest.set(offset).rotateY(mountYaw).negate().add(mountPosition)

    /**
     * Grounded test for a character. Mounted, the feet are held [rideHeight] above the surface (on the deck), so
     * the grounded band is raised by that much; on foot it is the probe's own result.
     */
    fun grounded(ground: GroundInfo, mounted: Boolean, rideHeight: Float, groundedDistance: Float): Boolean {
        if (!mounted) return ground.standing
        return ground.found && ground.walkable && ground.distance <= rideHeight + groundedDistance
    }

    /** True when a mount at [distance] meters can be mounted; NaN (no target in the scene) never can. */
    fun inRange(distance: Float, range: Float): Boolean = !distance.isNaN() && distance <= range

    /** Decides what a toggle press does: dismount when mounted, otherwise mount if in range. */
    fun toggle(mounted: Boolean, pressed: Boolean, distance: Float, range: Float): MountRequest = when {
        !pressed -> MountRequest.NONE
        mounted -> MountRequest.DISMOUNT
        inRange(distance, range) -> MountRequest.MOUNT
        else -> MountRequest.NONE
    }
}
