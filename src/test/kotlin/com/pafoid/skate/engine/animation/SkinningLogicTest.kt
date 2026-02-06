package com.pafoid.skate.engine.animation

import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SkinningLogicTest {

    @Test
    fun `Verify Skinning Math with Unit Scaling`() {
        // 1. Simulate Setup
        val unitScale = 0.01f // CM to M
        
        // Original File Data (Centimeters)
        val originalVertex = Vector3f(0f, 100f, 0f) // Head at 100cm
        val jointPos = Vector3f(0f, 100f, 0f) // Joint at Head
        
        // 2. Load Process (Vertex Scaling)
        val rootTransform = Matrix4f().scale(unitScale)
        val scaledVertex = Vector3f(originalVertex)
        rootTransform.transformPosition(scaledVertex)
        
        // Verify Vertex is now Meters
        assertEquals(1.0f, scaledVertex.y, 0.001f, "Vertex should be scaled to 1.0m")

        // 3. Load Process (Inverse Bind Matrix)
        // Offset Matrix (Mesh -> Bone). Bone is at 100. So Offset is Translate(-100).
        val originalOffsetMatrix = Matrix4f().translate(0f, -100f, 0f)
        
        // Apply Fix: Scale Input (M -> CM)
        val ibm = Matrix4f(originalOffsetMatrix)
        ibm.scale(1.0f / unitScale) 
        
        // 4. Animation / Skeleton Update
        // Animation moves bone to 120cm (Jump)
        val animTranslation = Vector3f(0f, 120f, 0f)
        val jointLocal = Matrix4f().translate(animTranslation)
        
        // Apply Fix: Skeleton Root Transform
        val skeletonRoot = Matrix4f().scale(unitScale)
        
        // Calculate World Transform
        val jointWorld = Matrix4f(skeletonRoot).mul(jointLocal)
        
        // 5. Skinning Calculation in Shader
        // JointMatrix = JointWorld * IBM
        val jointMatrix = Matrix4f(jointWorld).mul(ibm)
        
        // Transform Vertex
        val finalPos = Vector4f(scaledVertex.x, scaledVertex.y, scaledVertex.z, 1.0f)
        jointMatrix.transform(finalPos)
        
        println("Original Vertex (CM): $originalVertex")
        println("Scaled Vertex (M): $scaledVertex")
        println("Anim Translation (CM): $animTranslation")
        println("Final Position (M): $finalPos")

        // Expected: 
        // Vertex (1.0m).
        // IBM converts 1.0m -> 100cm. Then subtracts 100cm (Bone Rest Pos). Result: (0,0,0) in Bone Space.
        // Animation puts Bone at 120cm.
        // Bone Space (0,0,0) -> World 120cm.
        // Skeleton Root converts 120cm -> 1.2m.
        
        assertEquals(1.2f, finalPos.y, 0.001f, "Final position should be 1.2m (Animation height)")
    }
}
