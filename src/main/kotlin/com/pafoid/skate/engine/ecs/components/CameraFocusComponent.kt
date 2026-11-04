package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UIButtonComponent
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.exp

/** A named camera viewpoint: [position] and Euler [rotation] in degrees, in the camera's (parent's) space. */
@Serializable
class CameraFocusTarget(
    var name: String = "",
    /** Name of a runtime UI button (anywhere in the scene) that flies the camera here when clicked; empty for none. */
    var buttonName: String = "",
    @Contextual val position: Vector3f = Vector3f(),
    @Contextual val rotation: Vector3f = Vector3f(),
) {
    companion object {
        /** The viewpoint at [eye] looking at [target] (camera convention of [Transform.lookAt]: +pitch looks down). */
        fun lookingAt(name: String, eye: Vector3f, target: Vector3f, buttonName: String = ""): CameraFocusTarget {
            val pose = Transform(Vector3f(eye)).also { it.lookAt(target) }
            return CameraFocusTarget(name, buttonName, Vector3f(eye), Vector3f(pose.rotation))
        }
    }
}

/**
 * Put on a camera object to fly it between named viewpoints while the scene runs (Play in the editor, or the game).
 * Each [targets] entry can be bound to a runtime UI button by name; clicking it calls [focus]. The move is exponential
 * smoothing of position and rotation at [sharpness] per second (frame-rate independent, frame delta clamped for
 * hitches), yaw taking the short way round.
 */
@Serializable
class CameraFocusComponent(
    val targets: MutableList<CameraFocusTarget> = mutableListOf(),
    /** Higher is faster; about 4.6 / sharpness seconds to cover 99% of the way. */
    var sharpness: Float = 4f,
) : Component() {

    /** The viewpoint being approached, or null when the camera is left alone. */
    @Transient var active: CameraFocusTarget? = null
        private set

    @Transient private var buttonsBound = false

    /** Starts flying to the target called [name]; false when there is no such target. */
    fun focus(name: String): Boolean {
        active = targets.firstOrNull { it.name == name } ?: return false
        return true
    }

    override fun update(dt: Float) {
        super.update(dt)
        if (!buttonsBound) bindButtons()
        val target = active ?: return
        val transform = gameObject?.getComponent<Transform>() ?: return
        if (step(transform, target, smoothing(sharpness, dt))) active = null
    }

    private fun bindButtons() {
        val owner = gameObject ?: return
        var root: GameObject = owner
        while (true) root = root.parent ?: break
        val byName = HashMap<String, UIButtonComponent>()
        fun collect(go: GameObject) {
            go.getComponent<UIButtonComponent>()?.let { byName[go.name] = it }
            go.children.forEach { collect(it) }
        }
        collect(root)
        targets.filter { it.buttonName.isNotEmpty() }.forEach { target ->
            byName[target.buttonName]?.onClick = { focus(target.name) }
        }
        buttonsBound = true
    }

    companion object {
        /** Longest frame step applied, so a hitch does not jump the camera. */
        const val MAX_DT = 0.1f
        private const val SNAP_DISTANCE = 0.01f
        private const val SNAP_DEGREES = 0.05f

        /** Fraction of the remaining distance to cover this frame; composes across frame splits. */
        fun smoothing(sharpness: Float, dt: Float): Float = 1f - exp(-sharpness * dt.coerceIn(0f, MAX_DT))

        /** Signed shortest difference `to - from` in degrees, in (-180, 180]. */
        fun deltaDegrees(from: Float, to: Float): Float {
            var d = (to - from) % 360f
            if (d > 180f) d -= 360f
            if (d <= -180f) d += 360f
            return d
        }

        /** Moves [transform] the fraction [k] towards [target]; snaps and returns true once there. */
        fun step(transform: Transform, target: CameraFocusTarget, k: Float): Boolean {
            transform.translation.lerp(target.position, k)
            val r = transform.rotation
            r.x += deltaDegrees(r.x, target.rotation.x) * k
            r.y += deltaDegrees(r.y, target.rotation.y) * k
            r.z += deltaDegrees(r.z, target.rotation.z) * k
            val arrived = transform.translation.distance(target.position) < SNAP_DISTANCE &&
                abs(deltaDegrees(r.x, target.rotation.x)) < SNAP_DEGREES &&
                abs(deltaDegrees(r.y, target.rotation.y)) < SNAP_DEGREES &&
                abs(deltaDegrees(r.z, target.rotation.z)) < SNAP_DEGREES
            if (arrived) {
                transform.translation.set(target.position)
                r.set(target.rotation)
            }
            return arrived
        }
    }
}
