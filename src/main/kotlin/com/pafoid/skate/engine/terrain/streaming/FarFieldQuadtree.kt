package com.pafoid.skate.engine.terrain.streaming

import com.pafoid.skate.engine.terrain.TerrainLayout
import kotlin.math.sqrt

/** A far-field tile: [level] 0 is one chunk; each level doubles the side. [tx], [tz] in tiles of that level. */
data class FarTile(val level: Int, val tx: Int, val tz: Int)

/**
 * Far-field tile selection (REN-28 §15.4), pure: a quadtree over the terrain whose leaves are chunks. A tile splits
 * while the camera is within [SPLIT] × its side, or while it holds a chunk that is drawn in detail; leaves of
 * detailed chunks are skipped, so the far field fills exactly the ground the detail chunks don't cover (no holes, no
 * double draw). Tiles beyond [farDistance] are dropped.
 */
object FarFieldQuadtree {
    const val SPLIT = 2.5f

    fun select(
        layout: TerrainLayout, camX: Float, camZ: Float, farDistance: Float,
        detailed: (cx: Int, cz: Int) -> Boolean, out: MutableList<FarTile>,
    ) {
        out.clear()
        var levels = 0
        while ((1 shl levels) < layout.chunksPerSide) levels++
        visit(layout, levels, 0, 0, camX, camZ, farDistance, detailed, out)
    }

    /** True when any chunk under tile ([level], [tx], [tz]) is detailed. */
    private fun anyDetailed(layout: TerrainLayout, level: Int, tx: Int, tz: Int, detailed: (Int, Int) -> Boolean): Boolean {
        val span = 1 shl level
        for (cz in tz * span until minOf((tz + 1) * span, layout.chunksPerSide))
            for (cx in tx * span until minOf((tx + 1) * span, layout.chunksPerSide)) if (detailed(cx, cz)) return true
        return false
    }

    private fun visit(
        layout: TerrainLayout, level: Int, tx: Int, tz: Int, camX: Float, camZ: Float, far: Float,
        detailed: (Int, Int) -> Boolean, out: MutableList<FarTile>,
    ) {
        val span = 1 shl level
        if (tx * span >= layout.chunksPerSide || tz * span >= layout.chunksPerSide) return // past the terrain
        val side = span * layout.chunkSize
        val minX = layout.chunkCornerX(tx * span); val minZ = layout.chunkCornerZ(tz * span)
        val dx = maxOf(minX - camX, 0f, camX - (minX + side)); val dz = maxOf(minZ - camZ, 0f, camZ - (minZ + side))
        val d = sqrt(dx * dx + dz * dz)
        if (d > far) return
        if (level == 0) {
            if (!detailed(tx, tz)) out += FarTile(0, tx, tz)
            return
        }
        if (d < SPLIT * side || anyDetailed(layout, level, tx, tz, detailed)) {
            for (cz in 0..1) for (cx in 0..1) visit(layout, level - 1, tx * 2 + cx, tz * 2 + cz, camX, camZ, far, detailed, out)
        } else {
            out += FarTile(level, tx, tz)
        }
    }
}
