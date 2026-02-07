package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.assets.AssimpLoader
import org.junit.jupiter.api.Test
class SkeletonJointTest {

    @Test
    fun `list all bones in james model`() {
        val loader = AssimpLoader()
        val filePath = "assets/characters/james.dae"
        val preLoaded = loader.preLoadModel(filePath)

        assert(preLoaded.skeleton != null)
        val skeleton = preLoaded.skeleton!!

        println("Bones in ${filePath}:")
        printBone(skeleton.rootBone, 0)
    }

    private fun printBone(bone: Bone, depth: Int) {
        val indent = "  ".repeat(depth)
        println("$indent- ${bone.name} (index: ${bone.index})")
        for (child in bone.children) {
            printBone(child, depth + 1)
        }
    }
}
