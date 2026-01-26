package com.pafoid.skate.engine.utils

import org.joml.Vector3f
import org.lwjgl.BufferUtils
import java.nio.ByteBuffer
import kotlin.math.*
import kotlin.random.Random

object NoiseGenerator {

    /**
     * Generates a 3D Perlin-Worley noise texture.
     * R = Perlin-Worley
     * G = Worley (Fine)
     * B = Worley (Medium)
     * A = Worley (Coarse)
     */
    fun generateCloudNoise(size: Int): ByteBuffer {
        val buffer = BufferUtils.createByteBuffer(size * size * size * 4)
        val random = Random(42)
        
        // Worley Points
        val coarsePoints = Array(8) { Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat()) }
        val mediumPoints = Array(16) { Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat()) }
        val finePoints = Array(32) { Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat()) }

        for (z in 0 until size) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val u = x.toFloat() / size
                    val v = y.toFloat() / size
                    val w = z.toFloat() / size
                    val p = Vector3f(u, v, w)

                    val wCoarse = 1.0f - worley(p, coarsePoints)
                    val wMedium = 1.0f - worley(p, mediumPoints)
                    val wFine = 1.0f - worley(p, finePoints)
                    
                    // Simple Perlin-ish approximation using sine waves
                    val perlin = (sin(u * 10.0) * cos(v * 10.0) * sin(w * 10.0)).toFloat() * 0.5f + 0.5f
                    
                    val combined = (perlin * wCoarse).coerceIn(0f, 1f)

                    buffer.put((combined * 255).toInt().toByte())
                    buffer.put((wFine * 255).toInt().toByte())
                    buffer.put((wMedium * 255).toInt().toByte())
                    buffer.put((wCoarse * 255).toInt().toByte())
                }
            }
        }
        buffer.flip()
        return buffer
    }

    private fun worley(p: Vector3f, points: Array<Vector3f>): Float {
        var minDist = 100f
        for (point in points) {
            // Check current and all 26 neighboring copies for seamless tiling
            for (dx in -1..1) {
                for (dy in -1..1) {
                    for (dz in -1..1) {
                        val neighborX = point.x + dx
                        val neighborY = point.y + dy
                        val neighborZ = point.z + dz
                        
                        val diffX = p.x - neighborX
                        val diffY = p.y - neighborY
                        val diffZ = p.z - neighborZ
                        val distSq = diffX*diffX + diffY*diffY + diffZ*diffZ
                        
                        if (distSq < minDist) minDist = distSq
                    }
                }
            }
        }
        return sqrt(minDist).coerceIn(0f, 1f)
    }
}
