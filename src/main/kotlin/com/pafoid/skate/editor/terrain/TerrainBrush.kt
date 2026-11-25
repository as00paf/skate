package com.pafoid.skate.editor.terrain

import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.terrain.HeightField
import com.pafoid.skate.engine.terrain.TerrainChunkData
import com.pafoid.skate.engine.terrain.TerrainLayout
import com.pafoid.skate.engine.terrain.TerrainRuntime
import org.joml.Vector3f
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** The terrain tool chosen in the terrain inspector (EDT-92); NONE leaves the viewport to the usual gizmos. */
enum class TerrainTool(val sculpt: Boolean) {
    NONE(false), RAISE(true), LOWER(true), SMOOTH(true), FLATTEN(true), PAINT_BIOME(false), STAMP(false);
}

/**
 * Editor-side terrain tool settings (EDT-92), shared by the inspector (which sets them) and `TerrainBrushTool` (which
 * uses them on the inspected [terrain]). View state, not project data: none of it is saved or undone.
 */
class TerrainToolState {
    var tool: TerrainTool = TerrainTool.NONE
    /** The terrain the inspector last drew; the tool acts on it while its object is the only selection. */
    var terrain: TerrainComponent? = null
    var radius: Float = 5f
        set(value) { field = value.coerceIn(MIN_RADIUS, MAX_RADIUS) }
    /** Raise/Lower: metres per second at the centre. Smooth/Flatten: blend rate per second (1 = most of the way). */
    var strength: Float = 2f
        set(value) { field = value.coerceIn(MIN_STRENGTH, MAX_STRENGTH) }
    /** Biome index painted by [TerrainTool.PAINT_BIOME]. */
    var biome: Int = 0
    /** Index in the terrain's stamp list shown with handles (-1: none). */
    var selectedStamp: Int = -1

    fun reset() {
        tool = TerrainTool.NONE
        terrain = null
        selectedStamp = -1
    }

    companion object {
        const val MIN_RADIUS = 0.5f
        const val MAX_RADIUS = 200f
        const val MIN_STRENGTH = 0.05f
        const val MAX_STRENGTH = 50f

        val shared = TerrainToolState()
    }
}

/**
 * The heights and painted biome ids of the chunks a stroke touched, captured on first touch, so the stroke can be
 * undone as one command (EDT-92 §3). Keys are chunk coordinates: chunk objects are replaced when a terrain rebuilds.
 */
class TerrainEditRecorder {
    class Snapshot(val heights: FloatArray, val painted: ByteArray?)

    val before = LinkedHashMap<Pair<Int, Int>, Snapshot>()

    fun touch(chunk: TerrainChunkData) {
        before.getOrPut(chunk.cx to chunk.cz) { Snapshot(chunk.heights.copyOf(), chunk.paintedBiomeIds?.copyOf()) }
    }

    val isEmpty: Boolean get() = before.isEmpty()

    /** The touched chunks' current state, in [before]'s order. Chunks no longer resident are left out. */
    fun after(runtime: TerrainRuntime): Map<Pair<Int, Int>, Snapshot> = before.keys.mapNotNull { key ->
        runtime.chunk(key.first, key.second)?.let { key to Snapshot(it.heights.copyOf(), it.paintedBiomeIds?.copyOf()) }
    }.toMap()
}

/**
 * Brush maths of the terrain tools (EDT-92), free of the editor and the GPU so it is unit-tested.
 *
 * Coordinates are terrain-local (relative to the terrain's Transform). A brush works on *global samples*: sample
 * (gx, gz) sits at `(-half + gx × spacing, -half + gz × spacing)`. Chunk (cx, cz) owns gx in `cx·q .. cx·q + q` and
 * keeps a one-sample apron, so a sample is stored in up to nine chunks; every write goes to all of them, which keeps
 * seams and edge normals closed.
 */
object TerrainBrush {

    /** Smooth brush weight at distance [d] from the centre: 1 at the centre, 0 at [radius] (`(1 − d²/r²)²`). */
    fun falloff(d: Float, radius: Float): Float {
        if (radius <= 0f || d >= radius) return 0f
        val t = 1f - (d * d) / (radius * radius)
        return t * t
    }

