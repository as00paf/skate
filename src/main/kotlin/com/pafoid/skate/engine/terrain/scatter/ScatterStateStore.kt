package com.pafoid.skate.engine.terrain.scatter

/**
 * Remembered scatter state of one terrain (REN-28 §15.10.4): chunk → [ChunkStateDelta]. Untouched items cost nothing
 * (regenerated from the seed); only differences are stored. Deltas are **never evicted** with their chunk, so a broken
 * crate stays broken however far the player goes. Main thread only; [snapshot] hands workers an immutable copy.
 *
 * Each change bumps [version] and the chunk's own version ([chunkVersion]), so instance batches and spawns of that
 * chunk refresh.
 */
class ScatterStateStore(
    /** Gameplay data: never shrinks with quality (REN-28 §15.10.4, 8 MB). */
    var budgetBytes: Long = 8L * 1024 * 1024,
    private val warn: (String) -> Unit = {},
) {
    private val chunks = LinkedHashMap<Long, ChunkStateDelta>()
    private val chunkVersions = HashMap<Long, Int>()
    private var warned = false

    var version = 0L
        private set

    val chunkCount: Int get() = chunks.size

    fun bytes(): Long = chunks.values.sumOf { it.bytes() }

    fun chunkVersion(cx: Int, cz: Int): Int = chunkVersions[pack(cx, cz)] ?: 0

    /** The delta of chunk ([cx], [cz]), or null when it has no change. */
    fun delta(cx: Int, cz: Int): ChunkStateDelta? = chunks[pack(cx, cz)]

    /** Every chunk with a change, by packed (cx, cz). */
    fun deltas(): Map<Long, ChunkStateDelta> = chunks

    /** The record of an item, if any. */
    fun record(key: ScatterItemKey): ScatterRecord? = chunks[pack(key.cx, key.cz)]?.records?.get(key.ruleId)?.get(key.ordinal)

    /**
     * True when the placed item [key] must not appear from placement (destroyed, taken, moved away, or changed: a changed
     * item comes back from its record instead). Instance batches and fresh spawns skip these.
     */
    fun hidesPlaced(key: ScatterItemKey): Boolean = record(key) != null

    fun destroy(key: ScatterItemKey, originX: Float, originZ: Float, time: Float = 0f) =
        put(key, ScatterRecord(ScatterRecordKind.DESTROYED, originX, originZ, time))

    fun take(key: ScatterItemKey, originX: Float, originZ: Float, time: Float = 0f) =
        put(key, ScatterRecord(ScatterRecordKind.TAKEN, originX, originZ, time))

    /** The item is still in its chunk but moved and/or changed state; an untouched capture clears its record. */
    fun change(key: ScatterItemKey, originX: Float, originZ: Float, transform: FloatArray?, overrides: Map<String, String>, time: Float = 0f) {
        if (transform == null && overrides.isEmpty()) { clearRecord(key); return }
        put(key, ScatterRecord(ScatterRecordKind.CHANGED, originX, originZ, time, transform, overrides))
    }

    /**
     * The item now rests in chunk ([toCx], [toCz]) (thrown, carried): `MOVED_OUT` in its origin chunk and a
     * [ForeignItem] where it is. Moving back home normalises to `CHANGED` (§15.10.3).
     */
    fun move(key: ScatterItemKey, prefab: String, originX: Float, originZ: Float, toCx: Int, toCz: Int, transform: FloatArray, overrides: Map<String, String>, time: Float = 0f) {
        // Wherever it rested before as a foreign item, it is gone from there
        val left = chunks.entries.filter { (_, d) -> d.foreign.removeAll { it.key == key } }.map { it.key }
        left.forEach(::touch)
        if (toCx == key.cx && toCz == key.cz) { change(key, originX, originZ, transform, overrides, time); return }
        put(key, ScatterRecord(ScatterRecordKind.MOVED_OUT, originX, originZ, time))
        val target = pack(toCx, toCz)
        chunks.getOrPut(target) { ChunkStateDelta() }.foreign += ForeignItem(key, prefab, originX, originZ, transform, overrides, time)
        touch(target)
        checkBudget()
    }

    /** A foreign item ([key]) resting in ([cx], [cz]) was destroyed or taken: it leaves that chunk for good. */
    fun removeForeign(cx: Int, cz: Int, key: ScatterItemKey) {
        val packed = pack(cx, cz)
        if (chunks[packed]?.foreign?.removeAll { it.key == key } == true) touch(packed)
    }

    fun clearRecord(key: ScatterItemKey) {
        val packed = pack(key.cx, key.cz)
        val d = chunks[packed] ?: return
        if (d.records[key.ruleId]?.remove(key.ordinal) != null) touch(packed)
    }

    /** Sets the placement fingerprint a chunk's records of [ruleId] were made against. */
    fun setFingerprint(cx: Int, cz: Int, ruleId: String, fingerprint: Long) {
        val d = chunks[pack(cx, cz)] ?: return
        d.fingerprints[ruleId] = fingerprint
    }

    /**
     * Checks chunk ([cx], [cz]) against its current placement (REN-28 §15.10.4): for each rule whose [fingerprints]
     * differ from the stored one, records are kept only if an item with the same ordinal is placed within
     * [MATCH_DISTANCE] of the record's origin ([placedOrigin] gives it, null when no such item). Foreign items always
     * stay. Returns the number of records dropped.
     */
    fun validate(cx: Int, cz: Int, fingerprints: Map<String, Long>, placedOrigin: (ruleId: String, ordinal: Int) -> FloatArray?): Int {
        val packed = pack(cx, cz)
        val d = chunks[packed] ?: return 0
        var dropped = 0
        for ((ruleId, records) in d.records) {
            val now = fingerprints[ruleId] ?: continue
            if (d.fingerprints[ruleId] == now) continue
            val it = records.entries.iterator()
            while (it.hasNext()) {
                val (ordinal, r) = it.next()
                val o = placedOrigin(ruleId, ordinal)
                if (o == null || kotlin.math.hypot((o[0] - r.originX).toDouble(), (o[1] - r.originZ).toDouble()) > MATCH_DISTANCE) { it.remove(); dropped++ }
            }
            d.fingerprints[ruleId] = now
        }
        if (dropped > 0) touch(packed)
        return dropped
    }

    /**
     * Respawn (REN-28 §17): drops `DESTROYED` and `TAKEN` records of chunk ([cx], [cz]) older than their rule's
     * respawn time ([respawnAfter] by rule id, 0 = never) at game time [now]. Returns how many came back.
     */
    fun expire(cx: Int, cz: Int, now: Float, respawnAfter: (ruleId: String) -> Float): Int {
        val packed = pack(cx, cz)
        val d = chunks[packed] ?: return 0
        var back = 0
        for ((ruleId, records) in d.records) {
            val after = respawnAfter(ruleId)
            if (after <= 0f) continue
            back += records.entries.count { (_, r) -> (r.kind == ScatterRecordKind.DESTROYED || r.kind == ScatterRecordKind.TAKEN) && now - r.time >= after }
            records.entries.removeAll { (_, r) -> (r.kind == ScatterRecordKind.DESTROYED || r.kind == ScatterRecordKind.TAKEN) && now - r.time >= after }
        }
        if (back > 0) touch(packed)
        return back
    }

    /** A different world (runtime reseed, recipe or rule edit in Play): every record is meaningless. */
    fun clear() {
        if (chunks.isEmpty()) return
        chunks.clear(); chunkVersions.clear(); version++
    }

    /** Immutable copy for workers and saving. */
    fun snapshot(): Map<Long, ChunkStateDelta> = chunks.mapValues { it.value.copy() }

    /** Replaces everything with [deltas] (a loaded save game). */
    fun restore(deltas: Map<Long, ChunkStateDelta>) {
        chunks.clear(); chunkVersions.clear()
        for ((k, d) in deltas) { chunks[k] = d.copy(); chunkVersions[k] = 1 }
        version++
        checkBudget()
    }

    private fun put(key: ScatterItemKey, record: ScatterRecord) {
        val packed = pack(key.cx, key.cz)
        chunks.getOrPut(packed) { ChunkStateDelta() }.records.getOrPut(key.ruleId) { LinkedHashMap() }[key.ordinal] = record
        touch(packed)
        checkBudget()
    }

    private fun touch(packed: Long) {
        chunkVersions.merge(packed, 1, Int::plus)
        if (chunks[packed]?.isEmpty == true) chunks.remove(packed)
        version++
    }

    /** Over budget: warn once, never drop (it is gameplay state). */
    private fun checkBudget() {
        if (warned) return
        val b = bytes()
        if (b > budgetBytes) { warned = true; warn("Terrain scatter state is $b bytes, over its ${budgetBytes} byte budget (nothing is dropped)") }
    }

    companion object {
        const val MATCH_DISTANCE = 0.25

        fun pack(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
        fun cx(packed: Long): Int = (packed shr 32).toInt()
        fun cz(packed: Long): Int = packed.toInt()
    }
}
