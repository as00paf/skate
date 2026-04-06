package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.engine.assets.data.models.CharacterModel
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.toWorldMatrix
import com.pafoid.skate.engine.render.data.PickingMesh
import com.pafoid.skate.engine.render.renderer.PickingRenderer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4f
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
class PoseGizmo : Component(), KoinComponent {
    @Transient private val pickingRenderer: PickingRenderer by inject()

    @Transient private val boneMap = mutableMapOf<Int, Bone>()

    override fun editorUpdate(dt: Float) {
        val skeleton = (gameObject.getComponent<RenderComponent>()?.model as? CharacterModel)?.skeleton ?: return
        boneMap.clear()

        skeleton.getAllBones().forEach { bone ->
            val goTransform = gameObject.getComponent<Transform>()
        val modelMatrix = goTransform?.toWorldMatrix() ?: Matrix4f().identity()
            val boneWorldTransform = Matrix4f(modelMatrix).mul(bone.worldTransform)
            val gizmoTransform = Matrix4f(boneWorldTransform).scale(0.05f)

            val objectId = bone.index + BONE_ID_OFFSET
            boneMap[objectId] = bone

            pickingRenderer.addMesh(
                PickingMesh(
                    vertices = CUBE_VERTICES,
                    transform = gizmoTransform,
                    objectId = objectId
                )
            )
        }
    }

    fun getBoneById(id: Int): Bone? {
        return boneMap[id]
    }

    companion object {
        private const val BONE_ID_OFFSET = 1000000 // To differentiate from GameObject IDs
        private val CUBE_VERTICES = listOf(
            //-X
            Vector3f(-0.5f, -0.5f, -0.5f), Vector3f(-0.5f, -0.5f, 0.5f), Vector3f(-0.5f, 0.5f, 0.5f),
            Vector3f(-0.5f, 0.5f, 0.5f), Vector3f(-0.5f, 0.5f, -0.5f), Vector3f(-0.5f, -0.5f, -0.5f),
            //+X
            Vector3f(0.5f, -0.5f, -0.5f), Vector3f(0.5f, 0.5f, -0.5f), Vector3f(0.5f, 0.5f, 0.5f),
            Vector3f(0.5f, 0.5f, 0.5f), Vector3f(0.5f, -0.5f, 0.5f), Vector3f(0.5f, -0.5f, -0.5f),
            //-Y
            Vector3f(-0.5f, -0.5f, -0.5f), Vector3f(0.5f, -0.5f, -0.5f), Vector3f(0.5f, -0.5f, 0.5f),
            Vector3f(0.5f, -0.5f, 0.5f), Vector3f(-0.5f, -0.5f, 0.5f), Vector3f(-0.5f, -0.5f, -0.5f),
            //+Y
            Vector3f(-0.5f, 0.5f, -0.5f), Vector3f(-0.5f, 0.5f, 0.5f), Vector3f(0.5f, 0.5f, 0.5f),
            Vector3f(0.5f, 0.5f, 0.5f), Vector3f(0.5f, 0.5f, -0.5f), Vector3f(-0.5f, 0.5f, -0.5f),
            //-Z
            Vector3f(-0.5f, -0.5f, -0.5f), Vector3f(-0.5f, 0.5f, -0.5f), Vector3f(0.5f, 0.5f, -0.5f),
            Vector3f(0.5f, 0.5f, -0.5f), Vector3f(0.5f, -0.5f, -0.5f), Vector3f(-0.5f, -0.5f, -0.5f),
            //+Z
            Vector3f(-0.5f, -0.5f, 0.5f), Vector3f(0.5f, -0.5f, 0.5f), Vector3f(0.5f, 0.5f, 0.5f),
            Vector3f(0.5f, 0.5f, 0.5f), Vector3f(-0.5f, 0.5f, 0.5f), Vector3f(-0.5f, -0.5f, 0.5f)
        )
    }
}