package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.toRadiansF
import org.joml.Matrix4f
import org.joml.Vector3f

class TransformSystem : System(priority = ExecutionPriority.EARLY) {

    override val phase = SystemPhase.LATE_UPDATE

    private val transforms = query(Transform::class, includeDisabled = true)

    override fun start() {
        update(0f)
    }

    override fun update(dt: Float) {
        transforms.forEach { go ->
            go.getComponent<Transform>()?.let { updateTransform(it) }
        }
    }

    fun updateTransform(transform: Transform) {
        updateLocalMatrix(transform)
        val parentTransform = transform.gameObject?.parent?.getComponent<Transform>()// TODO: Could be cached
        if (parentTransform != null) {
            parentTransform.worldMatrix.mul(transform.localMatrix, transform.worldMatrix)
        } else {
            transform.worldMatrix.set(transform.localMatrix)
        }
    }

    private fun updateLocalMatrix(transform: Transform): Matrix4f {
        with(transform) {
            localMatrix.identity()
            localMatrix.translate(translation)
            localMatrix.rotate(rotation.x.toRadiansF(), Vector3f(1f, 0f, 0f))
            localMatrix.rotate(rotation.y.toRadiansF(), Vector3f(0f, 1f, 0f))
            localMatrix.rotate(rotation.z.toRadiansF(), Vector3f(0f, 0f, 1f))
            localMatrix.scale(scale)
            return localMatrix
        }
    }
}
