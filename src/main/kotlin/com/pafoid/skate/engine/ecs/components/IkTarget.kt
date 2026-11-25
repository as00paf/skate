package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/**
 * A 3D IK target (ANI-28 §4.1): this object's world transform is where a chain of an [IkSetup] reaches (position, and
 * rotation for feet and hands with `alignRotation`). Put it anywhere: a child of the board (front foot), of a rail, of
 * the character (look focus) or free in the level; moving it with the normal gizmo moves the limb. Chains refer to it
 * by the object's name. [label] and [color] are for the viewport gizmo (ANI-28 slice e).
 */
@Serializable
data class IkTarget(
    var label: String = "",
    @Contextual val color: Vector3f = Vector3f(1f, 0.45f, 0.1f),
) : Component()
