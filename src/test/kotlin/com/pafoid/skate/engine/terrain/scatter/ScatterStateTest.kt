package com.pafoid.skate.engine.terrain.scatter

import com.pafoid.skate.engine.terrain.Biome
import com.pafoid.skate.engine.terrain.BiomePresets
import com.pafoid.skate.engine.terrain.LayerRule
import com.pafoid.skate.engine.terrain.ScatterRule
import com.pafoid.skate.engine.terrain.TerrainKey
import com.pafoid.skate.engine.terrain.TerrainPreset
import com.pafoid.skate.engine.terrain.TerrainRuntime
import com.pafoid.skate.engine.terrain.streaming.Focus
import com.pafoid.skate.engine.terrain.streaming.StreamingRings
import com.pafoid.skate.engine.terrain.streaming.TerrainStreamer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** REN-28 tasks 23, 25 and 27: remembered scatter state, its save section, and the stateful props loop (no GL). */
class ScatterStateTest {
    private val k = ScatterItemKey("crates", 2, 3, 17)
    private val t = floatArrayOf(1f, 2f, 3f, 0f, 0f, 0f, 1f, 1f, 1f, 1f)

    @Test
    fun Records_EachKindHidesThePlacedItem_AndUntouchedCapturesClear() {
        val s = ScatterStateStore()
        assertFalse(s.hidesPlaced(k))
        s.destroy(k, 5f, 6f)
        assertEquals(ScatterRecordKind.DESTROYED, s.record(k)!!.kind)
        assertTrue(s.hidesPlaced(k))
        s.take(k, 5f, 6f); assertEquals(ScatterRecordKind.TAKEN, s.record(k)!!.kind)
        s.change(k, 5f, 6f, t, mapOf("|Crate#0|health" to "3")); assertEquals(ScatterRecordKind.CHANGED, s.record(k)!!.kind)
        val v = s.chunkVersion(2, 3)
        s.change(k, 5f, 6f, null, emptyMap())
        assertNull(s.record(k), "an untouched capture leaves no record")
        assertTrue(s.chunkVersion(2, 3) > v)
        assertEquals(0, s.chunkCount, "empty chunks are dropped")
    }

    @Test
    fun Move_AcrossChunks_IsMovedOutPlusForeign_AndBackHomeIsChanged() {
        val s = ScatterStateStore()
        s.move(k, "crate.prefab", 5f, 6f, toCx = 4, toCz = 3, transform = t, overrides = emptyMap())
        assertEquals(ScatterRecordKind.MOVED_OUT, s.record(k)!!.kind)
        assertEquals(k, s.delta(4, 3)!!.foreign.single().key)
        s.move(k, "crate.prefab", 5f, 6f, toCx = 5, toCz = 3, transform = t, overrides = emptyMap())
        assertNull(s.delta(4, 3), "it left the chunk it rested in")
        assertEquals(1, s.delta(5, 3)!!.foreign.size)
        s.move(k, "crate.prefab", 5f, 6f, toCx = 2, toCz = 3, transform = t, overrides = emptyMap())
        assertEquals(ScatterRecordKind.CHANGED, s.record(k)!!.kind, "back home: normalised to CHANGED")
        assertNull(s.delta(5, 3))
        s.move(k, "crate.prefab", 5f, 6f, 9, 9, t, emptyMap())
        s.removeForeign(9, 9, k)
        assertNull(s.delta(9, 9), "a foreign item destroyed where it rested")
    }

    @Test
    fun Validation_DropsRecordsThatNoLongerMatch_KeepsForeign() {
        val s = ScatterStateStore()
        s.destroy(k, 5f, 6f)
        s.destroy(k.copy(ordinal = 18), 9f, 9f)
        s.move(k.copy(ordinal = 30, cx = 7), "c", 1f, 1f, 2, 3, t, emptyMap())
        s.setFingerprint(2, 3, "crates", 111L)
        assertEquals(0, s.validate(2, 3, mapOf("crates" to 111L)) { _, _ -> null }, "same fingerprint: nothing checked")
        // A patch: item 17 still sits at its origin, item 18 moved 1 m away
        val dropped = s.validate(2, 3, mapOf("crates" to 222L)) { _, ordinal -> if (ordinal == 17) floatArrayOf(5.1f, 6f) else floatArrayOf(10f, 9f) }
        assertEquals(1, dropped)
        assertNotNull(s.record(k)); assertNull(s.record(k.copy(ordinal = 18)))
        assertEquals(1, s.delta(2, 3)!!.foreign.size, "foreign items always stay")
    }

