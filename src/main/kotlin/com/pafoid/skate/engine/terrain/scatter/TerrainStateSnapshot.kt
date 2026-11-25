package com.pafoid.skate.engine.terrain.scatter

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * The terrain section of a save game (REN-28 §15.10.5): magic `SKSD`, format version, terrain id, world seed and recipe
 * fingerprint, then every chunk delta; deflated. The container holding sections belongs to skate-assets (AST-41);
 * this is opaque bytes to it. Encoding is deterministic, so encode(decode(x)) == x byte for byte.
 */
class TerrainStateSnapshot(
    val terrainId: String,
    val worldSeed: Long,
    val recipeFingerprint: Long,
    val deltas: Map<Long, ChunkStateDelta>,
) {
    fun encode(): ByteArray {
        val raw = ByteArrayOutputStream()
        DataOutputStream(raw).use { out ->
            out.writeInt(MAGIC); out.writeShort(VERSION)
            out.writeUTF(terrainId); out.writeLong(worldSeed); out.writeLong(recipeFingerprint)
            val chunks = deltas.entries.sortedBy { it.key }
            out.writeInt(chunks.size)
            for ((packed, d) in chunks) {
                out.writeLong(packed)
                out.writeInt(d.records.size)
                for ((ruleId, records) in d.records.entries.sortedBy { it.key }) {
                    out.writeUTF(ruleId)
                    out.writeLong(d.fingerprints[ruleId] ?: 0L)
                    out.writeInt(records.size)
                    for ((ordinal, r) in records.entries.sortedBy { it.key }) {
                        out.writeInt(ordinal); writeRecord(out, r)
                    }
                }
                // Fingerprints of rules without records (kept for validation)
                val extra = d.fingerprints.keys.filter { it !in d.records }.sorted()
                out.writeInt(extra.size)
                for (ruleId in extra) { out.writeUTF(ruleId); out.writeLong(d.fingerprints.getValue(ruleId)) }
                out.writeInt(d.foreign.size)
                for (f in d.foreign) writeForeign(out, f)
            }
        }
        val packedBytes = ByteArrayOutputStream()
        DeflaterOutputStream(packedBytes).use { it.write(raw.toByteArray()) }
        return packedBytes.toByteArray()
    }

    companion object {
        const val MAGIC = 0x534B5344 // "SKSD"
        const val VERSION = 1

        fun decode(bytes: ByteArray): TerrainStateSnapshot {
            val raw = InflaterInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            DataInputStream(ByteArrayInputStream(raw)).use { input ->
                require(input.readInt() == MAGIC) { "Not a terrain state section (bad magic)" }
                val version = input.readUnsignedShort()
                require(version == VERSION) { "Terrain state version $version is not supported (current $VERSION)" }
                val id = input.readUTF(); val seed = input.readLong(); val recipe = input.readLong()
                val deltas = LinkedHashMap<Long, ChunkStateDelta>()
                repeat(input.readInt()) {
                    val packed = input.readLong()
                    val d = ChunkStateDelta()
                    repeat(input.readInt()) {
                        val ruleId = input.readUTF()
                        val fingerprint = input.readLong()
                        val records = LinkedHashMap<Int, ScatterRecord>()
                        repeat(input.readInt()) { val ordinal = input.readInt(); records[ordinal] = readRecord(input) }
                        d.records[ruleId] = records
                        d.fingerprints[ruleId] = fingerprint
                    }
                    repeat(input.readInt()) { d.fingerprints[input.readUTF()] = input.readLong() }
                    repeat(input.readInt()) { d.foreign += readForeign(input) }
                    deltas[packed] = d
                }
                return TerrainStateSnapshot(id, seed, recipe, deltas)
            }
        }

        private fun writeTransform(out: DataOutputStream, t: FloatArray?) {
            out.writeBoolean(t != null)
            t?.forEach(out::writeFloat)
        }

        private fun readTransform(input: DataInputStream): FloatArray? = if (input.readBoolean()) FloatArray(TRANSFORM_FLOATS) { input.readFloat() } else null

        private fun writeOverrides(out: DataOutputStream, o: Map<String, String>) {
            out.writeInt(o.size)
            for ((k, v) in o.entries.sortedBy { it.key }) { out.writeUTF(k); writeLong(out, v) }
        }

        private fun readOverrides(input: DataInputStream): Map<String, String> {
            val m = LinkedHashMap<String, String>()
            repeat(input.readInt()) { m[input.readUTF()] = readLong(input) }
            return m
        }

        /** Strings that may exceed writeUTF's 64 KB (component JSON). */
        private fun writeLong(out: DataOutputStream, s: String) { val b = s.toByteArray(Charsets.UTF_8); out.writeInt(b.size); out.write(b) }
        private fun readLong(input: DataInputStream): String { val b = ByteArray(input.readInt()); input.readFully(b); return String(b, Charsets.UTF_8) }

        private fun writeRecord(out: DataOutputStream, r: ScatterRecord) {
            out.writeByte(r.kind.ordinal); out.writeFloat(r.originX); out.writeFloat(r.originZ); out.writeFloat(r.time)
            writeTransform(out, r.transform); writeOverrides(out, r.overrides)
        }

        private fun readRecord(input: DataInputStream): ScatterRecord {
            val kind = ScatterRecordKind.entries[input.readUnsignedByte()]
            return ScatterRecord(kind, input.readFloat(), input.readFloat(), input.readFloat(), readTransform(input), readOverrides(input))
        }

        private fun writeForeign(out: DataOutputStream, f: ForeignItem) {
            out.writeUTF(f.key.ruleId); out.writeInt(f.key.cx); out.writeInt(f.key.cz); out.writeInt(f.key.ordinal)
            out.writeUTF(f.prefab); out.writeFloat(f.originX); out.writeFloat(f.originZ); out.writeFloat(f.time)
            writeTransform(out, f.transform); writeOverrides(out, f.overrides)
        }

        private fun readForeign(input: DataInputStream): ForeignItem {
            val key = ScatterItemKey(input.readUTF(), input.readInt(), input.readInt(), input.readInt())
            val prefab = input.readUTF(); val ox = input.readFloat(); val oz = input.readFloat(); val time = input.readFloat()
            return ForeignItem(key, prefab, ox, oz, readTransform(input)!!, readOverrides(input), time)
        }

        /** Position xyz, rotation quaternion xyzw, scale xyz. */
        const val TRANSFORM_FLOATS = 10
    }
}

