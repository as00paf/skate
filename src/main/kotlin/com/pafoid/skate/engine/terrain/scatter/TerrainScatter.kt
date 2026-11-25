package com.pafoid.skate.engine.terrain.scatter

import com.pafoid.skate.engine.terrain.ScatterInstance
import com.pafoid.skate.engine.terrain.TerrainChunkData
import com.pafoid.skate.engine.terrain.TerrainRuntime

/**
 * Gameplay access to scatter items (REN-28 §15.10.2): keys of placed items and removal of instanced-only ones (a bush
 * cut down). Removal writes a `DESTROYED` record, so the item stays gone across unloads and saves; the instance batch
 * of its chunk is rebuilt without it.
 */
object TerrainScatter {
    /** The key of [item] placed on [chunk] of [runtime]'s terrain; null when its rule is unknown. */
    fun keyOf(runtime: TerrainRuntime, chunk: TerrainChunkData, item: ScatterInstance): ScatterItemKey? {
        val rule = runtime.generator?.scatterEntries?.getOrNull(item.rule)?.rule ?: return null
        return ScatterItemKey(rule.id, chunk.cx, chunk.cz, item.key)
    }

    /** The placed item with [key], if its chunk is resident. */
    fun find(runtime: TerrainRuntime, key: ScatterItemKey): ScatterInstance? {
        val chunk = runtime.chunk(key.cx, key.cz) ?: return null
        val entries = runtime.generator?.scatterEntries ?: return null
        return chunk.scatter.firstOrNull { it.key == key.ordinal && entries.getOrNull(it.rule)?.rule?.id == key.ruleId }
    }

    /** Removes the item [key] for good (until its rule's respawn time); returns false when it isn't placed. */
    fun remove(runtime: TerrainRuntime, key: ScatterItemKey): Boolean {
        val item = find(runtime, key) ?: return false
        val chunk = runtime.chunk(key.cx, key.cz) ?: return false
        runtime.scatterState.destroy(key, item.x, item.z, runtime.stateTime)
        recordFingerprints(runtime, chunk)
        return true
    }

    /** Stores the placement fingerprints of [chunk] next to its records, for validation after a patch (§15.10.4). */
    fun recordFingerprints(runtime: TerrainRuntime, chunk: TerrainChunkData) {
        val g = runtime.generator ?: return
        for (e in g.scatterEntries) runtime.scatterState.setFingerprint(chunk.cx, chunk.cz, e.rule.id, ScatterFingerprint.of(g.projectSeed, g.seedName, g.seedOffset, e.rule, chunk))
    }

    /** True when [item] on [chunk] must not be drawn or spawned from placement (it has a record). */
    fun hidden(runtime: TerrainRuntime, chunk: TerrainChunkData, item: ScatterInstance): Boolean =
        runtime.scatterState.chunkCount > 0 && keyOf(runtime, chunk, item)?.let(runtime.scatterState::hidesPlaced) == true
}
