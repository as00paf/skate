package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getAllComponents
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.toRadiansF
import org.joml.Matrix4f
import org.joml.Quaternionf

class TransformSystem : System(priority = ExecutionPriority.EARLY) {

    private val cache = mutableListOf<Transform>()

    override fun init(scene: Scene) {
        super.init(scene)
        rebuildCache()
        cacheDirty = false
    }

    override fun start() {
        cacheDirty = true
    }

    override fun update(dt: Float) {
        if (cacheDirty) rebuildCache()
        cache.forEach { transform ->
            updateTransform(transform)
        }
    }

    private fun updateTransform(transform: Transform) {
        val localMatrix = getLocalMatrix(transform)
        val parentTransform = transform.gameObject?.parent?.getComponent<Transform>()
        if (parentTransform != null) {
            parentTransform.worldMatrix.mul(localMatrix, transform.worldMatrix)
        } else {
            transform.worldMatrix.set(localMatrix)
        }
    }

    private val matrix = Matrix4f()

    private fun getLocalMatrix(transform: Transform): Matrix4f {
        with(transform) {
            matrix.identity()
            matrix.translate(translation)
            matrix.rotate(Quaternionf(rotation.x.toRadiansF(), rotation.y.toRadiansF(), rotation.z.toRadiansF(), 1f))
            matrix.scale(scale)
        }
        return matrix
    }

    override fun invalidateCache() {
        cache.clear()
        cacheDirty = true
    }

    override fun rebuildCache() {
        cache.clear()
        cache.addAll(scene.getAllComponents<Transform>())
    }
}