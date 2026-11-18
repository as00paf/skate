package com.pafoid.skate.editor.assetimport.osm.map

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/** Geometry type of a vector tile feature. */
enum class MvtType { UNKNOWN, POINT, LINESTRING, POLYGON }

/**
 * A decoded feature: [parts] are point lists in tile units (0..[MvtLayer.extent]); for polygons each part is a ring
 * (exterior rings clockwise in tile space, holes counter-clockwise; drawn with even-odd fill, so order does not matter).
 */
class MvtFeature(val id: Long, val type: MvtType, val tags: Map<String, Any>, val parts: List<FloatArray>)

class MvtLayer(val name: String, val extent: Int, val features: List<MvtFeature>)

/**
 * Minimal Mapbox Vector Tile 2.x decoder (AST-17 §3.1): protobuf varints, length-delimited fields, zig-zag deltas and
 * the MoveTo/LineTo/ClosePath geometry commands. No protobuf dependency: it reads one fixed message type. Gzipped
 * tiles are inflated first.
 */
object MvtDecoder {
    fun decode(data: ByteArray): List<MvtLayer> {
        val bytes = if (data.size > 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
        } else data
        val layers = ArrayList<MvtLayer>()
        Reader(bytes, 0, bytes.size).forEachField { field, r ->
            if (field == 3) layers += layer(r.bytes()) else r.skip()
        }
        return layers
    }

    private fun layer(r: Reader): MvtLayer {
        var name = ""
        var extent = 4096
        val keys = ArrayList<String>()
        val values = ArrayList<Any>()
        val raw = ArrayList<Reader>()
        r.forEachField { field, f ->
            when (field) {
                1 -> name = f.string()
                2 -> raw += f.bytes()
                3 -> keys += f.string()
                4 -> values += value(f.bytes())
                5 -> extent = f.varint().toInt()
                else -> f.skip()
            }
        }
        return MvtLayer(name, extent, raw.map { feature(it, keys, values) })
    }

    private fun value(r: Reader): Any {
        var v: Any = ""
        r.forEachField { field, f ->
            v = when (field) {
                1 -> f.string()
                2 -> Float.fromBits(f.fixed32())
                3 -> Double.fromBits(f.fixed64())
                4 -> f.varint()
                5 -> f.varint()
                6 -> zigzag(f.varint())
                7 -> f.varint() != 0L
                else -> { f.skip(); v }
            }
        }
        return v
    }

    private fun feature(r: Reader, keys: List<String>, values: List<Any>): MvtFeature {
        var id = 0L
        var type = MvtType.UNKNOWN
        var tagIdx = IntArray(0)
        var geometry = IntArray(0)
        r.forEachField { field, f ->
            when (field) {
                1 -> id = f.varint()
                2 -> tagIdx = f.packed()
                3 -> type = MvtType.entries.getOrElse(f.varint().toInt()) { MvtType.UNKNOWN }
                4 -> geometry = f.packed()
                else -> f.skip()
            }
        }
        val tags = LinkedHashMap<String, Any>()
        for (i in 0 until tagIdx.size - 1 step 2) {
            val k = keys.getOrNull(tagIdx[i]); val v = values.getOrNull(tagIdx[i + 1])
            if (k != null && v != null) tags[k] = v
        }
        return MvtFeature(id, type, tags, geometry(geometry))
    }

    /** Decodes the command stream into parts; ClosePath repeats the first point. */
    fun geometry(cmds: IntArray): List<FloatArray> {
        val parts = ArrayList<FloatArray>()
        var current = ArrayList<Float>()
        var x = 0; var y = 0; var i = 0
        fun flush() { if (current.isNotEmpty()) parts += current.toFloatArray(); current = ArrayList() }
        while (i < cmds.size) {
            val header = cmds[i++]
            val id = header and 0x7
            val count = header ushr 3
            when (id) {
                1, 2 -> repeat(count) {
                    if (i + 1 >= cmds.size) return parts.also { flush() }
                    if (id == 1) flush()
                    x += zigzag(cmds[i++].toLong()).toInt(); y += zigzag(cmds[i++].toLong()).toInt()
                    current += x.toFloat(); current += y.toFloat()
                }
                7 -> if (current.size >= 2) { current += current[0]; current += current[1] }
                else -> return parts.also { flush() }
            }
        }
        flush()
        return parts
    }

    private fun zigzag(n: Long): Long = (n ushr 1) xor -(n and 1)

    private class Reader(val buf: ByteArray, var pos: Int, val end: Int) {
        var wire = 0

        fun forEachField(block: (Int, Reader) -> Unit) {
            while (pos < end) {
                val key = varint().toInt()
                wire = key and 0x7
                block(key ushr 3, this)
            }
        }

        fun varint(): Long {
            var result = 0L; var shift = 0
            while (true) {
                require(pos < end) { "truncated varint" }
                val b = buf[pos++].toInt()
                result = result or ((b and 0x7f).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                require(shift < 64) { "varint too long" }
            }
        }

        fun fixed32(): Int {
            require(pos + 4 <= end) { "truncated fixed32" }
            var v = 0
            for (k in 0 until 4) v = v or ((buf[pos + k].toInt() and 0xff) shl (8 * k))
            pos += 4
            return v
        }

        fun fixed64(): Long {
            require(pos + 8 <= end) { "truncated fixed64" }
            var v = 0L
            for (k in 0 until 8) v = v or ((buf[pos + k].toLong() and 0xff) shl (8 * k))
            pos += 8
            return v
        }

        fun bytes(): Reader {
            val len = varint().toInt()
            require(len >= 0 && pos + len <= end) { "truncated field" }
            return Reader(buf, pos, pos + len).also { pos += len }
        }

        fun string(): String = bytes().let { String(buf, it.pos, it.end - it.pos, Charsets.UTF_8) }

        fun packed(): IntArray {
            val r = bytes()
            val out = ArrayList<Int>()
            while (r.pos < r.end) out += r.varint().toInt()
            return out.toIntArray()
        }

        fun skip() {
            when (wire) {
                0 -> varint()
                1 -> pos += 8
                2 -> bytes()
                5 -> pos += 4
                else -> error("unsupported wire type $wire")
            }
        }
    }
}
