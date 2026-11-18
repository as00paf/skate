package com.pafoid.skate.engine.assets.terrain

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.roundToInt

/**
 * A region file `regions/r_<rx>_<rz>.bin` (REN-28 §15.6): 16 × 16 chunks, each record deflated on its own, so one
 * chunk reads with one seek and one read.
 *
 * ```
 * header (little-endian)  "SKTR" | version u16 | chunkQuads u16 | sampleSpacing f32
 * index                   256 × (offset u32, storedSize u32, rawSize u32, flags u16, crc32 u32); zero = absent
 * records                 deflated chunk records, at their offsets
 * ```
 *
 * A chunk record ([FLAG_HEIGHTS]): `base f32 | step f32 | (chunkQuads + 3)² × uint16`, heights = base + q × step, apron
 * included; the per-record base and step keep ≤ step/2 error (1 mm over a 64 m range). Splat, biome and scatter masks
 * (REN tasks 5–7) will append after the heights with their own flags; readers skip what they don't know.
 */
object TerrainRegionFile {
    const val MAGIC = 0x52544B53 // "SKTR" little-endian
    const val VERSION = 1
    const val REGION_CHUNKS = 16
    const val SLOTS = REGION_CHUNKS * REGION_CHUNKS
    const val FLAG_HEIGHTS = 1
    private const val ENTRY_BYTES = 4 + 4 + 4 + 2 + 4
    const val HEADER_BYTES = 4 + 2 + 2 + 4 + SLOTS * ENTRY_BYTES

    /** One index entry; [offset] 0 means the chunk is absent. */
    data class Entry(val offset: Int, val storedSize: Int, val rawSize: Int, val flags: Int, val crc: Int) {
        val present: Boolean get() = offset != 0
    }

    class Header(val chunkQuads: Int, val sampleSpacing: Float, val entries: List<Entry>)

    fun regionOf(c: Int): Int = Math.floorDiv(c, REGION_CHUNKS)
    fun slotOf(cx: Int, cz: Int): Int = Math.floorMod(cz, REGION_CHUNKS) * REGION_CHUNKS + Math.floorMod(cx, REGION_CHUNKS)

    /** Raw (undeflated) record for a chunk's `(chunkQuads + 3)²` heights. */
    fun encodeRecord(heights: FloatArray): ByteArray {
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        heights.forEach { if (it < lo) lo = it; if (it > hi) hi = it }
        if (heights.isEmpty()) { lo = 0f; hi = 0f }
        val step = if (hi > lo) (hi - lo) / 65535f else 0f
        val b = ByteBuffer.allocate(8 + heights.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.putFloat(lo).putFloat(step)
        heights.forEach { h -> b.putShort((if (step == 0f) 0 else ((h - lo) / step).roundToInt().coerceIn(0, 65535)).toShort()) }
        return b.array()
    }

    fun decodeRecord(raw: ByteArray, count: Int): FloatArray {
        val b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val base = b.float; val step = b.float
        require(raw.size >= 8 + count * 2) { "chunk record too short: ${raw.size} bytes for $count heights" }
        return FloatArray(count) { base + (b.short.toInt() and 0xffff) * step }
    }

    /** A region file holding [records] (slot → raw record) for chunks of [chunkQuads] quads at [sampleSpacing]. */
    fun encode(chunkQuads: Int, sampleSpacing: Float, records: Map<Int, ByteArray>): ByteArray {
        val body = ByteArrayOutputStream()
        val entries = arrayOfNulls<Entry>(SLOTS)
        records.toSortedMap().forEach { (slot, raw) ->
            require(slot in 0 until SLOTS) { "bad slot $slot" }
            val stored = deflate(raw)
            val crc = CRC32().apply { update(raw) }.value.toInt()
            entries[slot] = Entry(HEADER_BYTES + body.size(), stored.size, raw.size, FLAG_HEIGHTS, crc)
            body.write(stored)
        }
        val head = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        head.putInt(MAGIC).putShort(VERSION.toShort()).putShort(chunkQuads.toShort()).putFloat(sampleSpacing)
        entries.forEach { e ->
            val x = e ?: Entry(0, 0, 0, 0, 0)
            head.putInt(x.offset).putInt(x.storedSize).putInt(x.rawSize).putShort(x.flags.toShort()).putInt(x.crc)
        }
        return head.array() + body.toByteArray()
    }

    fun readHeader(bytes: ByteArray): Header {
        require(bytes.size >= HEADER_BYTES) { "region file too short" }
        val b = ByteBuffer.wrap(bytes, 0, HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        require(b.int == MAGIC) { "not a terrain region file (bad magic)" }
        val version = b.short.toInt()
        require(version == VERSION) { "region file version $version, expected $VERSION" }
        val quads = b.short.toInt() and 0xffff
        val spacing = b.float
        val entries = List(SLOTS) { Entry(b.int, b.int, b.int, b.short.toInt() and 0xffff, b.int) }
        return Header(quads, spacing, entries)
    }

    /** The raw record in [slot] of [bytes], CRC-checked, or null when the chunk is absent. */
    fun readRecord(bytes: ByteArray, header: Header, slot: Int): ByteArray? {
        val e = header.entries[slot]
        if (!e.present) return null
        require(e.offset >= HEADER_BYTES && e.offset + e.storedSize <= bytes.size) { "record $slot lies outside the file" }
        val raw = try {
            inflate(bytes, e.offset, e.storedSize, e.rawSize)
        } catch (x: java.util.zip.DataFormatException) {
            throw IllegalArgumentException("record $slot is corrupt (${x.message})", x)
        }
        val crc = CRC32().apply { update(raw) }.value.toInt()
        require(crc == e.crc) { "record $slot is corrupt (crc mismatch)" }
        return raw
    }

    /** Every present record (slot → raw), to merge before rewriting a region. */
    fun readAll(bytes: ByteArray): Pair<Header, Map<Int, ByteArray>> {
        val header = readHeader(bytes)
        return header to (0 until SLOTS).mapNotNull { s -> readRecord(bytes, header, s)?.let { s to it } }.toMap()
    }

    private fun deflate(raw: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION)
        try {
            d.setInput(raw); d.finish()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            return out.toByteArray()
        } finally { d.end() }
    }

    private fun inflate(bytes: ByteArray, offset: Int, length: Int, rawSize: Int): ByteArray {
        val i = Inflater()
        try {
            i.setInput(bytes, offset, length)
            val out = ByteArray(rawSize)
            var n = 0
            while (n < rawSize && !i.finished()) {
                val k = i.inflate(out, n, rawSize - n)
                if (k == 0 && (i.needsInput() || i.needsDictionary())) break
                n += k
            }
            require(n == rawSize) { "record inflated to $n bytes, expected $rawSize" }
            return out
        } finally { i.end() }
    }
}
