package com.pafoid.skate.engine.ecs.components

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

/**
 * Pins this object to a socket of its parent's skeleton (GPL-24 §6.2): hats, caps, glasses. Put it on a child of the
 * character next to the child's own `RenderComponent`; `BoneAttachmentSystem` sets the child's transform every frame.
 * [offset] (metres), [rotationDeg] (Euler XYZ) and [scale] adjust the item on the socket.
 */
@Serializable
data class BoneAttachment(
    var socket: String = "",
    @Contextual val offset: Vector3f = Vector3f(),
    @Contextual val rotationDeg: Vector3f = Vector3f(),
    @Contextual val scale: Vector3f = Vector3f(1f, 1f, 1f),
) : Component()
