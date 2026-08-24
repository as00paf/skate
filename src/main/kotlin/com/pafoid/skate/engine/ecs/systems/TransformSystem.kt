package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.associateWithNotNull
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getAllComponents
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.utils.toRadiansF
import org.joml.Matrix4f
import org.joml.Quaternionf

class TransformSystem : System(priority = ExecutionPriority.EARLY) {

    private val cache = mutableMapOf<Transform, GameObject>()

    override fun init(scene: Scene) {
        super.init(scene)
        rebuildCache()
    }

    override fun update(dt: Float) {
        cache.forEach { (transform, go) ->
            updateTransform(go, transform)
        }
    }

    private fun updateTransform(gameObject: GameObject, transform: Transform) {
        val localMatrix = getLocalMatrix(transform)
        val parentTransform = gameObject.parent?.getComponent<Transform>()
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

    override fun destroy() {
        invalidateCache()
    }

    override fun invalidateCache() {
        cache.clear()
    }

    override fun rebuildCache() {
        cache.clear()
        cache.putAll(
            scene.getAllComponents<Transform>().associateWithNotNull { it.gameObject }
        )
    }
}