package com.pafoid.skate.engine.assets.data.models.animations

import kotlinx.serialization.Serializable

@Serializable
class Skeleton(
    val rootBone: Bone,
    val boneCount: Int,
) {
    val bones = arrayOfNulls<Bone>(boneCount)

    init {
        addBoneToMap(rootBone)
    }

    private fun addBoneToMap(bone: Bone) {
        if (bone.index in 0 until boneCount) {
            bones[bone.index] = bone
        }
        for (child in bone.children) {
            addBoneToMap(child)
        }
    }

    fun getBoneByName(name: String): Bone? {
        return bones.find { it?.name == name }
    }

    fun getAllBones(): List<Bone> {
        return bones.filterNotNull()
    }

    fun copy(): Skeleton {
        return Skeleton(rootBone.copy(), boneCount)
    }
}
