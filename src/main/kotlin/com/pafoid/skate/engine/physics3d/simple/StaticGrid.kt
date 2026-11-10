package com.pafoid.skate.engine.physics3d.simple

import org.joml.Vector3f
import kotlin.math.floor

/**
 * Broad phase for SimplePhysics3D's static bodies (PHY-25): a uniform grid hash of [cellSize] metres. Each static body
 * is listed in every cell its bounds overlap; one covering more than [maxCellsPerBody] cells (a huge ground box) goes
 * to an "oversize" list that every query visits. Rebuilt only when the static set changes.
 */
internal class StaticGrid(private val cellSize: Float = 16f, private val maxCellsPerBody: Int = 1024) {
    private val cells = HashMap<Long, MutableList<SimpleBody>>()
    private val oversize = ArrayList<SimpleBody>()
    private val all = ArrayList<SimpleBody>()
    private var stamp = 0
    private val min = Vector3f()
    private val max = Vector3f()

    val size: Int get() = all.size

    fun rebuild(statics: List<SimpleBody>) {
        cells.clear()
        oversize.clear()
        all.clear()
        all.addAll(statics)
        for (body in statics) {
            body.bounds(min, max)
            if (cellCount(min, max) > maxCellsPerBody) {
                oversize += body
                continue
            }
            forEachCell(min, max) { key -> cells.getOrPut(key) { ArrayList(4) } += body }
        }
    }

    /**
     * Visits every static body whose cells overlap the box [lo]–[hi], once each. A query covering more than
     * [maxCellsPerBody] cells (a very long ray) visits every static body instead.
     */
    fun query(lo: Vector3f, hi: Vector3f, visit: (SimpleBody) -> Unit) {
        if (all.isEmpty()) return
        val current = ++stamp
        fun once(body: SimpleBody) {
            if (body.queryStamp == current) return
            body.queryStamp = current
            visit(body)
        }
        if (cellCount(lo, hi) > maxCellsPerBody) {
            all.forEach(::once)
            return
        }
        oversize.forEach(::once)
        forEachCell(lo, hi) { key -> cells[key]?.forEach(::once) }
    }

    private fun index(v: Float): Int = floor(v / cellSize).toInt()

    private fun cellCount(lo: Vector3f, hi: Vector3f): Long =
        (index(hi.x) - index(lo.x) + 1L) * (index(hi.y) - index(lo.y) + 1L) * (index(hi.z) - index(lo.z) + 1L)

    private inline fun forEachCell(lo: Vector3f, hi: Vector3f, action: (Long) -> Unit) {
        for (x in index(lo.x)..index(hi.x)) for (y in index(lo.y)..index(hi.y)) for (z in index(lo.z)..index(hi.z)) {
            action(key(x, y, z))
        }
    }

    private fun key(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and MASK) shl 42) or ((y.toLong() and MASK) shl 21) or (z.toLong() and MASK)

    private companion object {
        const val MASK = (1L shl 21) - 1
    }
}
