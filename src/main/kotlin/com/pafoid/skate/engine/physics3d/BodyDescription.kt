package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.Collider3D
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.toRadiansF
import org.joml.Quaternionf
import org.joml.Vector3f

/** One collider's [shape], placed at [offset] in the body's local frame. */
data class ShapePart(val shape: ColliderShape, val offset: Vector3f)

/**
 * Backend-neutral description of the body a GameObject asks for, built from its [RigidBody3D], [Collider3D]s and
 * [Transform] (PHY-17). Every backend builds its native body from this, so mass, motion type, shapes, layers and CCD
 * follow the same rules everywhere.
 *
 * @property motionType resolved type: a body with a static-only collider (triangle mesh) is [BodyType.Static]
 *   whatever its [RigidBody3D.bodyType].
 * @property mass kilograms; 0 for static bodies. A [BodyType.Dynamic] body with no mass is treated as static.
 * @property parts one entry per collider, in component order. Empty means "no collider" (backends use a unit box).
 * @property rotation from the Transform's Euler XYZ degrees.
 * @property ccdRadius swept-sphere radius in metres when CCD is on, else null.
 * @property boxMargin margin of the object's [BoxCollider3D], if any (Bullet applies it to the whole body shape).
 */
class BodyDescription(
    val motionType: BodyType,
    val mass: Float,
    val parts: List<ShapePart>,
    val position: Vector3f,
    val rotation: Quaternionf,
    val scale: Vector3f,
    val friction: Float,
    val restitution: Float,
    val linearDamping: Float,
    val angularDamping: Float,
    val collisionLayer: Int,
    val collisionMask: Int,
    val isTrigger: Boolean,
    val ccdRadius: Float?,
    val boxMargin: Float?,
) {
    /** True when the body is a single collider with no offset, so backends can use its shape without a compound. */
    val isSingleCentredShape: Boolean get() = parts.size == 1 && parts[0].offset.lengthSquared() == 0f

    companion object {
        /** Describes [go]'s body, or returns null when it has no [RigidBody3D]. */
        fun of(go: GameObject): BodyDescription? {
            val rb = go.getComponent<RigidBody3D>() ?: return null
            val colliders = go.components.filterIsInstance<Collider3D>()
            val transform = go.getComponent<Transform>()
            val staticOnly = colliders.any { it.staticOnly }
            val motionType = when {
                rb.bodyType == BodyType.Static || staticOnly -> BodyType.Static
                rb.bodyType == BodyType.Kinematic -> BodyType.Kinematic
                rb.mass <= 0f -> BodyType.Static
                else -> BodyType.Dynamic
            }
            val parts = parts(colliders)
            val scale = transform?.scale?.let { Vector3f(it) } ?: Vector3f(1f)
            return BodyDescription(
                motionType = motionType,
                mass = if (motionType == BodyType.Static) 0f else rb.mass,
                parts = parts,
                position = transform?.translation?.let { Vector3f(it) } ?: Vector3f(),
                rotation = transform?.let { rotationOf(it) } ?: Quaternionf(),
                scale = scale,
                friction = rb.friction,
                restitution = rb.restitution,
                linearDamping = rb.linearDamping,
                angularDamping = rb.angularDamping,
                collisionLayer = rb.collisionLayer,
                collisionMask = rb.collisionMask,
                isTrigger = rb.isTrigger,
                ccdRadius = if (rb.useCCD && motionType != BodyType.Static) ccdRadius(parts.map { it.shape }, scale) else null,
                boxMargin = go.getComponent<BoxCollider3D>()?.margin,
            )
        }

        /** One [ShapePart] per collider, in order. */
        fun parts(colliders: List<Collider3D>): List<ShapePart> = colliders.map { ShapePart(it.toShape(), Vector3f(it.offset)) }

        /** The Transform's Euler XYZ degrees as a quaternion. */
        fun rotationOf(transform: Transform): Quaternionf {
            val r = transform.rotation
            return Quaternionf().rotationXYZ(r.x.toRadiansF(), r.y.toRadiansF(), r.z.toRadiansF())
        }

        /**
         * Half of the thinnest collider dimension: a swept sphere this size cannot skip over geometry thinner than the
         * body. Hulls and meshes use 0.1 m; the result is at least 1 cm.
         */
        fun ccdRadius(shapes: List<ColliderShape>, scale: Vector3f): Float {
            val smallest = shapes.minOfOrNull { shape ->
                when (shape) {
                    is ColliderShape.Box ->
                        minOf(shape.halfExtents.x * scale.x, shape.halfExtents.y * scale.y, shape.halfExtents.z * scale.z)
                    is ColliderShape.Capsule -> shape.radius * minOf(scale.x, scale.z)
                    is ColliderShape.Cylinder -> minOf(shape.radius * minOf(scale.x, scale.z), shape.height / 2f * scale.y)
                    is ColliderShape.ConvexHull, is ColliderShape.CompoundHull, is ColliderShape.TriangleMesh -> 0.1f
                }
            } ?: 0.1f
            return (smallest * 0.5f).coerceAtLeast(0.01f)
        }
    }
}
