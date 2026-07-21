package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.io.File

class AssimpModelInspectionTest {

    @Test
    fun `inspect james animations`() {
        val loader = AssimpLoader(mockk(), mockk())
        val filePath = "assets/characters/james.dae"
        val file = File(filePath)
        if (!file.exists()) {
            println("File not found: ${file.absolutePath}")
            return
        }

        val preLoaded = loader.loadModel(filePath)
        println("Model: $filePath")
        
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var maxZ = Float.MIN_VALUE

        preLoaded.mesh.forEach { part ->
            for (i in part.vertices.indices step 3) {
                val x = part.vertices[i]
                val y = part.vertices[i+1]
                val z = part.vertices[i+2]
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (z < minZ) minZ = z
                if (z > maxZ) maxZ = z
            }
        }
        
        println("Bounding Box:")
        println("  Min: ($minX, $minY, $minZ)")
        println("  Max: ($maxX, $maxY, $maxZ)")
        println("  Dimensions: (${maxX-minX}, ${maxY-minY}, ${maxZ-minZ})")
        
        println("Skeleton: ${if (preLoaded.skeleton != null) "Yes" else "No"}")
        preLoaded.skeleton?.let {
            println("Bone count: ${it.boneCount}")
        }

        println("Mesh Parts: ${preLoaded.mesh.size}")
        preLoaded.mesh.forEachIndexed { index, part ->
            println("  - Part $index: ${part.vertices.size / 3} vertices, Material: ${part.material.baseColorTexture?.filePath ?: "Default"}")
        }
    }
}
