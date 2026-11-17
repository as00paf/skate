package com.pafoid.skate.engine.terrain

import com.pafoid.skate.engine.procedural.ProceduralSeeds
import org.joml.Vector3f
import java.util.SplittableRandom
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.min

/**
 * Per-chunk scatter placement (REN-28 §7), pure and deterministic: a jittered grid at the rule's `minSpacing`, every
 * cell drawing the same random numbers in the same order whatever is rejected, seeded per rule and chunk. So a chunk
 * places the same items alone, in any order, on any thread, and other rules or chunks never shift it.
 */
object ScatterPlacer {

    /** One rule with the biome it belongs to. */
    class Entry(val rule: ScatterRule, val biome: Int)

    fun entries(biomes: List<Biome>): List<Entry> = biomes.flatMapIndexed { b, biome -> biome.scatter.map { Entry(it, b) } }

    /**
     * Places every rule's items on [chunk] (heights already final). [biomeWeight] gives a biome's weight at a local
     * position (dithers items at borders); [stamps] are the stamps reaching the chunk.
     */
    fun place(
        chunk: TerrainChunkData,
        entries: List<Entry>,
        projectSeed: Long,
        seedName: String,
        seedOffset: Int,
        stamps: List<TerrainStamp>,
        biomeWeight: (biome: Int, x: Float, z: Float, height: Float) -> Float,
    ): List<ScatterInstance> {
        if (entries.isEmpty()) return emptyList()
        val out = ArrayList<ScatterInstance>()
        val size = chunk.chunkQuads * chunk.spacing
        val normal = Vector3f()
        for ((index, entry) in entries.withIndex()) {
            val rule = entry.rule
            if (rule.densityPer100m2 <= 0f || rule.minSpacing <= 0f) continue
            val cell = rule.minSpacing
            val cells = ceil(size / cell).toInt()
            val chance = min(1f, rule.densityPer100m2 * cell * cell / 100f)
            val random = SplittableRandom(ProceduralSeeds.chunkSeed(projectSeed, "$seedName/scatter/${rule.id}", chunk.cx, chunk.cz, seedOffset))
            for (gz in 0 until cells) for (gx in 0 until cells) {
                // Always the same 6 draws per cell, in order
                val jx = random.nextDouble().toFloat(); val jz = random.nextDouble().toFloat()
                val keep = random.nextDouble().toFloat(); val dither = random.nextDouble().toFloat()
                val yawR = random.nextDouble().toFloat(); val scaleR = random.nextDouble().toFloat()
                if (keep >= chance) continue
                val lx = (gx + jx) * cell; val lz = (gz + jz) * cell
                if (lx >= size || lz >= size) continue
                val x = chunk.cornerX + lx; val z = chunk.cornerZ + lz
                val y = heightOn(chunk, lx / chunk.spacing, lz / chunk.spacing)
                if (y < rule.minHeight || y > rule.maxHeight) continue
                normalOn(chunk, lx / chunk.spacing, lz / chunk.spacing, normal)
                val slope = (acos(normal.y.coerceIn(-1f, 1f).toDouble()) * 180.0 / PI).toFloat()
                if (slope < rule.minSlope || slope > rule.maxSlope) continue
                if (rule.avoidStamps && stamps.any { it.distanceOutside(x, z) < it.falloff }) continue
                if (dither >= biomeWeight(entry.biome, x, z, y)) continue
                val yaw = ((yawR - 0.5f) * rule.yawJitter * PI / 180.0).toFloat()
                val scale = rule.scaleMin + (rule.scaleMax - rule.scaleMin) * scaleR
                out += ScatterInstance(index, gz * cells + gx, x, y, z, yaw, scale, normal.x, normal.y, normal.z, dither)
            }
        }
        return out
    }

    /** Height at fractional sample ([fi], [fj]) of [chunk], on the collider's triangle split. */
    fun heightOn(chunk: TerrainChunkData, fi: Float, fj: Float): Float {
        val q = chunk.chunkQuads
        val i = fi.toInt().coerceIn(0, q - 1); val j = fj.toInt().coerceIn(0, q - 1)
        val u = (fi - i).coerceIn(0f, 1f); val v = (fj - j).coerceIn(0f, 1f)
        val h00 = chunk.height(i, j); val h11 = chunk.height(i + 1, j + 1)
        return if (u >= v) { val h10 = chunk.height(i + 1, j); h00 + (h10 - h00) * u + (h11 - h10) * v }
        else { val h01 = chunk.height(i, j + 1); h00 + (h01 - h00) * v + (h11 - h01) * u }
    }

    fun normalOn(chunk: TerrainChunkData, fi: Float, fj: Float, out: Vector3f): Vector3f {
        val i = (fi + 0.5f).toInt().coerceIn(0, chunk.chunkQuads); val j = (fj + 0.5f).toInt().coerceIn(0, chunk.chunkQuads)
        return chunk.normal(i, j, out)
    }
}
