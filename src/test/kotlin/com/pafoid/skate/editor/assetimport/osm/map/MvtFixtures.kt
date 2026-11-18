package com.pafoid.skate.editor.assetimport.osm.map

import java.io.ByteArrayOutputStream

/** Hand-built Mapbox Vector Tile bytes for tests (protobuf encoding), so no `.pbf` download is needed. */
object MvtFixtures {
    class Pb {
        val out = ByteArrayOutputStream()
        fun varint(v: Long): Pb { var x = v; while (x and 0x7fL.inv() != 0L) { out.write(((x and 0x7f) or 0x80).toInt()); x = x ushr 7 }; out.write(x.toInt()); return this }
        fun key(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())
        fun uint(field: Int, v: Long) = key(field, 0).varint(v)
        fun bytes(field: Int, b: ByteArray): Pb { key(field, 2).varint(b.size.toLong()); out.write(b); return this }
        fun string(field: Int, s: String) = bytes(field, s.toByteArray())
        fun packed(field: Int, values: IntArray): Pb { val p = Pb(); values.forEach { p.varint(it.toLong() and 0xffffffffL) }; return bytes(field, p.out.toByteArray()) }
        fun fixed64(field: Int, v: Long): Pb { key(field, 1); for (k in 0 until 8) out.write(((v ushr (8 * k)) and 0xff).toInt()); return this }
        fun build(): ByteArray = out.toByteArray()
    }

    fun zz(n: Int) = (n shl 1) xor (n shr 31)
    fun cmd(id: Int, count: Int) = (count shl 3) or (id and 7)

    /** Command stream for rings/lines given absolute points per part. */
    fun geometry(parts: List<List<Pair<Int, Int>>>, closed: Boolean): IntArray {
        val out = ArrayList<Int>()
        var cx = 0; var cy = 0
        for (part in parts) {
            val (x0, y0) = part[0]
            out += cmd(1, 1); out += zz(x0 - cx); out += zz(y0 - cy); cx = x0; cy = y0
            out += cmd(2, part.size - 1)
            for ((x, y) in part.drop(1)) { out += zz(x - cx); out += zz(y - cy); cx = x; cy = y }
            if (closed) out += cmd(7, 1)
        }
        return out.toIntArray()
    }

    class F(val type: Int, val geometry: IntArray, val tags: IntArray = IntArray(0), val id: Long = 0)

    /** values: strings, Long (uint), Double, Boolean. */
    fun layer(name: String, features: List<F>, keys: List<String> = emptyList(), values: List<Any> = emptyList(), extent: Int = 4096): ByteArray {
        val l = Pb().uint(15, 2).string(1, name)
        features.forEach { f ->
            val fb = Pb().uint(1, f.id).also { if (f.tags.isNotEmpty()) it.packed(2, f.tags) }.uint(3, f.type.toLong()).packed(4, f.geometry)
            l.bytes(2, fb.build())
        }
        keys.forEach { l.string(3, it) }
        values.forEach { v ->
            val vb = when (v) {
                is String -> Pb().string(1, v)
                is Double -> Pb().fixed64(3, v.toRawBits())
                is Long -> Pb().uint(5, v)
                is Boolean -> Pb().uint(7, if (v) 1 else 0)
                else -> error("value $v")
            }
            l.bytes(4, vb.build())
        }
        l.uint(5, extent.toLong())
        return l.build()
    }

    fun tile(vararg layers: ByteArray): ByteArray = Pb().also { p -> layers.forEach { p.bytes(3, it) } }.build()

    fun rect(x0: Int, y0: Int, x1: Int, y1: Int) = listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1)

    /** Water on the left half, a park square bottom-right, a building top-right, a primary road across the middle. */
    fun city(): ByteArray = tile(
        layer("water", listOf(F(3, geometry(listOf(rect(0, 0, 2048, 4096)), closed = true)))),
        layer("park", listOf(F(3, geometry(listOf(rect(2600, 2600, 3900, 3900)), closed = true)))),
        layer("building", listOf(F(3, geometry(listOf(rect(2600, 300, 3600, 1300)), closed = true)))),
        layer("transportation", listOf(F(2, geometry(listOf(listOf(0 to 2048, 4096 to 2048)), closed = false), intArrayOf(0, 0))),
            keys = listOf("class"), values = listOf("primary")),
    )
}
