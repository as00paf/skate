package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.BoneAttachment
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.gameplay.appearance.SkeletonConfigs
import com.pafoid.skate.engine.gameplay.appearance.SocketMath
import com.pafoid.skate.engine.getComponent
import org.joml.Matrix4f

/**
 * Places every [BoneAttachment] child on its parent's skeleton socket (GPL-24 §6.2), after animation and before world
 * matrices are built, so `TransformSystem` composes it with the character's world matrix. Runs while paused and in
 * edit mode, so accessories stay on the head in the editor too.
 */
class BoneAttachmentSystem : System(priority = ExecutionPriority.LATE) {

    override val runsBefore = listOf(TransformSystem::class)
    override val runsWhilePaused get() = true

    private val attachments = query(BoneAttachment::class, Transform::class)
    private val matrix = Matrix4f()

    override fun update(dt: Float) {
        attachments.forEach { go ->
            val attachment = go.getComponent<BoneAttachment>() ?: return@forEach
            val transform = go.getComponent<Transform>() ?: return@forEach
            val character = go.parent ?: return@forEach
            val skeleton = character.getComponent<SkeletonComponent>() ?: return@forEach
            place(attachment, transform, character, skeleton)
        }
    }

    private fun place(attachment: BoneAttachment, transform: Transform, character: GameObject, skeleton: SkeletonComponent) {
        val resolved = SkeletonConfigs.resolve(SkeletonConfigs.configOf(character), skeleton.pose.skeleton)
        val socket = resolved.config.socket(attachment.socket) ?: return
        val bone = resolved.boneOf(socket.name) ?: return
        val global = skeleton.pose.globalTransforms.getOrNull(bone) ?: return
        SocketMath.attachment(global, socket, attachment.offset, attachment.rotationDeg, attachment.scale, matrix)
        SocketMath.decompose(matrix, transform.translation, transform.rotation, transform.scale)
    }
}
