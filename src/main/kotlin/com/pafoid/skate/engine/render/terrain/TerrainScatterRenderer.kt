package com.pafoid.skate.engine.render.terrain

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.render.instancing.InstanceBatch
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.terrain.ScatterInstance
import com.pafoid.skate.engine.terrain.ScatterPlacer
import com.pafoid.skate.engine.terrain.ScatterRule
import com.pafoid.skate.engine.terrain.TerrainChunkData
import org.joml.FrustumIntersection
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.IdentityHashMap

/**
 * Draws terrain scatter items without behaviour as instance batches (REN-28 §7): one [InstanceBatch] per chunk × rule
 * × mesh part, rebuilt when the chunk's scatter changes. Instances are sorted by their seeded random, so thinning by
 * distance (beyond the rule's `fadeStart`, none past `streamRadius`) is a prefix of the batch: no re-upload while the
 * camera moves.
 */
class TerrainScatterRenderer(
    private val modelRenderer: ModelRenderer,
    /** Loads a rule's model (null: missing, the rule draws nothing). */
    private val loadModel: (String) -> `3dModel`? = { null },
    /** Loads a rule's `.skmat` override (null: the model's materials). */
    private val loadMaterial: (String) -> Material? = { null },
    private val device: () -> RhiDevice = RhiContext::require,
) {
    class RuleBatches(val rule: ScatterRule, val batches: List<InstanceBatch>, val count: Int, val radius: Float)

    private class ChunkScatter(val source: List<ScatterInstance>, val rules: List<RuleBatches>)

    private val chunks = IdentityHashMap<TerrainChunkData, ChunkScatter>()
    private val models = HashMap<String, `3dModel`?>()
    private val materials = HashMap<String, Material?>()
    private val world = Matrix4f()
    private val align = Quaternionf()
    private val up = Vector3f(0f, 1f, 0f)
    private val normal = Vector3f()
    private val centre = Vector3f()
    private val frustum = FrustumIntersection()
    private val vp = Matrix4f()

    /** Instances drawn by the last [draw] calls since [resetCounters] (stats, tests). */
    var instancesDrawn = 0
        private set

    fun resetCounters() { instancesDrawn = 0 }

    fun setFrustum(viewProjection: Matrix4f) { frustum.set(viewProjection) }
    fun setFrustum(projection: Matrix4f, view: Matrix4f) { frustum.set(vp.set(projection).mul(view)) }

    /** The batches of [chunk] (terrain at [terrainPosition]), built or rebuilt when its scatter list changed. */
    fun batches(chunk: TerrainChunkData, rules: List<ScatterPlacer.Entry>, terrainPosition: Vector3f): List<RuleBatches> {
        val existing = chunks[chunk]
        if (existing != null && existing.source === chunk.scatter) return existing.rules
        existing?.rules?.forEach { r -> r.batches.forEach { it.close() } }
        val byRule = chunk.scatter.groupBy { it.rule }
        val built = byRule.mapNotNull { (ruleIndex, items) ->
            val rule = rules.getOrNull(ruleIndex)?.rule ?: return@mapNotNull null
            if (rule.model.isBlank()) return@mapNotNull null
            val model = models.getOrPut(rule.model) { runCatching { loadModel(rule.model) }.getOrNull() } ?: return@mapNotNull null
            val override = rule.material.takeIf { it.isNotBlank() }?.let { m -> materials.getOrPut(m) { runCatching { loadMaterial(m) }.getOrNull() } }
            val sorted = items.sortedBy { it.random }
            val batches = model.mesh.map { part ->
                InstanceBatch(part, override ?: part.material, device).also { batch ->
                    for (item in sorted) batch.add(matrixOf(item, rule, terrainPosition), random = item.random)
                }
            }
            RuleBatches(rule, batches, sorted.size, sorted.maxOfOrNull { it.scale } ?: 1f)
        }
        chunks[chunk] = ChunkScatter(chunk.scatter, built)
        return built
    }

    /** World matrix of [item]: on the ground, tilted towards the normal by `alignToNormal`, turned and scaled. */
    fun matrixOf(item: ScatterInstance, rule: ScatterRule, terrainPosition: Vector3f): Matrix4f {
        normal.set(item.nx, item.ny, item.nz)
        align.identity().rotationTo(up, normal).nlerp(Quaternionf(), 1f - rule.alignToNormal.coerceIn(0f, 1f))
        return world.translation(terrainPosition.x + item.x, terrainPosition.y + item.y, terrainPosition.z + item.z)
            .rotate(align).rotateY(item.yaw).scale(item.scale)
    }

    /**
     * Draws [chunk]'s items with [shader] (an INSTANCED variant, bound, frame uniforms uploaded): chunks outside the
     * frustum or beyond every rule's stream radius are skipped, and each rule thins out between `fadeStart` and
     * `streamRadius` from [camera]. [simple] is the picking path; [shadow] draws everything in range (no thinning).
     */
    fun draw(
        shader: Shader, chunk: TerrainChunkData, rules: List<ScatterPlacer.Entry>, terrainPosition: Vector3f,
        chunkMin: Vector3f, chunkMax: Vector3f, camera: Vector3f, simple: Boolean = false,
    ) {
        if (chunk.scatter.isEmpty()) return
        val margin = 4f
        if (!frustum.testAab(chunkMin.x - margin, chunkMin.y - margin, chunkMin.z - margin, chunkMax.x + margin, chunkMax.y + margin * 2, chunkMax.z + margin)) return
        centre.set((chunkMin.x + chunkMax.x) * 0.5f, (chunkMin.y + chunkMax.y) * 0.5f, (chunkMin.z + chunkMax.z) * 0.5f)
        val half = (chunkMax.x - chunkMin.x) * 0.5f
        val distance = (centre.distance(camera) - half * 1.42f).coerceAtLeast(0f)
        for (r in batches(chunk, rules, terrainPosition)) {
            val count = visibleCount(r.count, distance, r.rule.fadeStart, r.rule.streamRadius)
            if (count == 0) continue
            for (batch in r.batches) if (modelRenderer.drawInstanced(batch, shader, simple, count)) instancesDrawn += count
        }
    }

    /** Shadow casters: [draw] with the shadow pipeline, items whose rule has colliders or any size, no material. */
    fun drawShadow(
        shader: Shader, chunk: TerrainChunkData, rules: List<ScatterPlacer.Entry>, terrainPosition: Vector3f,
        chunkMin: Vector3f, chunkMax: Vector3f, camera: Vector3f,
    ) {
        if (chunk.scatter.isEmpty()) return
        val margin = 4f
        if (!frustum.testAab(chunkMin.x - margin, chunkMin.y - margin, chunkMin.z - margin, chunkMax.x + margin, chunkMax.y + margin * 2, chunkMax.z + margin)) return
        centre.set((chunkMin.x + chunkMax.x) * 0.5f, (chunkMin.y + chunkMax.y) * 0.5f, (chunkMin.z + chunkMax.z) * 0.5f)
        val distance = (centre.distance(camera) - (chunkMax.x - chunkMin.x) * 0.71f).coerceAtLeast(0f)
        for (r in batches(chunk, rules, terrainPosition)) {
            val count = visibleCount(r.count, distance, r.rule.fadeStart, r.rule.streamRadius)
            for (batch in r.batches) modelRenderer.drawInstanced(batch, shader, simple = true, count = count, shadow = true)
        }
    }

    /** Drops batches of chunks no longer resident. */
    fun retain(resident: Collection<TerrainChunkData>) {
        val keep = java.util.Collections.newSetFromMap(IdentityHashMap<TerrainChunkData, Boolean>()).apply { addAll(resident) }
        val it = chunks.entries.iterator()
        while (it.hasNext()) { val e = it.next(); if (e.key !in keep) { e.value.rules.forEach { r -> r.batches.forEach { b -> b.close() } }; it.remove() } }
    }

    fun destroy() {
        chunks.values.forEach { c -> c.rules.forEach { r -> r.batches.forEach { it.close() } } }
        chunks.clear(); models.clear(); materials.clear()
    }

    companion object {
        /** How many of [count] items (sorted by random) draw at [distance]: all before [fadeStart], none past [radius]. */
        fun visibleCount(count: Int, distance: Float, fadeStart: Float, radius: Float): Int = when {
            distance >= radius -> 0
            distance <= fadeStart || radius <= fadeStart -> count
            else -> (count * (1f - (distance - fadeStart) / (radius - fadeStart)) + 0.5f).toInt().coerceIn(0, count)
        }
    }
}
