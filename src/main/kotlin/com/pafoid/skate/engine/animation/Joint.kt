package com.pafoid.skate.engine.animation

import org.joml.Matrix4f

class Joint(
    val index: Int,
    val name: String,
    val localTransform: Matrix4f = Matrix4f()
) {
    val children = mutableListOf<Joint>()
    
    // Original Bind Pose Local Transform (Model's Skeleton)
    val bindLocalTransform = Matrix4f(localTransform)

    // The matrix that goes from Model Space to Joint Local Space in Bind Pose
    val inverseBindMatrix = Matrix4f()

    // The matrix that goes from Joint Local Space to Model Space in current pose
    val worldTransform = Matrix4f()

    fun addChild(child: Joint) {
        children.add(child)
    }

    fun calculateWorldTransforms(parentTransform: Matrix4f) {
        parentTransform.mul(localTransform, worldTransform)
        for (child in children) {
            child.calculateWorldTransforms(worldTransform)
        }
    }

    fun copy(): Joint {
        val copy = Joint(index, name, Matrix4f(localTransform))
        copy.bindLocalTransform.set(bindLocalTransform)
        copy.inverseBindMatrix.set(inverseBindMatrix)
        for (child in children) {
            copy.addChild(child.copy())
        }
        return copy
    }
}
