package com.pafoid.skate.engine.assets

import org.junit.jupiter.api.Test
import java.io.File

class SkateboardModelInspectionTest {

    @Test
    fun `inspect skateboard dimensions`() {
        val loader = AssimpLoader()
        val filePath = "assets/obj/skateboard_free_model.glb"
        val file = File(filePath)
        if (!file.exists()) {
            println("File not found: ${file.absolutePath}")
            return
        }

        val preLoaded = loader.preLoadModel(filePath)
        println("Model: $filePath")
        
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var maxZ = Float.MIN_VALUE
        
        preLoaded.parts.forEach { part ->
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
        
        println("Bounding Box (after 0.01 scale):")
        println("  Min: ($minX, $minY, $minZ)")
        println("  Max: ($maxX, $maxY, $maxZ)")
        println("  Dimensions: (${maxX-minX}, ${maxY-minY}, ${maxZ-minZ})")
    }
}
