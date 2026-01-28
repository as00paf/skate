package com.pafoid.skate.engine.animation

import com.pafoid.skate.engine.assets.AssimpLoader
import org.junit.jupiter.api.Test
import java.io.File

class SkeletonJointTest {

    @Test
    fun `list all joints in james model`() {
        val loader = AssimpLoader()
        val filePath = "assets/characters/james.dae"
        val preLoaded = loader.preLoadModel(filePath)
        
        assert(preLoaded.skeleton != null)
        val skeleton = preLoaded.skeleton!!
        
        println("Joints in ${filePath}:")
        printJoint(skeleton.rootJoint, 0)
    }

    private fun printJoint(joint: Joint, depth: Int) {
        val indent = "  ".repeat(depth)
        println("$indent- ${joint.name} (index: ${joint.index})")
        for (child in joint.children) {
            printJoint(child, depth + 1)
        }
    }
}
