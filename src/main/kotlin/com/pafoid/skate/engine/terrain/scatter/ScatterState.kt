package com.pafoid.skate.engine.terrain.scatter

/**
 * Identity of one scatter item (REN-28 §15.10.1): the rule's stable id, its chunk and its ordinal there. The ordinal
 * is the item's cell in the rule's jittered placement grid (`ScatterInstance.key`): placement is a pure function of
 * the seed stream, the rule and the chunk, so the same item gets the same key on every load, on every machine, and
 * removing one item never shifts another's.
 */
data class ScatterItemKey(val ruleId: String, val cx: Int, val cz: Int, val ordinal: Int)

/** What happened to an item (REN-28 §15.10.2). */
enum class ScatterRecordKind { DESTROYED, TAKEN, MOVED_OUT, CHANGED }

/**
 * A recorded change of a placed item. [originX], [originZ] are where placement put it (terrain-local), to match the
 * record again after a patch (§15.10.4). [transform] is 10 floats (position xyz, rotation quaternion xyzw, scale xyz)
 * when it moved; [overrides] the prefab overrides (`nodePath|component|property` → JSON text). [time] is the game time
 * of the capture, for `respawnAfterSeconds`.
 */
data class ScatterRecord(
    val kind: ScatterRecordKind,
    val originX: Float,
    val originZ: Float,
    val time: Float = 0f,
    val transform: FloatArray? = null,
    val overrides: Map<String, String> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean = other is ScatterRecord && kind == other.kind && originX == other.originX &&
        originZ == other.originZ && time == other.time && overrides == other.overrides &&
        (transform?.contentEquals(other.transform) ?: (other.transform == null))

    override fun hashCode(): Int = (kind.hashCode() * 31 + originX.hashCode()) * 31 + (transform?.contentHashCode() ?: 0)

    /** Approximate stored size (REN-28 §15.10.4). */
    fun bytes(): Long = 2L + (if (transform != null) 40 else 0) + overrides.entries.sumOf { (k, v) -> (k.length + v.length).toLong() }
}

/** An item that came from another chunk and now rests in this one (§15.10.2): it carries everything to respawn it. */
data class ForeignItem(
    val key: ScatterItemKey,
    val prefab: String,
    val originX: Float,
    val originZ: Float,
    val transform: FloatArray,
    val overrides: Map<String, String> = emptyMap(),
    val time: Float = 0f,
) {
    override fun equals(other: Any?): Boolean = other is ForeignItem && key == other.key && prefab == other.prefab &&
        originX == other.originX && originZ == other.originZ && transform.contentEquals(other.transform) &&
        overrides == other.overrides && time == other.time

    override fun hashCode(): Int = key.hashCode() * 31 + transform.contentHashCode()

    fun bytes(): Long = 48L + prefab.length + key.ruleId.length + overrides.entries.sumOf { (k, v) -> (k.length + v.length).toLong() }
}

/**
 * The changes of one chunk (REN-28 §15.10.2): per rule id its records by ordinal and its placement [fingerprints]
 * (a hash of what placement depended on), plus the foreign items resting here.
 */
class ChunkStateDelta {
    val records: MutableMap<String, MutableMap<Int, ScatterRecord>> = LinkedHashMap()
    val fingerprints: MutableMap<String, Long> = LinkedHashMap()
    val foreign: MutableList<ForeignItem> = ArrayList()

    val isEmpty: Boolean get() = records.values.all { it.isEmpty() } && foreign.isEmpty()

    fun bytes(): Long = records.values.sumOf { m -> m.values.sumOf { it.bytes() } } + foreign.sumOf { it.bytes() } + fingerprints.size * 8L

    fun copy(): ChunkStateDelta = ChunkStateDelta().also { c ->
        records.forEach { (r, m) -> c.records[r] = LinkedHashMap(m) }
        c.fingerprints.putAll(fingerprints)
        c.foreign.addAll(foreign)
    }

    override fun equals(other: Any?): Boolean = other is ChunkStateDelta && records == other.records &&
        fingerprints == other.fingerprints && foreign == other.foreign

    override fun hashCode(): Int = records.hashCode() * 31 + foreign.hashCode()
}
