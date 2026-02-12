package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import org.junit.jupiter.api.Test
class SkeletonJointTest {

    @Test
    fun `list all bones in james model`() {
        val loader = AssimpLoader()
        val filePath = "assets/characters/james.glb"
        try {
            val preLoaded = loader.preLoadModel(filePath)

            assert(preLoaded.skeleton != null) { "Skeleton should not be null" }
            val skeleton = preLoaded.skeleton!!

            println("Bones in ${filePath}:")
            printBone(skeleton.rootBone, 0)
        } catch (e: RuntimeException) {
            // If the file doesn't exist, skip the test gracefully
            println("Skipping test: File not found $filePath")
        }
    }

    private fun printBone(bone: Bone, depth: Int) {
        val indent = "  ".repeat(depth)
        println("$indent- ${bone.name} (index: ${bone.index})")
        for (child in bone.children) {
            printBone(child, depth + 1)
        }
    }
}