    @Test
    fun Respawn_OnlyAfterTheRulesTime_AndNeverForChanges() {
        val s = ScatterStateStore()
        s.destroy(k, 5f, 6f, time = 10f)
        s.change(k.copy(ordinal = 2), 0f, 0f, t, emptyMap(), time = 10f)
        assertEquals(0, s.expire(2, 3, now = 50f) { 60f })
        assertEquals(0, s.expire(2, 3, now = 500f) { 0f }, "0 = never")
        assertEquals(1, s.expire(2, 3, now = 71f) { 60f })
        assertNull(s.record(k)); assertNotNull(s.record(k.copy(ordinal = 2)))
    }

    @Test
    fun Budget_WarnsOnce_DropsNothing() {
        val warnings = ArrayList<String>()
        val s = ScatterStateStore(budgetBytes = 100, warn = { warnings += it })
        repeat(200) { s.destroy(k.copy(ordinal = it), 0f, 0f) }
        assertEquals(1, warnings.size)
        assertEquals(200, s.delta(2, 3)!!.records.getValue("crates").size)
    }

    @Test
    fun Snapshot_RoundTripsByteForByte_AndRestores() {
        val s = ScatterStateStore()
        s.destroy(k, 5f, 6f, 1f)
        s.change(k.copy(ordinal = 3), 1f, 2f, t, mapOf("|Crate#0|health" to "1", "|Light#0|on" to "false"), 2f)
        s.move(k.copy(ordinal = 4), "engine:Prefabs/crate.prefab", 3f, 4f, 8, 8, t, mapOf("x" to "\"y\""), 3f)
        s.setFingerprint(2, 3, "crates", 42L)
        val snap = TerrainStateSnapshot("terrain/a", 99L, 7L, s.snapshot())
        val bytes = snap.encode()
        val back = TerrainStateSnapshot.decode(bytes)
        assertEquals("terrain/a", back.terrainId); assertEquals(99L, back.worldSeed); assertEquals(7L, back.recipeFingerprint)
        assertArrayEquals(bytes, back.encode())
        assertEquals(s.snapshot(), back.deltas)
        val other = ScatterStateStore()
        other.restore(back.deltas)
        assertEquals(s.snapshot(), other.snapshot())
        assertThrows<IllegalArgumentException> { TerrainStateSnapshot.decode(java.io.ByteArrayOutputStream().also { java.util.zip.DeflaterOutputStream(it).use { d -> d.write(ByteArray(16)) } }.toByteArray()) }
    }

    @Test
    fun Service_SavesEveryTerrain_AndRestoresBySectionId() {
        val a = ScatterStateStore().apply { destroy(k, 0f, 0f) }
        val b = ScatterStateStore()
        val sections = TerrainStateService.snapshot(listOf(TerrainStateService.Terrain("A", 1L, 2L, a)))
        val restoredA = ScatterStateStore(); val restoredB = ScatterStateStore().apply { destroy(k, 0f, 0f) }
        assertEquals(1, TerrainStateService.restore(sections, listOf(
            TerrainStateService.Terrain("A", 1L, 2L, restoredA), TerrainStateService.Terrain("B", 1L, 2L, restoredB))))
        assertTrue(restoredA.hidesPlaced(k))
        assertEquals(0, restoredB.chunkCount, "no section: a fresh start")
        assertEquals(0, b.chunkCount)
    }

    // --- runtime integration ---

    private val crates = Biome(layerRules = listOf(LayerRule(0)), scatter = listOf(
        ScatterRule(id = "crates", model = "crate.obj", densityPer100m2 = 3f, minSpacing = 4f, maxSlope = 90f),
    ))

    private fun key(seedOffset: Int = 0, streamed: Boolean = false) = TerrainKey(256f, 1f, 32, 20f, TerrainPreset.ROLLING_HILLS.recipe(),
        emptyList(), 5L, "terrain/C", seedOffset, listOf(crates), streamed = streamed)

