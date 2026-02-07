package com.pafoid.skate.engine.animation

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Matrix4f

@Serializable
class Bone(
    val index: Int,
    val name: String,
    @Contextual val localTransform: Matrix4f = Matrix4f()
) {
    val children = mutableListOf<Bone>()

    // Original Bind Pose Local Transform (Model's Skeleton)
    @Contextual
    val bindLocalTransform = Matrix4f(localTransform)

    // The matrix that goes from Model Space to Bone Local Space in Bind Pose
    @Contextual
    val inverseBindMatrix = Matrix4f()

    // The matrix that goes from Bone Local Space to Model Space in current pose
    @Contextual
    val worldTransform = Matrix4f()

    fun addChild(child: Bone) {
        children.add(child)
    }

    fun calculateWorldTransforms(parentTransform: Matrix4f) {
        parentTransform.mul(localTransform, worldTransform)
        for (child in children) {
            child.calculateWorldTransforms(worldTransform)
        }
    }

    fun copy(): Bone {
        val copy = Bone(index, name, Matrix4f(localTransform))
        copy.bindLocalTransform.set(bindLocalTransform)
        copy.inverseBindMatrix.set(inverseBindMatrix)
        for (child in children) {
            copy.addChild(child.copy())
        }
        return copy
    }
}