/** Placement fingerprints (REN-28 §15.10.1): what a chunk's items of one rule depended on. */
object ScatterFingerprint {
    private const val PRIME = 1099511628211L

    private fun mix(h: Long, v: Long): Long = (h xor v) * PRIME

    /** A hash of the seed stream, the rule's parameters and the chunk's heights. */
    fun of(projectSeed: Long, seedName: String, seedOffset: Int, rule: com.pafoid.skate.engine.terrain.ScatterRule, chunk: com.pafoid.skate.engine.terrain.TerrainChunkData): Long {
        var h = -3750763034362895579L
        h = mix(h, projectSeed); h = mix(h, seedName.hashCode().toLong()); h = mix(h, seedOffset.toLong())
        h = mix(h, rule.hashCode().toLong())
        h = mix(h, chunk.heights.contentHashCode().toLong())
        return h
    }

    /** A hash of what a whole terrain's world depends on (recipe, stamps, seed): the section header's check. */
    fun ofWorld(key: com.pafoid.skate.engine.terrain.TerrainKey): Long =
        mix(mix(-3750763034362895579L, key.recipe.hashCode().toLong()), (key.stamps.hashCode().toLong() shl 1) xor key.projectSeed xor key.seedName.hashCode().toLong() xor key.seedOffset.toLong())
}

/**
 * Save and load of every terrain's scatter state (REN-28 §15.10.5): [snapshot] encodes the stores into sections keyed
 * by terrain id (its asset GUID, else its seed name); [restore] puts them back before the first prefetch. A section
 * whose world fingerprint no longer matches (a patch changed the recipe) still restores; per-chunk validation then
 * drops records that no longer match their items (§15.10.4).
 */
object TerrainStateService {
    class Terrain(val id: String, val worldSeed: Long, val worldFingerprint: Long, val store: ScatterStateStore)

    fun snapshot(terrains: List<Terrain>): Map<String, ByteArray> =
        terrains.associate { t -> t.id to TerrainStateSnapshot(t.id, t.worldSeed, t.worldFingerprint, t.store.snapshot()).encode() }

    /** Restores [sections] into the matching terrains; returns how many were restored. Terrains without a section start empty. */
    fun restore(sections: Map<String, ByteArray>, terrains: List<Terrain>): Int {
        var restored = 0
        for (t in terrains) {
            val bytes = sections[t.id]
            if (bytes == null) { t.store.clear(); continue }
            t.store.restore(TerrainStateSnapshot.decode(bytes).deltas)
            restored++
        }
        return restored
    }
}