    @Test
    fun Remove_HidesAnInstancedItem_AndANewWorldClearsTheState() {
        val rt = TerrainRuntime().apply { ensureBuilt(key(), parallel = false) }
        val chunk = rt.chunks.first { it.scatter.isNotEmpty() }
        val item = chunk.scatter.first()
        val itemKey = TerrainScatter.keyOf(rt, chunk, item)!!
        assertTrue(TerrainScatter.remove(rt, itemKey))
        assertTrue(TerrainScatter.hidden(rt, chunk, item))
        assertFalse(TerrainScatter.hidden(rt, chunk, chunk.scatter.last()) && chunk.scatter.size > 1 && chunk.scatter.last() !== item)
        // Same world, only streamed: kept
        rt.ensureBuilt(key(streamed = true), parallel = false)
        assertTrue(rt.scatterState.hidesPlaced(itemKey))
        // A reroll: a different world
        rt.ensureBuilt(key(seedOffset = 1, streamed = true), parallel = false)
        assertEquals(0, rt.scatterState.chunkCount)
    }

    /** Task 27 (REN part): break a crate, fly away until its chunk unloads, come back, save, load: still gone. */
    @Test
    fun StatefulProps_DestroyedStaysDestroyed_AcrossUnloadSaveAndLoad() {
        val exec = java.util.concurrent.Executor { it.run() }
        val rings = StreamingRings(physics = 32f, detail = 64f)
        val rt = TerrainRuntime().apply { ensureBuilt(key(streamed = true), parallel = false) }
        val streamer = TerrainStreamer(rt, exec, cacheBytes = 0, maxInFlight = 1000, maxEvictionsPerUpdate = 1000)
        val home = listOf(Focus().set(0f, 0f))
        repeat(3) { streamer.update(home, 1, rings) }
        val chunk = rt.chunks.first { it.scatter.isNotEmpty() && kotlin.math.abs(it.cornerX) < 64f && kotlin.math.abs(it.cornerZ) < 64f }
        val item = chunk.scatter.first()
        val itemKey = TerrainScatter.keyOf(rt, chunk, item)!!
        TerrainScatter.remove(rt, itemKey) // the crate breaks
        // Fly away: the chunk unloads (no cache, so it regenerates)
        repeat(3) { streamer.update(listOf(Focus().set(120f, 120f)), 1, rings) }
        assertNull(rt.chunk(chunk.cx, chunk.cz))
        repeat(3) { streamer.update(home, 1, rings) }
        val reloaded = rt.chunk(chunk.cx, chunk.cz)!!
        assertTrue(reloaded !== chunk, "regenerated, not cached")
        assertTrue(TerrainScatter.hidden(rt, reloaded, reloaded.scatter.first { it.key == item.key && it.rule == item.rule }))
        // Save, quit, load into a fresh runtime
        val sections = TerrainStateService.snapshot(listOf(TerrainStateService.Terrain("terrain/C", 5L, ScatterFingerprint.ofWorld(rt.key!!), rt.scatterState)))
        val loaded = TerrainRuntime().apply { ensureBuilt(key(streamed = true), parallel = false) }
        TerrainStateService.restore(sections, listOf(TerrainStateService.Terrain("terrain/C", 5L, 0L, loaded.scatterState)))
        val again = TerrainStreamer(loaded, exec, maxInFlight = 1000)
        repeat(3) { again.update(home, 1, rings) }
        val c2 = loaded.chunk(chunk.cx, chunk.cz)!!
        assertTrue(TerrainScatter.hidden(loaded, c2, c2.scatter.first { it.key == item.key && it.rule == item.rule }))
        assertEquals(1, c2.scatter.count { TerrainScatter.hidden(loaded, c2, it) }, "only that crate")
    }

    @Test
    fun BreakableCrates_IsAPrefabRuleWithColliders() {
        val rule = BiomePresets.breakableCrates("Prefabs/crate.prefab", respawnAfterSeconds = 120f)
        assertEquals("Prefabs/crate.prefab", rule.prefab)
        assertTrue(rule.collider)
        assertEquals(120f, rule.respawnAfterSeconds)
    }
}
