package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.models.CharacterModel
import com.pafoid.skate.engine.scenes.components.RenderComponent
import com.pafoid.skate.engine.render.PickingDraw
import com.pafoid.skate.engine.render.PickingMesh
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.Transform
import com.pafoid.skate.engine.scenes.components.toWorldMatrix
import org.joml.Matrix4f
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class PoseGizmo : Component(), KoinComponent {
    private val pickingDraw: PickingDraw by inject()

    private val boneMap = mutableMapOf<Int, Bone>()

    override fun editorUpdate(dt: Float) {
        val skeleton = (gameObject.getComponent<RenderComponent>()?.model as? CharacterModel)?.skeleton ?: return
        boneMap.clear()

        skeleton.getAllBones().forEach { bone ->
            val goTransform = gameObject.getComponent<Transform>()
        val modelMatrix = goTransform?.toWorldMatrix() ?: Matrix4f().identity()
            val boneWorldTransform = Matrix4f(modelMatrix).mul(bone.worldTransform)

            // Add a small scale to the bone's transform for visibility
            val gizmoTransform = Matrix4f(boneWorldTransform).scale(0.05f)

            val objectId = bone.index + BONE_ID_OFFSET
            boneMap[objectId] = bone

            pickingDraw.addMesh(
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