    /**
     * First point where the ray ([origin], [dir], terrain-local) meets the ground of [field] inside [layout], or null:
     * marched in steps of the sample spacing up to [maxDistance], then refined by bisection.
     */
    fun raycast(field: HeightField, layout: TerrainLayout, origin: Vector3f, dir: Vector3f, maxDistance: Float = 4000f, out: Vector3f = Vector3f()): Vector3f? {
        val len = dir.length()
        if (len < 1e-6f) return null
        val dx = dir.x / len; val dy = dir.y / len; val dz = dir.z / len
        val step = max(layout.spacing * 0.5f, 0.05f)
        fun above(t: Float): Float {
            val x = origin.x + dx * t; val z = origin.z + dz * t
            return (origin.y + dy * t) - field.heightAt(x, z)
        }
        fun inside(t: Float) = layout.contains(origin.x + dx * t, origin.z + dz * t)
        var t0 = 0f
        var h0 = above(t0)
        var t = step
        while (t <= maxDistance) {
            val h = above(t)
            if (h <= 0f && h0 > 0f && inside(t)) {
                var lo = t0; var hi = t
                repeat(24) {
                    val mid = (lo + hi) * 0.5f
                    if (above(mid) > 0f) lo = mid else hi = mid
                }
                val hit = (lo + hi) * 0.5f
                return out.set(origin.x + dx * hit, 0f, origin.z + dz * hit).also { it.y = field.heightAt(it.x, it.z) }
            }
            t0 = t; h0 = h
            t += step
        }
        return null
    }

    /** Samples per side of the whole terrain grid minus one (`chunksPerSide × q`). */
    private fun lastSample(layout: TerrainLayout) = layout.chunksPerSide * layout.chunkQuads

    /** Height of global sample ([gx], [gz]) from the chunk that owns it, or null when that chunk is not resident. */
    fun heightAt(runtime: TerrainRuntime, gx: Int, gz: Int): Float? {
        val layout = runtime.layout ?: return null
        val q = layout.chunkQuads
        val n = lastSample(layout)
        if (gx !in 0..n || gz !in 0..n) return null
        val cx = min(gx / q, layout.chunksPerSide - 1)
        val cz = min(gz / q, layout.chunksPerSide - 1)
        val chunk = runtime.chunk(cx, cz) ?: return null
        return chunk.height(gx - cx * q, gz - cz * q)
    }

    /** Calls [visit] with every resident chunk storing global sample ([gx], [gz]) and the sample's (i, j) in it. */
    inline fun forEachCopy(runtime: TerrainRuntime, gx: Int, gz: Int, ownOnly: Boolean, visit: (TerrainChunkData, Int, Int) -> Unit) {
        val layout = runtime.layout ?: return
        val q = layout.chunkQuads
        val lo = if (ownOnly) 0 else -1
        val hi = if (ownOnly) q else q + 1
        for (cz in max(0, floorDiv(gz - hi, q)).. min(layout.chunksPerSide - 1, floorDiv(gz - lo, q))) {
            val j = gz - cz * q
            if (j < lo || j > hi) continue
            for (cx in max(0, floorDiv(gx - hi, q))..min(layout.chunksPerSide - 1, floorDiv(gx - lo, q))) {
                val i = gx - cx * q
                if (i < lo || i > hi) continue
                val chunk = runtime.chunk(cx, cz) ?: continue
                visit(chunk, i, j)
            }
        }
    }

    fun floorDiv(a: Int, b: Int): Int = Math.floorDiv(a, b)

    /** Global samples within [radius] of ([x], [z]) with their weight, as (gx, gz, weight) triples. */
    fun samples(layout: TerrainLayout, x: Float, z: Float, radius: Float): List<Triple<Int, Int, Float>> {
        val s = layout.spacing
        val half = layout.half
        val n = lastSample(layout)
        val out = ArrayList<Triple<Int, Int, Float>>()
        val gx0 = max(0, floor((x - radius + half) / s).toInt()); val gx1 = min(n, ceil((x + radius + half) / s).toInt())
        val gz0 = max(0, floor((z - radius + half) / s).toInt()); val gz1 = min(n, ceil((z + radius + half) / s).toInt())
        for (gz in gz0..gz1) for (gx in gx0..gx1) {
            val px = -half + gx * s; val pz = -half + gz * s
            val d = sqrt((px - x) * (px - x) + (pz - z) * (pz - z))
            val w = falloff(d, radius)
            if (w > 0f) out += Triple(gx, gz, w)
        }
        return out
    }

