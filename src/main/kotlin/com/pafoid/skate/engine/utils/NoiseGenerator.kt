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
        
        // Worley Points for different scales
        val points1 = Array(12) { Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat()) }
        val points2 = Array(24) { Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat()) }
        val points3 = Array(48) { Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat()) }

        for (z in 0 until size) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val p = Vector3f(x.toFloat() / size, y.toFloat() / size, z.toFloat() / size)

                    val w1 = 1.0f - worley(p, points1)
                    val w2 = 1.0f - worley(p, points2)
                    val w3 = 1.0f - worley(p, points3)
                    
                    // Combine into a multi-scale Perlin-Worley like texture
                    val r = (w1 * 0.6f + w2 * 0.3f + w3 * 0.1f).coerceIn(0f, 1f)
                    val g = w1
                    val b = w2
                    val a = w3

                    buffer.put((r * 255).toInt().toByte())
                    buffer.put((g * 255).toInt().toByte())
                    buffer.put((b * 255).toInt().toByte())
                    buffer.put((a * 255).toInt().toByte())
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
