package com.pafoid.skate.engine.assets

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class AssimpLoaderTest {

    @Test
    fun `preLoadModel should load weights and joints from FBX`() {
        val loader = AssimpLoader()
        val path = "assets/characters/james.fbx"
        
        if (!File(path).exists()) {
            println("Skipping test: File not found $path")
            return
        }

        val model = loader.preLoadModel(path)
        
        assertNotNull(model.skeleton, "Skeleton should not be null for character model")
        assertTrue(model.parts.isNotEmpty(), "Model should have mesh parts")
        
        val part = model.parts[0]
        
        // Verify we have weights
        // weights array size is numVertices * 4
        assertTrue(part.weights.size > 0, "Weights array should not be empty")
        
        var hasWeights = false
        for (w in part.weights) {
            if (w > 0f) {
                hasWeights = true
                break
            }
        }
        assertTrue(hasWeights, "Mesh should have at least some non-zero weights")
        
        // Verify skeleton has bones
        assertTrue(model.skeleton!!.jointCount > 0, "Skeleton should have joints")
    }
}