    /**
     * One frame of a sculpt [tool] at ([x], [z]) over [dt] seconds: computes every new height from the heights before
     * this frame, then writes each to all chunk copies (recording first touches in [recorder]). [flattenTo] is the
     * Flatten target. Returns the number of samples changed. The caller then calls `runtime.markEdited()`.
     */
    fun sculpt(
        runtime: TerrainRuntime, tool: TerrainTool, x: Float, z: Float, radius: Float, strength: Float, dt: Float,
        flattenTo: Float, recorder: TerrainEditRecorder,
    ): Int {
        require(tool.sculpt) { "$tool is not a sculpt tool" }
        val layout = runtime.layout ?: return 0
        val writes = ArrayList<Triple<Int, Int, Float>>()
        for ((gx, gz, w) in samples(layout, x, z, radius)) {
            val h = heightAt(runtime, gx, gz) ?: continue
            val next = when (tool) {
                TerrainTool.RAISE -> h + strength * dt * w
                TerrainTool.LOWER -> h - strength * dt * w
                TerrainTool.FLATTEN -> h + (flattenTo - h) * min(1f, strength * dt * w)
                TerrainTool.SMOOTH -> {
                    var sum = 0f; var count = 0
                    for (oz in -1..1) for (ox in -1..1) heightAt(runtime, gx + ox, gz + oz)?.let { sum += it; count++ }
                    h + (sum / count - h) * min(1f, strength * dt * w)
                }
                else -> h
            }
            if (next != h) writes += Triple(gx, gz, next)
        }
        for ((gx, gz, value) in writes) {
            forEachCopy(runtime, gx, gz, ownOnly = false) { chunk, i, j ->
                recorder.touch(chunk)
                chunk.setHeight(i, j, value)
            }
        }
        return writes.size
    }

    /**
     * Paints [biome] on the own samples within [radius] of ([x], [z]) (hard edge: the splat blend softens borders).
     * Chunks without painted ids start from their current dominant biomes. Returns the number of samples changed.
     */
    fun paint(runtime: TerrainRuntime, x: Float, z: Float, radius: Float, biome: Int, recorder: TerrainEditRecorder): Int {
        val layout = runtime.layout ?: return 0
        val id = biome.coerceIn(0, 255).toByte()
        var changed = 0
        for ((gx, gz, _) in samples(layout, x, z, radius)) {
            forEachCopy(runtime, gx, gz, ownOnly = true) { chunk, i, j ->
                val n = chunk.chunkQuads + 1
                val ids = chunk.paintedBiomeIds?.takeIf { it.size == n * n }
                if (ids == null || ids[j * n + i] != id) {
                    recorder.touch(chunk)
                    val target = ids ?: seedPainted(chunk).also { chunk.paintedBiomeIds = it }
                    if (target[j * n + i] != id) { target[j * n + i] = id; changed++ }
                    chunk.dirty = true
                }
            }
        }
        return changed
    }

    /** Painted ids for [chunk] starting from its current dominant biomes (zeros when none were computed). */
    fun seedPainted(chunk: TerrainChunkData): ByteArray {
        val n = chunk.chunkQuads + 1
        return chunk.biomeIds?.takeIf { it.size == n * n }?.copyOf() ?: ByteArray(n * n)
    }

    /** Restores [state] into the resident chunks (undo/redo of a stroke); returns the chunks written. */
    fun restore(runtime: TerrainRuntime, state: Map<Pair<Int, Int>, TerrainEditRecorder.Snapshot>): Int {
        var written = 0
        for ((key, snap) in state) {
            val chunk = runtime.chunk(key.first, key.second) ?: continue
            if (chunk.heights.size != snap.heights.size) continue
            snap.heights.copyInto(chunk.heights)
            chunk.paintedBiomeIds = snap.painted?.copyOf()
            chunk.dirty = true
            written++
        }
        if (written > 0) runtime.markEdited()
        return written
    }
}
