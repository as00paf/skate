package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.entities.Entity
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.render.PickingDraw
import com.pafoid.skate.engine.render.PickingMesh
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.scenes.components.toWorldMatrix
import org.joml.Matrix4f
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class PoseGizmo : Component(), KoinComponent {
    private val pickingDraw: PickingDraw by inject()

    private val jointMap = mutableMapOf<Int, Joint>()

    override fun editorUpdate(dt: Float) {
        val skeleton = gameObject.getComponent<Entity>()?.model?.skeleton ?: return
        jointMap.clear()

        skeleton.getAllJoints().forEach { joint ->
            val modelMatrix = gameObject.transform.toWorldMatrix()
            val jointWorldTransform = Matrix4f(modelMatrix).mul(joint.worldTransform)
            
            // Add a small scale to the joint's transform for visibility
            val gizmoTransform = Matrix4f(jointWorldTransform).scale(0.05f)

            val objectId = joint.index + BONE_ID_OFFSET
            jointMap[objectId] = joint

            pickingDraw.addMesh(
                PickingMesh(
                    vertices = CUBE_VERTICES,
                    transform = gizmoTransform,
                    objectId = objectId
                )
            )
        }
    }
    
    fun getJointById(id: Int): Joint? {
        return jointMap[id]
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
